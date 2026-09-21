#!/system/bin/sh
# HapticBoost events listener v4: incoming-call detect (incl. VoIP/WeChat)
# START: call notification / voip service / voip activity
# END: only when the voip call UI is destroyed (avoid mid-ring false end)
HINT=/data/local/tmp/vib_call_hint
PAT='category=call|VoipNewForegroundService|plugin.voip.ui.VideoActivity'
START=$(date +%s)
while true; do
  logcat -b events -v epoch -e "$PAT" 2>/dev/null | while IFS= read -r L; do
    E=$(echo "$L" | awk '{print $1}' | cut -d. -f1)
    case "$E" in ''|*[!0-9]*) continue;; esac
    [ "$E" -ge "$START" ] || continue
    case "$L" in
      *notification_enqueue*category=call*|*am_foreground_service_start*VoipNewForegroundService*|*wm_create_activity*plugin.voip.ui.VideoActivity*|*wm_on_resume_called*plugin.voip.ui.VideoActivity*)
        date +%s > $HINT ;;
      *wm_destroy_activity*plugin.voip.ui.VideoActivity*)
        rm -f $HINT ;;
    esac
  done
  sleep 1
done
