#!/system/bin/sh
echo 1280 > /sys/class/qcom-haptics/vmax
echo 7700 > /sys/class/qcom-haptics/fifo_vmax
echo 1280 > /sys/class/qcom-haptics/cl_vmax
pkill -f watch.sh 2>/dev/null
echo "已恢复原厂值：vmax=$(cat /sys/class/qcom-haptics/vmax) fifo=$(cat /sys/class/qcom-haptics/fifo_vmax)"
