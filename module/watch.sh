#!/system/bin/sh
# HapticBoost scene watcher v7
# Only boost while the phone is ringing. Answering, rejecting, or hanging up
# returns to the normal profile and actively stops the synthetic ring pulse.
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

LAST=""; HB_AT=0; WAS_EXPLICIT_RING=0; WAS_RINGING=0
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
  HOLD=${HOLD:-0}; case "$HOLD" in ''|*[!0-9]*) HOLD=0;; esac
  HW=${HINT_WINDOW:-90}; case "$HW" in ''|*[!0-9]*) HW=90;; esac
  [ "$HW" -gt 30 ] && HW=30
  # CALL_BOOST is the single master switch. RING_PLAY remains in config only
  # for backward compatibility with older App/module versions.
  RP=${CALL_BOOST:-1}

  NOW=$(date +%s)
  EXPLICIT_RING=0; ACTIVE_CALL=0; HINT_RING=0; SRC="-"
  if [ "${CALL_BOOST:-1}" = "1" ]; then
    TD=$($DT 2>/dev/null)
    MODE=$(read_mode "$TD")
    case "$MODE" in
      MODE_RINGTONE) EXPLICIT_RING=1; SRC="mode:$MODE" ;;
      MODE_IN_CALL|MODE_IN_COMMUNICATION) ACTIVE_CALL=1; SRC="mode:$MODE" ;;
    esac
    RC=$(ring_count "$TD")
    if [ "$RC" -gt 0 ] && [ "$ACTIVE_CALL" = "0" ]; then
      EXPLICIT_RING=1; SRC="ringList:$RC"
    fi

    # A connected call is never a ringing state. Also clear a stale hint as
    # soon as telecom reports that the call was answered.
    if [ "$ACTIVE_CALL" = "1" ]; then
      rm -f "$HINTF"
    elif [ "$WAS_EXPLICIT_RING" = "1" ] && [ "$EXPLICIT_RING" = "0" ]; then
      # Explicit ringing disappeared: rejected or ended before answer.
      rm -f "$HINTF"
    elif [ "$EXPLICIT_RING" = "0" ] && [ -f "$HINTF" ]; then
      HT=$(cat "$HINTF" 2>/dev/null)
      case "$HT" in
        ''|*[!0-9]*) ;;
        *) [ $((NOW - HT)) -le "$HW" ] && { HINT_RING=1; SRC="hint"; } ;;
      esac
    fi
  fi
  WAS_EXPLICIT_RING=$EXPLICIT_RING

  if [ "$EXPLICIT_RING" = "1" ] || [ "$HINT_RING" = "1" ]; then
    RINGING=1
    V=$CALL_VMAX; F=$CALL_FIFO_VMAX; C=$CALL_CL_VMAX; ST=call
  else
    RINGING=0
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

  if [ "$RINGING" = "1" ] && [ "$RP" = "1" ]; then
    date +%s > $ACT
    if [ ! -f /data/local/tmp/vib_ring_play.pid ]; then
      ( setsid sh $MODDIR/ring_play.sh >/dev/null 2>&1 < /dev/null & )
    fi
  else
    rm -f $ACT
    if [ "$WAS_RINGING" = "1" ]; then
      cmd vibrator_manager cancel >/dev/null 2>&1
      pkill -f "$MODDIR/ring_play.sh" 2>/dev/null
      rm -f /data/local/tmp/vib_ring_play.pid
      echo "$(date '+%m-%d %H:%M:%S') ring STOP src=$SRC" >> $HB
    fi
  fi
  WAS_RINGING=$RINGING

  N2=$(date +%s)
  if [ $((N2 - HB_AT)) -ge 60 ]; then
    echo "$(date '+%m-%d %H:%M:%S') alive pid=$$ state=$LAST src=$SRC" >> $HB
    HB_AT=$N2
  fi
  sleep "$POLL"
done
