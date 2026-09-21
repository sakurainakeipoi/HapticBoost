#!/system/bin/sh
L=/data/local/tmp/watch_out.log
echo "[$(date "+%m-%d %H:%M:%S")] wrapper start ppid=$PPID" >> $L
sh /data/adb/modules/haptic_boost_qcom/watch.sh >> $L 2>&1
echo "[$(date "+%m-%d %H:%M:%S")] wrapper end rc=$?" >> $L
