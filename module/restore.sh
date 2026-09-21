#!/system/bin/sh
M=/data/adb/modules/haptic_boost_qcom
echo 1280 > /sys/class/qcom-haptics/vmax
echo 7700 > /sys/class/qcom-haptics/fifo_vmax
echo 1280 > /sys/class/qcom-haptics/cl_vmax

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
echo "已恢复原厂值：vmax=$(cat /sys/class/qcom-haptics/vmax) fifo=$(cat /sys/class/qcom-haptics/fifo_vmax)"
