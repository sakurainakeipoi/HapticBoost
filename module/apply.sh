#!/system/bin/sh
MODDIR=${0%/*}
. "$MODDIR/config.sh" || exit 1
target=${1:-normal}
if [ "$target" = "call" ]; then
  V=$CALL_VMAX; F=$CALL_FIFO_VMAX; C=$CALL_CL_VMAX
else
  V=$NORMAL_VMAX; F=$NORMAL_FIFO_VMAX; C=$NORMAL_CL_VMAX
fi
valid_voltage() {
  case "$1" in ''|*[!0-9]*) return 1;; esac
  [ "$1" -ge 0 ] && [ "$1" -le 9500 ]
}
valid_voltage "$V" && valid_voltage "$F" && valid_voltage "$C" || exit 1
echo "$V" > /sys/class/qcom-haptics/vmax || exit 1
echo "$F" > /sys/class/qcom-haptics/fifo_vmax || exit 1
echo "$C" > /sys/class/qcom-haptics/cl_vmax || exit 1
[ "$(cat /sys/class/qcom-haptics/vmax)" = "$V" ] &&
  [ "$(cat /sys/class/qcom-haptics/fifo_vmax)" = "$F" ] &&
  [ "$(cat /sys/class/qcom-haptics/cl_vmax)" = "$C" ] || exit 1
echo "applied($target): vmax=$(cat /sys/class/qcom-haptics/vmax) fifo_vmax=$(cat /sys/class/qcom-haptics/fifo_vmax)"
