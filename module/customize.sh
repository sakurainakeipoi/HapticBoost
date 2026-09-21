#!/system/bin/sh
# ============================================================
#  Magisk / KernelSU / APatch 模块安装脚本
#  在「面具 App 里点安装」时执行（此时系统已启动）
# ============================================================

ui_print "*********************************"
ui_print "   HapticBoost · 震动增强"
ui_print "*********************************"
ui_print " "

# ---------- 1. 驱动 / 兼容性检测 ----------
NODE_DIR=/sys/class/qcom-haptics
NODES=""
MISS=0
for n in vmax fifo_vmax cl_vmax; do
  if [ -e "$NODE_DIR/$n" ]; then NODES="$NODES $n"; else MISS=$((MISS + 1)); fi
done

if [ ! -d "$NODE_DIR" ]; then
  ui_print "!================================="
  ui_print "!  未检测到 qcom-hv-haptics 驱动"
  ui_print "!  本模块不支持你的设备"
  ui_print "!  （常见不支持：联发科 / 三星 / Tensor）"
  ui_print "!================================="
  ui_print "!  仍会安装，但在本机不会生效"
elif [ "$MISS" -gt 0 ]; then
  ui_print "~  检测到驱动，但节点不完整"
  ui_print "   可用:$NODES"
  ui_print "   缺失 $MISS 个（可能是较老版本的驱动）"
  ui_print "   → 功能可能不完整"
else
  ui_print "- 兼容性检测通过 ✓"
  ui_print "  qcom-hv-haptics 全部节点就绪"
  ui_print "  当前 vmax = $(cat "$NODE_DIR/vmax" 2>/dev/null) mV"
fi

# 判断是不是 OPPO/realme 系（决定「来电强震铃音」是否必要）
if [ -e /odm/etc/aac_richtap.config ] || [ -e /vendor/bin/hw/vendor.oplus.hardware.vibrator-service ]; then
  ui_print "- 检测到 AAC RichTap HAL（OPPO/realme 系）"
  ui_print "  → 系统铃声震动绕开了 vmax，需要「来电强震铃音」补偿 ✓"
else
  ui_print "- 未检测到 RichTap HAL"
  ui_print "  → 你的铃声震动可能本来就受 vmax 控制"
  ui_print "  → 如不需要请把 config.sh 里 RING_PLAY 设为 0"
fi
ui_print " "

# ---------- 2. 脚本可执行权限 ----------
# zip / 手机存储传输时经常丢失可执行位，这里统一补上
for f in "$MODPATH"/*.sh; do
  [ -f "$f" ] && chmod 0755 "$f" 2>/dev/null
done
ui_print "- 脚本权限已设置 ✓"
ui_print " "

# ---------- 3. 自动安装配套 App ----------
APK="$MODPATH/HapticBoost.apk"
if [ -f "$APK" ]; then
  ui_print "- 正在安装配套 App（震动增强）…"
  # ⚠️ 必须先拷到 /data/local/tmp：
  #    system_server 读不了 /data/adb（权限）也读不了 /sdcard（fuse 上下文）
  cp -f "$APK" /data/local/tmp/HapticBoost.apk 2>/dev/null
  chmod 0644 /data/local/tmp/HapticBoost.apk 2>/dev/null
  if pm install -r /data/local/tmp/HapticBoost.apk >/dev/null 2>&1; then
    ui_print "  App 安装成功 ✓"
    ui_print "  打开桌面「震动增强」即可图形化调参"
  else
    ui_print "  ! App 自动安装失败"
    ui_print "  ! 请手动安装包内的 HapticBoost.apk"
  fi
  rm -f /data/local/tmp/HapticBoost.apk 2>/dev/null
else
  ui_print "- 未随包附带 App（可单独安装 APK）"
fi
ui_print " "

# ---------- 4. 配置文件 ----------
if [ -f "$MODPATH/config.sh" ]; then
  ui_print "- 配置文件已就位 ✓"
else
  ui_print "! 缺少 config.sh，将使用内置默认值"
fi
ui_print " "

# ---------- 5. 收尾 ----------
ui_print "*********************************"
ui_print "  安装完成！重启后生效"
ui_print "  配置：/data/adb/modules/haptic_boost_qcom/config.sh"
ui_print "  卸载前请先执行 restore.sh 恢复原厂值"
ui_print "*********************************"
