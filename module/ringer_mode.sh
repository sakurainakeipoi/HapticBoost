#!/system/bin/sh
# AudioService's ringer mode is separate from MODE_RINGTONE / call audio mode.
# Internal mode is authoritative (including system policy); an explicitly
# silent external mode also vetoes boosting. Missing/unknown state stays quiet.
ringer_allows_boost() {
  RINGER_INTERNAL=$(printf '%s\n' "$1" | sed -n 's/^[[:space:]]*- mode (internal) = //p' | head -n 1 | tr -d '\r')
  RINGER_EXTERNAL=$(printf '%s\n' "$1" | sed -n 's/^[[:space:]]*- mode (external) = //p' | head -n 1 | tr -d '\r')
  case "$RINGER_EXTERNAL" in SILENT|0) return 1;; esac
  case "$RINGER_INTERNAL" in
    VIBRATE|NORMAL|1|2) return 0 ;;
    '') case "$RINGER_EXTERNAL" in VIBRATE|NORMAL|1|2) return 0;; esac ;;
  esac
  return 1
}
