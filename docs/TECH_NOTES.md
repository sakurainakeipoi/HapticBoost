# 技术笔记 · TECH_NOTES

本文件记录本项目的**原理、关键发现、以及踩过的坑**。改代码前请先读完。

---

## 1. 硬件与驱动

| 项 | 值 |
|---|---|
| 机型 | realme RMX3888 |
| 马达 | LRA（线性谐振马达），谐振频率 **174 Hz** |
| 驱动 | Qualcomm `qcom-hv-haptics`（PM8550B） |
| 用户态 | `vendor.oplus.hardware.vibrator-service` |
| 节点 | `/sys/class/qcom-haptics/` |

---

## 2. ★ 核心发现：三条独立通路

这是本项目**最重要的发现**，也是走了最多弯路才确认的。

一开始只调 `vmax`，发现：

- 把 `vmax` 改大 → **来电铃声的震动没有变化**
- 但**按通知、点按键的手感**变了

继续深挖，发现驱动其实有**三个独立可调的电压上限**：

```
/sys/class/qcom-haptics/vmax        # 普通震动（oneshot / notification）
/sys/class/qcom-haptics/fifo_vmax   # 触感反馈（点按、滑动、交互）
/sys/class/qcom-haptics/cl_vmax     # 闭环 / 恒定反馈
```

### 结论
- **用户感知最明显的是 `fifo_vmax`**（日常点按/滑动手感）
- 想让「通知更有感」→ 调 `vmax`
- 三条通路互相独立，**必须分别设置**

> 取值范围 0 ~ 9500（mV）。写入超限值驱动会自动截断，所以「9500」就是硬件天花板。

---

## 3. 触发震动的两条途径

### 3.1 传统通路（受 sysfs 控制）✅
```bash
# 指定时长 + 振幅的震动
cmd vibrator_manager synced oneshot <duration_ms> <amplitude 0-255>

# 系统触感反馈（点按）
cmd vibrator_manager feedback <constant_id>
```
`vmax` / `fifo_vmax` / `cl_vmax` 影响的就是这条通路 → **可以增强**。

### 3.2 AAC RichTap 通路（不受 sysfs 控制）❌
来电铃声、以及部分「高品质触感」走的是**另一个 HAL**：

```
/odm/lib64/libaacvibrator.so
/odm/lib64/vendor.aac.hardware.richtap.vibrator-V2-ndk.so
/odm/etc/aac_richtap.config          # ← 加密的效果参数表
/vendor/firmware/Hapticsconfig.xml
```

**现象**：调用链完全绕过了 `vmax`，所以调 sysfs 对铃声震动**毫无作用**。

**原因推测**：RichTap 的震动效果是按「预编译的效果包」播放的，
每个效果自带振幅/频率/时长参数，最后由 HAL 直接下发到驱动，
不经过 `vmax` 这个「上限」变量（或者说它自己设了上限）。

**本项目的绕过方案**：
`ring_play.sh` 只在响铃期间用 `cmd vibrator_manager synced oneshot` **循环播放满幅震动**，
用「节奏 + 满幅」来盖过系统铃声震动的存在感。

### ⭐ 真凶找到了：`VMAX_HDRM`

从 `dmesg` 抓到了 RichTap 播放时的驱动日志：

```
qcom-hv-haptics ...: custom data 111 length 422 with play-rate 24000 Hz, magnitude=0x5451
qcom-hv-haptics ...: LRA nominal impedance is 9 ohm
qcom-hv-haptics ...: update VMAX_HDRM to 1000 mv      ← ★ 关键
qcom-hv-haptics ...: Set Vmax to 5072 mV
qcom-hv-haptics ...: FIFO programming is done
```

**逐行解读**：
| 日志 | 含义 |
|---|---|
| `custom data 111 length 422` | RichTap HAL 直接塞给驱动的**私有波形数据**，绕过了 Android 的 `VibrationEffect` |
| **`update VMAX_HDRM to 1000 mv`** | **高清触感（HD / RichTap）专用电压上限 = 1000 mV** ← 铃声震动用的就是它 |
| `Set Vmax to 5072 mV` | 普通通路的实际电压（来自效果本身的参数） |
| `LRA nominal impedance is 9 ohm` | 马达阻抗 9Ω（健康参考值） |

👉 **结论**：系统铃声震动走的是 `VMAX_HDRM` 这条通路，
和 sysfs 的 `vmax` / `fifo_vmax` / `cl_vmax` **完全是两套**。
**这才是要攻克的目标**——见 `ROADMAP.md` 路线 C。

---

## 4. 场景检测机制

`watch.sh` 用**三层检测**，从可靠到兜底：

| 优先级 | 来源 | 说明 |
|---|---|---|
| 1 | `dumpsys telecom` | `MODE_RINGTONE` 表示响铃；`MODE_IN_CALL` / `MODE_IN_COMMUNICATION` 表示已接听，必须立即停止补偿震动 |
| 2 | `dumpsys telecom` → `Ringing calls:` 区块 | 统计正在响铃的通话条数 |
| 3 | **推送式 hint 文件** | `ring_listener.sh` 监听 `logcat -b events` |

