#!/system/bin/sh
# AudioService's ringer mode is separate from MODE_RINGTONE / call audio mode.
# Internal mode is authoritative (including system policy); an explicitly
# silent external mode also vetoes boosting. Missing/unknown state stays quiet.
ringer_allows_boost() {
  # Android printf is external: a full audio dump can exceed exec's per-arg
  # limit. Feed data through stdin so growing service history cannot hide mode.
  RINGER_INTERNAL=$(sed -n 's/^[[:space:]]*- mode (internal) = //p' <<EOF | head -n 1 | tr -d '\r'
$1
EOF
  )
  RINGER_EXTERNAL=$(sed -n 's/^[[:space:]]*- mode (external) = //p' <<EOF | head -n 1 | tr -d '\r'
$1
EOF
  )
  case "$RINGER_EXTERNAL" in SILENT|0) return 1;; esac
  case "$RINGER_INTERNAL" in
    VIBRATE|NORMAL|1|2) return 0 ;;
    '') case "$RINGER_EXTERNAL" in VIBRATE|NORMAL|1|2) return 0;; esac ;;
  esac
  return 1
}
