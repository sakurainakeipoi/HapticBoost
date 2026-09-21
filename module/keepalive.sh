#!/system/bin/sh
M=/data/adb/modules/haptic_boost_qcom
HB=/data/local/tmp/vibrate_boost_heartbeat
while true; do
  if ! pgrep -f "$M/run_watch.sh" >/dev/null 2>&1; then
    echo "$(date "+%m-%d %H:%M:%S") watcher down, restart" >> $HB
    ( setsid sh "$M/run_watch.sh" >/dev/null 2>&1 < /dev/null & )
  fi
  if ! pgrep -f "$M/ring_listener.sh" >/dev/null 2>&1; then
    echo "$(date "+%m-%d %H:%M:%S") listener down, restart" >> $HB
    ( setsid sh "$M/ring_listener.sh" >/dev/null 2>&1 < /dev/null & )
  fi
  sleep 20
done
