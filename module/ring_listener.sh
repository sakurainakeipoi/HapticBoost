#!/system/bin/sh
# HapticBoost events listener v6: incoming-call fallback (incl. VoIP/WeChat)
# Telecom state remains authoritative for ordinary calls. These events only
# provide a short fallback hint for calls that bypass Android Telecom.
HINT=/data/local/tmp/vib_call_hint
SESSION=/data/local/tmp/vib_voip_session
CONNECTED=/data/local/tmp/vib_voip_connected
PAT='category=call|VoipNewForegroundService|plugin.voip.ui.VideoActivity'
START=$(date +%s)
while true; do
  logcat -b events -v epoch -e "$PAT" 2>/dev/null | while IFS= read -r L; do
    E=$(echo "$L" | awk '{print $1}' | cut -d. -f1)
    case "$E" in ''|*[!0-9]*) continue;; esac
    [ "$E" -ge "$START" ] || continue
    case "$L" in
      *am_foreground_service_start*VoipNewForegroundService*)
        date +%s > "$SESSION"
        [ -f "$CONNECTED" ] || date +%s > "$HINT" ;;
      *notification_enqueue*category=call*|*wm_create_activity*plugin.voip.ui.VideoActivity*)
        [ -f "$CONNECTED" ] || date +%s > "$HINT" ;;
      *am_foreground_service_stop*VoipNewForegroundService*)
        rm -f "$HINT" "$SESSION" "$CONNECTED" ;;
      *notification_cancel*category=call*|*wm_destroy_activity*plugin.voip.ui.VideoActivity*)
        rm -f "$HINT" ;;
    esac
  done
  sleep 1
done
