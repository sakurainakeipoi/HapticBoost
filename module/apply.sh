#!/system/bin/sh
MODDIR=${0%/*}
. $MODDIR/config.sh 2>/dev/null
target=${1:-normal}
if [ "$target" = "call" ]; then
  V=$CALL_VMAX; F=$CALL_FIFO_VMAX; C=$CALL_CL_VMAX
else
  V=$NORMAL_VMAX; F=$NORMAL_FIFO_VMAX; C=$NORMAL_CL_VMAX
fi
echo ${V:-1280} > /sys/class/qcom-haptics/vmax
echo ${F:-7700} > /sys/class/qcom-haptics/fifo_vmax
echo ${C:-1280} > /sys/class/qcom-haptics/cl_vmax
echo "applied($target): vmax=$(cat /sys/class/qcom-haptics/vmax) fifo_vmax=$(cat /sys/class/qcom-haptics/fifo_vmax)"
