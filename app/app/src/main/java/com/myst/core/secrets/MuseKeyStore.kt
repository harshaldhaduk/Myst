package com.myst.core.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Muse API key, kept out of the APK. scripts/provision-muse-key.sh drops it in plaintext at
 * no_backup/muse_key.import over adb; the next [key] call encrypts it with an Android Keystore
 * AES/GCM key, stores ciphertext and IV beside it, and wipes the plaintext. no_backup is never
 * backed up, and the Keystore key never leaves the device.
 *
 * Never logs the key. Call off the main thread: it does file and Keystore work.
 */
class MuseKeyStore(context: Context) {
  private companion object {
    const val TAG = "Myst:Key"
    const val ALIAS = "myst_muse_key"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORM = "AES/GCM/NoPadding"
    const val TAG_BITS = 128
    const val IMPORT_FILE = "muse_key.import"
    const val CIPHER_FILE = "muse_key.bin"
    const val IV_FILE = "muse_key.iv"
  }

  private val dir: File = context.applicationContext.noBackupFilesDir
  private val importFile = File(dir, IMPORT_FILE)
  private val cipherFile = File(dir, CIPHER_FILE)
  private val ivFile = File(dir, IV_FILE)

  @Volatile private var cached: String? = null

  /** The key, or null when none was provisioned (or it can no longer be decrypted). */
  @Synchronized
  fun key(): String? {
    importIfPresent()
    cached?.let { return it }
    if (!cipherFile.exists() || !ivFile.exists()) return null
    return try {
      val cipher = Cipher.getInstance(TRANSFORM)
      cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, ivFile.readBytes()))
      String(cipher.doFinal(cipherFile.readBytes()), Charsets.UTF_8).takeIf { it.isNotBlank() }
          .also { cached = it }
    } catch (e: Exception) {
      // A reinstall or a wiped Keystore leaves ciphertext we cannot open; provision again.
      Log.w(TAG, "stored Muse key unreadable: ${e.javaClass.simpleName}")
      null
    }
  }

  fun hasKey(): Boolean = key() != null

  /** Encrypts a freshly provisioned plaintext key, then overwrites and deletes the plaintext. */
  private fun importIfPresent() {
    if (!importFile.exists()) return
    try {
      val plain = importFile.readText(Charsets.UTF_8).trim()
      if (plain.isNotEmpty()) {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        ivFile.writeBytes(cipher.iv)
        cipherFile.writeBytes(sealed)
        cached = plain
        Log.i(TAG, "Muse key imported and encrypted")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Muse key import failed: ${e.javaClass.simpleName}")
    } finally {
      wipe(importFile)
    }
  }

  private fun wipe(file: File) {
    runCatching { file.writeBytes(ByteArray(file.length().toInt())) }
    if (!file.delete()) Log.w(TAG, "could not delete the plaintext import file")
  }

  private fun secretKey(): SecretKey {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    gen.init(
        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build(),
    )
    return gen.generateKey()
  }
}
