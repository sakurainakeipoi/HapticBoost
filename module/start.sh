#!/system/bin/sh
M=/data/adb/modules/haptic_boost_qcom

# Android variants disagree on whether pkill -f really matches the full
# command line. Resolve matching PIDs explicitly so hot reload never leaves an
# old listener running beside the new one. Never kill this script or its
# caller, which may contain the target paths in an adb/su command line.
stop_match() {
  SIG=$1; PAT=$2
  for P in $(pgrep -f "$PAT" 2>/dev/null); do
    [ "$P" = "$$" ] && continue
    [ "$P" = "$PPID" ] && continue
    kill "$SIG" "$P" 2>/dev/null
  done
}

for S in keepalive.sh ring_listener.sh run_watch.sh watch.sh ring_play.sh; do
  stop_match -TERM "$M/$S"
done
sleep 1
for S in keepalive.sh ring_listener.sh run_watch.sh watch.sh ring_play.sh; do
  stop_match -KILL "$M/$S"
done
rm -f /data/local/tmp/vib_ring_active /data/local/tmp/vib_ring_play.pid /data/local/tmp/vib_call_hint \
  /data/local/tmp/vib_voip_session /data/local/tmp/vib_voip_connected
cmd vibrator_manager cancel >/dev/null 2>&1
( setsid sh "$M/run_watch.sh" >/dev/null 2>&1 < /dev/null & )
( setsid sh "$M/ring_listener.sh" >/dev/null 2>&1 < /dev/null & )
sleep 1
( setsid sh "$M/keepalive.sh" >/dev/null 2>&1 < /dev/null & )
sleep 2
echo "watch: $(pgrep -f "$M/watch.sh")"
echo "listener: $(pgrep -f "$M/ring_listener.sh")"
echo "keepalive: $(pgrep -f "$M/keepalive.sh")"
