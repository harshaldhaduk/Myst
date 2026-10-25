package com.myst.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myst.core.Capability
import com.myst.core.Label
import com.myst.ui.theme.Myst

/** A grouped block of content: a tinted panel with a serif title. Places still uses this as-is. */
@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
  Panel(tint = Myst.Mist) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    content()
  }
}

@Composable
internal fun Panel(
    tint: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    padding: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
  Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), color = tint, contentColor = Myst.Ink) {
    Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
  }
}

/** The main action of a screen: a pastel pill with ink text. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Myst.Lilac,
    enabled: Boolean = true,
) {
  Button(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      shape = CircleShape,
      colors =
          ButtonDefaults.buttonColors(
              containerColor = color,
              contentColor = Myst.Ink,
              disabledContainerColor = Myst.Line,
              disabledContentColor = Myst.InkSoft,
          ),
      contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge)
  }
}

/** A quiet secondary action: ink text, no fill. */
@Composable
internal fun QuietButton(text: String, onClick: () -> Unit, color: Color = Myst.InkSoft, enabled: Boolean = true) {
  TextButton(onClick = onClick, enabled = enabled) {
    Text(text, color = if (enabled) color else Myst.Line, style = MaterialTheme.typography.labelLarge)
  }
}

/** A small rounded tag, e.g. "with food". */
@Composable
internal fun Tag(text: String, color: Color) {
  Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = Myst.Ink,
      modifier = Modifier.background(color, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
) {
  OutlinedTextField(
      value,
      onChange,
      modifier,
      label = { Text(label) },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedBorderColor = Myst.LilacStrong,
              unfocusedBorderColor = Myst.Line,
              focusedLabelColor = Myst.Ink,
              unfocusedLabelColor = Myst.InkSoft,
              cursorColor = Myst.Ink,
              focusedContainerColor = Myst.White,
              unfocusedContainerColor = Myst.White,
          ),
  )
}

/** A round portrait: the enrollment photo, or the first letter on a pastel. */
@Composable
internal fun Avatar(name: String, photo: ByteArray?, tint: Color, size: Dp = 64.dp) {
  val bitmap = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
  Box(
      Modifier.size(size).clip(CircleShape).background(tint).border(3.dp, Myst.White, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      Image(bitmap.asImageBitmap(), contentDescription = "Photo of $name", contentScale = ContentScale.Crop)
    } else {
      Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
    }
  }
}

/**
 * Take a reference photo with the glasses. Used by Family, Medicine and Places, which all attach
 * the pending photo when they save.
 */
@Composable
internal fun GlassesPhotoCard(
    ui: EnrollUiState,
    connected: Boolean,
    hint: String,
    tint: Color,
    onCapture: () -> Unit,
    onDiscard: () -> Unit,
    accent: Color = Myst.Lilac,
) {
  Panel(tint = tint, radius = 24.dp, padding = 16.dp) {
    val pending = ui.pending
    if (pending == null) {
      Text(hint, style = MaterialTheme.typography.bodyMedium)
      PillButton(
          if (ui.capturing) "Taking photo…" else if (connected) "Take a photo with the glasses" else "Connect the glasses first",
          onCapture,
          color = accent,
          enabled = connected && !ui.capturing,
      )
    } else {
      JpegImage(
          pending.photo.jpeg,
          Modifier.fillMaxWidth().aspectRatio(pending.photo.width.toFloat() / pending.photo.height.coerceAtLeast(1)).clip(RoundedCornerShape(18.dp)),
          crop = true,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("This photo will be saved with it.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        QuietButton("Remove photo", onDiscard)
      }
    }
  }
}

/** Live / mock / experimental, as a soft tag (AGENTS.md truth labels). */
@Composable
fun LabelChip(label: Label) {
  val (text, color) =
      when (label) {
        Label.LIVE -> "Live" to Color(0xFFD5F0E3)
        Label.MOCK -> "Mock" to Myst.Blush
        Label.EXPERIMENTAL -> "Experimental" to Myst.Lilac
      }
  Tag(text, color)
}

@Composable
internal fun CapabilityRow(c: Capability) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
    LabelChip(c.label)
    Column(Modifier.weight(1f)) {
      Text(c.name, style = MaterialTheme.typography.labelLarge)
      Text(c.note, style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft)
      c.blockedBy?.let { Text("Waiting on: $it", style = MaterialTheme.typography.bodySmall, color = Myst.InkSoft) }
    }
  }
}

@Composable
internal fun JpegImage(bytes: ByteArray, modifier: Modifier, crop: Boolean = false) {
  val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } ?: return
  Image(
      bitmap.asImageBitmap(),
      contentDescription = null,
      modifier = modifier,
      contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
  )
}

@Composable
internal fun SmallLabel(text: String) {
  Text(text, style = MaterialTheme.typography.labelMedium, color = Myst.InkSoft, fontWeight = FontWeight.Medium)
}
