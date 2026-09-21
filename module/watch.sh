#!/system/bin/sh
# HapticBoost scene watcher v6
# sources: telecom audio-mode / hint(push) ; + hold window
# + ring boost: spawn ring_play.sh (fast loop) while in call
MODDIR=${0%/*}
CFG=$MODDIR/config.sh
LOG=/data/local/tmp/vibrate_boost.log
HB=/data/local/tmp/vibrate_boost_heartbeat
HINTF=/data/local/tmp/vib_call_hint
ACT=/data/local/tmp/vib_ring_active
echo $$ > /data/local/tmp/vibrate_boost.pid

ok() { case "$1" in ''|*[!0-9]*) return 1;; esac; [ "$1" -ge 0 ] && [ "$1" -le 9500 ]; }

DT="dumpsys telecom"
if command -v timeout >/dev/null 2>&1; then DT="timeout 5 dumpsys telecom"; fi
read_mode() {
  echo "$1" | sed -n '/CallAudioModeStateMachine/,/Pending Msg/p' | grep -oE 'Mode MODE_[A-Z_]+' | tail -n 1 | sed 's/.*Mode //'
}
ring_count() {
  echo "$1" | awk '/^ Ringing calls:/{f=1;next} /^ Holding calls:/{f=0} f&&/[^ \t]/{c++} END{print c+0}'
}

LAST=""; HB_AT=0; HOLD_UNTIL=0
while true; do
  [ -f "$CFG" ] && . "$CFG" 2>/dev/null
  ok "$NORMAL_VMAX"      || NORMAL_VMAX=5500
  ok "$NORMAL_FIFO_VMAX" || NORMAL_FIFO_VMAX=4800
  ok "$NORMAL_CL_VMAX"   || NORMAL_CL_VMAX=${CL_VMAX:-1280}
  ok "$NORMAL_CL_VMAX"   || NORMAL_CL_VMAX=1280
  ok "$CALL_VMAX"        || CALL_VMAX=9500
  ok "$CALL_FIFO_VMAX"   || CALL_FIFO_VMAX=9500
  ok "$CALL_CL_VMAX"     || CALL_CL_VMAX=9500
  POLL=${POLL:-1}; case "$POLL" in ''|*[!0-9]*) POLL=1;; esac; [ "$POLL" -lt 1 ] && POLL=1
  HOLD=${HOLD:-3}; case "$HOLD" in ''|*[!0-9]*) HOLD=3;; esac
  HW=${HINT_WINDOW:-90}; case "$HW" in ''|*[!0-9]*) HW=90;; esac
  RP=${RING_PLAY:-1}

  NOW=$(date +%s)
  CALLING=0; SRC="-"
  if [ "${CALL_BOOST:-1}" = "1" ]; then
    TD=$($DT 2>/dev/null)
    MODE=$(read_mode "$TD")
    case "$MODE" in
      MODE_RINGTONE|MODE_IN_CALL|MODE_IN_COMMUNICATION) CALLING=1; SRC="mode:$MODE" ;;
    esac
    if [ "$CALLING" = "0" ]; then
      RC=$(ring_count "$TD")
      [ "$RC" -gt 0 ] && { CALLING=1; SRC="ringList:$RC"; }
    fi
    if [ "$CALLING" = "0" ] && [ -f "$HINTF" ]; then
      HT=$(cat "$HINTF" 2>/dev/null)
      case "$HT" in
        ''|*[!0-9]*) ;;
        *) [ $((NOW - HT)) -le "$HW" ] && { CALLING=1; SRC="hint"; } ;;
      esac
    fi
  fi

  if [ "$CALLING" = "1" ]; then
    HOLD_UNTIL=$((NOW + HOLD))
    V=$CALL_VMAX; F=$CALL_FIFO_VMAX; C=$CALL_CL_VMAX; ST=call
  elif [ "$NOW" -lt "$HOLD_UNTIL" ]; then
    V=$CALL_VMAX; F=$CALL_FIFO_VMAX; C=$CALL_CL_VMAX; ST=call
  else
    V=$NORMAL_VMAX; F=$NORMAL_FIFO_VMAX; C=$NORMAL_CL_VMAX; ST=normal
  fi

  CUR="$ST:$V:$F:$C"
  if [ "$CUR" != "$LAST" ]; then
    echo "$V" > /sys/class/qcom-haptics/vmax      2>/dev/null
    echo "$F" > /sys/class/qcom-haptics/fifo_vmax 2>/dev/null
    echo "$C" > /sys/class/qcom-haptics/cl_vmax   2>/dev/null
    echo "[$(date '+%m-%d %H:%M:%S')] $CUR (src=$SRC)" >> $LOG
    echo "$(date '+%m-%d %H:%M:%S') STATE $CUR src=$SRC" >> $HB
    LAST="$CUR"
  fi

  if [ "$ST" = "call" ] && [ "$RP" = "1" ]; then
    date +%s > $ACT
    if [ ! -f /data/local/tmp/vib_ring_play.pid ]; then
      ( setsid sh $MODDIR/ring_play.sh >/dev/null 2>&1 < /dev/null & )
    fi
  else
    rm -f $ACT
  fi

  N2=$(date +%s)
  if [ $((N2 - HB_AT)) -ge 60 ]; then
    echo "$(date '+%m-%d %H:%M:%S') alive pid=$$ state=$LAST src=$SRC" >> $HB
    HB_AT=$N2
  fi
  sleep "$POLL"
done
