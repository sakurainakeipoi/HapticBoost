#!/system/bin/sh
echo 1280 > /sys/class/qcom-haptics/vmax
echo 7700 > /sys/class/qcom-haptics/fifo_vmax
echo 1280 > /sys/class/qcom-haptics/cl_vmax
pkill -f watch.sh 2>/dev/null
pkill -f ring_play.sh 2>/dev/null
rm -f /data/local/tmp/vib_ring_active /data/local/tmp/vib_ring_play.pid /data/local/tmp/vib_call_hint \
  /data/local/tmp/vib_voip_session /data/local/tmp/vib_voip_connected
cmd vibrator_manager cancel >/dev/null 2>&1
echo "已恢复原厂值：vmax=$(cat /sys/class/qcom-haptics/vmax) fifo=$(cat /sys/class/qcom-haptics/fifo_vmax)"
