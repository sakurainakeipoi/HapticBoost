#!/system/bin/sh
# 开机启动：等系统完全起来后再拉起来，绝不参与开机流程
MODDIR=${0%/*}
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 3; done
sleep 12
sh "$MODDIR/apply.sh" normal >> /data/local/tmp/vibrate_boost.log 2>&1
sh "$MODDIR/start.sh" >> /data/local/tmp/vibrate_boost_heartbeat 2>&1