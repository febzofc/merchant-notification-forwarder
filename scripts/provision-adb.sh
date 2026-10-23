#!/usr/bin/env bash
# Provisioning Bayarin lewat ADB.
#
# Kenapa ADB? Play Protect memblokir instalasi APK dari browser/WhatsApp/file
# manager jika aplikasi memakai izin Notification Listener
# (lihat https://developers.google.com/android/play-protect/warning-dev-guidance).
# ADB bukan "internet-sideloading source", sehingga instalasi tidak diblokir.
#
# Pemakaian:
#   ./scripts/provision-adb.sh Bayarin-v1.3.apk
#
# Prasyarat di perangkat: Opsi Pengembang -> USB debugging aktif.

set -euo pipefail

APK="${1:-}"
PKG="com.febz.bayarin"
LISTENER="$PKG/com.example.NotificationForwarderService"

if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Pemakaian: $0 <file.apk>" >&2
  exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb tidak ditemukan. Pasang Android Platform Tools lebih dulu." >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "Tidak ada perangkat. Colok kabel USB dan setujui prompt 'Allow USB debugging'." >&2
  exit 1
fi

echo "==> Memasang $APK"
# -r: timpa versi lama, -d: izinkan downgrade versionCode
adb install -r -d "$APK"

echo "==> Memberi akses Notification Listener"
if adb shell cmd notification allow_listener "$LISTENER" 2>/dev/null; then
  :
else
  # Android lama: tulis langsung ke secure settings, pertahankan listener lain.
  CURRENT=$(adb shell settings get secure enabled_notification_listeners | tr -d '\r')
  case "$CURRENT" in
    *"$LISTENER"*) ;;
    null|"") adb shell settings put secure enabled_notification_listeners "$LISTENER" ;;
    *) adb shell settings put secure enabled_notification_listeners "$CURRENT:$LISTENER" ;;
  esac
fi

echo "==> Mengizinkan notifikasi (Android 13+)"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

echo "==> Mengecualikan dari optimasi baterai"
adb shell dumpsys deviceidle whitelist "+$PKG" >/dev/null 2>&1 || true

echo
echo "Selesai. Buka aplikasi Bayarin, isi URL webhook, lalu kirim notifikasi uji."
adb shell am start -n "$PKG/com.example.MainActivity" >/dev/null 2>&1 || true
