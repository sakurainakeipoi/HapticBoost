#!/system/bin/sh
# 来电强震铃音：仅在响铃/振动模式播放；静音、接听或挂断停止
MODDIR=${0%/*}
. "$MODDIR/ringer_mode.sh"
. $MODDIR/config.sh 2>/dev/null
HB=/data/local/tmp/vibrate_boost_heartbeat
ACT=/data/local/tmp/vib_ring_active
PIDF=/data/local/tmp/vib_ring_play.pid
RD=${RING_DUR:-1000}; GM=${RING_GAP:-1000}
case "$RD" in ''|*[!0-9]*) RD=1000;; esac
[ "$RD" -lt 100 ] && RD=100
case "$GM" in ''|*[!0-9]*) GM=1000;; esac
GS="$((GM/1000)).$(printf '%03d' $((GM%1000)))"
DA="dumpsys audio"
if command -v timeout >/dev/null 2>&1; then DA="timeout 5 dumpsys audio"; fi
echo $$ > $PIDF
echo "$(date '+%m-%d %H:%M:%S') ring_play START pid=$$ dur=$RD gap=$GM" >> $HB
i=0
while [ $i -lt 900 ]; do
  [ -f "$ACT" ] || break
  A=$(cat "$ACT" 2>/dev/null)
  case "$A" in ''|*[!0-9]*) break;; esac
  NOW=$(date +%s)
  [ $((NOW - A)) -gt 20 ] && break
  # Recheck before every pulse: the phone may have been switched to silent
  # since the watcher last refreshed the token. Never infer mode from volume.
  RING_AUDIO=$($DA 2>/dev/null) || break
  ringer_allows_boost "$RING_AUDIO" || break
  [ -f "$ACT" ] || break
  cmd vibrator_manager synced oneshot "$RD" 255 >/dev/null 2>&1
  sleep "$GS"
  i=$((i+1))
done
cmd vibrator_manager cancel >/dev/null 2>&1
echo "$(date '+%m-%d %H:%M:%S') ring_play END loops=$i" >> $HB
rm -f $PIDF