### 为什么需要第 3 层？
系统电话用前两层就够了，但**微信 / QQ 的 VoIP 通话不走 telecom**，
所以加了 `ring_listener.sh` 监听事件日志：

```
匹配：category=call
      am_foreground_service_start VoipNewForegroundService
      wm_create_activity / wm_on_resume_called  plugin.voip.ui.VideoActivity
结束：notification cancel / foreground service stop / activity destroy
```

命中就把当前时间戳写进 `/data/local/tmp/vib_call_hint`，
`watch.sh` 看到 hint 在 `HINT_WINDOW` 内就认为「来电了」，并把兜底窗口硬限制在 30 秒，
避免结束事件缺失时持续震动。

> ⚠️ 注意：VoIP 的结束判定**必须等 Activity destroy**，
> 否则会在响铃中途误判结束（踩过这个坑）。

---

## 5. 进程架构

```
        [ Magisk service.sh ]  ← 开机，等 boot_completed + 12s
                 │
          [ start.sh ]  ← pkill 旧的 → 拉起下面三个
                 │
     ┌───────────┼────────────┐
     ▼           ▼            ▼
 run_watch   ring_listener  keepalive
     │           │            │ (每 20s 巡检，掉了就拉)
     ▼           │            │
  watch.sh ──────┘◄───────────┘
     │  ▲
     │  └── 读 vib_call_hint
     │
     ├── 写 vmax / fifo_vmax / cl_vmax
     ├── 写 vib_ring_active（每轮刷新）
     └── 启动 ring_play.sh（若 pid 锁不存在）
              │
              ▼
          ring_play.sh  ← 循环 oneshot，直到令牌消失

  看门狗 keepalive.sh 会重启 run_watch.sh / ring_listener.sh
```

### 状态同步方式
**全部靠 `/data/local/tmp/` 下的普通文件**（没有共享内存、没有锁）：

| 文件 | 写入方 | 读取方 | 语义 |
|---|---|---|---|
| `vib_call_hint` | ring_listener | watch | 时间戳，最长 30s 有效；接听或显式响铃结束时会清除 |
| `vib_ring_active` | watch（每轮） | ring_play | 令牌，>20s 未刷新则视为失效 |
| `vib_ring_play.pid` | ring_play | watch | pid 锁，防重复启动 |

**为什么这么设计**：shell 里做 IPC 很难，文件最简单、崩溃后自愈。
**副作用**：手动测试时造的假文件会被 watcher 清掉（见 HANDOFF §5）。

---

## 6. 时序（实测）

| 阶段 | 耗时 |
|---|---|
| 来电 → 检测到 | ≤ `POLL`（默认 1s）+ dumpsys 耗时 |
| 检测到 → 电压切换 | 立即 |
| 检测到 → 首次强震 | ~0.2s |
| 接听 / 挂断 → 取消震动并恢复日常 | ≤ `POLL`（默认 1s） |

内核实测（`dmesg`）：
```
upload constant effect, length = 1000ms, amplitude = 0xff
Set Vmax to 9500 mV
```
→ 每隔 ~2.1s 一次（`RING_DUR=1000` + `RING_GAP=1000` + 命令开销）

---

## 7. 其他有意义的节点

发现在 `/sys/class/qcom-haptics/` 下还有：

| 节点 | 说明 | 值 |
|---|---|---|
| `vibrator_type` | 马达类型 | 809 |
| `lra_frequency_hz` | 谐振频率 | 174 |
| `lra_impedance` | 阻抗 | （只读） |
| `livetap_support` | 实时触感支持 | 0（本机不支持） |
| `primitive_duration` | 原语时长 | 0 |
| `register_write` | **直接写寄存器** | ⚠️ 极度危险，勿碰 |
| `lra_calibration` | 校准 | ⚠️ 勿碰 |

### 另外一个线索：OPPO 的触感等级属性
```
ro.oplus.haptic.lra_level_low      = 162
ro.oplus.haptic.lra_level_middle   = 166
ro.oplus.haptic.lra_level_middle1  = 170
ro.oplus.haptic.lra_level_middle2  = 174
ro.oplus.haptic.lra_level_high     = 178
ro.oplus.haptic.lra_range_max      = 179
ro.oplus.haptic.lra_range_min      = 158
```
这很像 ColorOS 设置里「震动强度」滑块的映射表。
`ro.*` 属性运行时不可改，但**如果能找到 oplus 震动服务实际读取的路径，
或许能从更上层解决问题**——见 `ROADMAP.md` 路线 D。

---

## 8. 坑位清单（速查）

1. `vmax` 不等于手感 → 手感看 `fifo_vmax`
2. 来电铃声震动不吃 sysfs → 只能循环播放绕过
3. `setsid` 必须加，否则进程被父 shell 带走
4. `service.sh` 要等 `sys.boot_completed=1` 再延迟启动
5. `pm install` 读不了 `/sdcard`（fuse 上下文），先拷 `/data/local/tmp/`
6. `sleep` 支持小数（toybox），ms → 秒换算安全
7. 重复实例：`start.sh` 与 `keepalive.sh` 会竞争拉起进程（见 ROADMAP 待修项）
