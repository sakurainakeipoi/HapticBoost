#!/system/bin/sh
M=/data/adb/modules/haptic_boost_qcom
pkill -f "$M/run_watch.sh" 2>/dev/null
pkill -f "$M/watch.sh" 2>/dev/null
pkill -f "$M/keepalive.sh" 2>/dev/null
pkill -f "$M/ring_listener.sh" 2>/dev/null
pkill -f "$M/ring_play.sh" 2>/dev/null
rm -f /data/local/tmp/vib_ring_active /data/local/tmp/vib_ring_play.pid /data/local/tmp/vib_call_hint
cmd vibrator_manager cancel >/dev/null 2>&1
sleep 1
( setsid sh "$M/run_watch.sh" >/dev/null 2>&1 < /dev/null & )
( setsid sh "$M/ring_listener.sh" >/dev/null 2>&1 < /dev/null & )
sleep 1
( setsid sh "$M/keepalive.sh" >/dev/null 2>&1 < /dev/null & )
sleep 2
echo "watch: $(pgrep -f "$M/watch.sh")"
echo "listener: $(pgrep -f "$M/ring_listener.sh")"
echo "keepalive: $(pgrep -f "$M/keepalive.sh")"
