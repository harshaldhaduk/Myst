#!/usr/bin/env bash
# Hand the Muse API key to the installed Myst debug app over adb, without compiling it into the APK.
# Reads MUSE_API_KEY (or MODEL_API_KEY) from the repo-root .env, never prints it, and sends it on
# stdin only, so it never shows up in any process's command line. The app encrypts it with an
# Android Keystore key on its next start and deletes the plaintext (core/secrets/MuseKeyStore.kt).
# Needs: adb on PATH, one phone connected (or ANDROID_SERIAL set), a debuggable build installed.
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
env_file="$root/.env"
gradle_file="$root/app/app/build.gradle.kts"

if [ ! -f "$env_file" ]; then
  echo "No .env at the repo root. Add a line MUSE_API_KEY=... there first." >&2
  exit 1
fi

key=""
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    MUSE_API_KEY=*) key=${line#MUSE_API_KEY=}; break ;;
    MODEL_API_KEY=*) [ -z "$key" ] && key=${line#MODEL_API_KEY=} ;;
  esac
done < "$env_file"
# Trim surrounding whitespace and quotes.
key=${key#"${key%%[![:space:]]*}"}
key=${key%"${key##*[![:space:]]}"}
key=${key#\"}; key=${key%\"}; key=${key#\'}; key=${key%\'}

if [ -z "$key" ]; then
  echo "MUSE_API_KEY is missing or empty in .env." >&2
  exit 1
fi

app_id=$(sed -n 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$gradle_file" | head -n 1)
if [ -z "$app_id" ]; then
  echo "Could not find applicationId in $gradle_file." >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No phone reachable over adb (connect one, or set ANDROID_SERIAL)." >&2
  exit 1
fi
if ! adb shell pm list packages "$app_id" | grep -qx "package:$app_id"; then
  echo "$app_id is not installed on the phone. Install the debug build first." >&2
  exit 1
fi

# run-as starts in the app's data dir; Context.getNoBackupFilesDir() is <dataDir>/no_backup.
# printf is a shell builtin, so the key reaches adb only through the pipe.
printf '%s' "$key" |
  adb shell "run-as $app_id sh -c 'umask 077 && mkdir -p no_backup && cat > no_backup/muse_key.import'"
unset key

# Stop the app so the next launch imports the key at start.
adb shell am force-stop "$app_id"
echo "Muse key sent to $app_id (not printed). Open Myst again; it encrypts the key and deletes the plaintext copy."
