# 路线图 · ROADMAP

按「优先级 / 难度」排列。前两节是**开源前该做的**，后四节是**硬核攻坚路线**。

---

## A. 已知待修项（bug / 设计缺陷）

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| A1 | **重复实例**：`start.sh` 与 `keepalive.sh` 可能同时拉起 watch | 多个进程同时写节点，行为不确定 | 加锁：用 `mkdir /data/local/tmp/vb.lock` 做原子锁；或 keepalive 只做「检查 + 单次拉起」 |
| A2 | `restore.sh` 只 `pkill watch.sh` | 其他守护进程仍在跑，可能把值写回去 | 一并 kill `run_watch/keepalive/ring_listener/ring_play`，并 `rm config.sh` 或改成恢复后禁用 |
| A3 | 模态：`watch.sh` 每轮 `dumpsys telecom` | 每秒一次 dumpsys 有微量耗电 | 可改为「事件优先」：平时低频（3-5s），收到 hint 才切高频 |
| A4 | 机型硬编码 | 换个机型就失效 | 启动时探测节点是否存在，不存在则优雅退出并提示 |
| A5 | ~~没有 Magisk 模块的 `customize.sh`~~ | ✅ **已解决** | 已加 `customize.sh`：机型检测 + 补权限 + 自动装 App |

---

## B. 开源发布前（必做）

- [ ] **通用化**：节点路径探测 + 不支持机型时友好提示（对应 A4）
- [x] **打包**：`module/` → `haptic-boost-vX.Y.zip` ✅ **已完成**（`tools/build-module-zip.sh`，含内嵌 App）
- [x] **图标**：App 已加入自绘 adaptive icon
- [ ] **截图**：更新 `docs/screenshot.png`
- [ ] **Release Note** 写清支持机型与风险
- [ ] 换成自己的 **release keystore** 签名
- [x] 确认 `LICENSE` / `module.prop` 的作者名 ✅ **已完成**（`法秋`）

---

## C. 攻坚路线：真正控制「来电铃声震动」

> 背景见 `TECH_NOTES.md` §3.2。
> 当前是「绕过」（自己循环播放震动），以下才是根治。

### 🅰 路线 A：破解 / 替换 `aac_richtap.config`
- **目标文件**：`/odm/etc/aac_richtap.config`（加密）
- **思路**：这是 RichTap 的「效果参数表」。若能解析格式并**放大其中的振幅系数**，
  就能让系统铃声震动本身变强（而不是靠循环播放盖过去）。
- **步骤**：
  1. 拉出来：`adb pull /odm/etc/aac_richtap.config`
  2. 熵分析 / 文件类型识别（可能是自定义二进制或混淆过的）
  3. Ghidra 逆向 `libaacvibrator.so`，找解密/解析函数
  4. 复现「读配置」逻辑，导出明文结构
  5. 修改振幅字段 → 回写（需要 `magisk --clone` 或 overlay 挂载）
- **难度**：★★★★☆　**收益**：★★★★★（真正的根治）

### 🅱 路线 B：Hook HAL 层
- **目标**：拦截 `vendor.aac.hardware.richtap.vibrator-V2-ndk.so` 的 `SetAmplitude`/`play`
- **手段**：LSPosed / Zygisk native hook（或直接改 `.so` 的常量再 overlay）
- **优点**：不用理解配置格式
- **难度**：★★★★☆　**风险**：动 HAL 可能影响开机触感甚至系统稳定性

### 🅲 路线 C（★ 首选 · 已有确凿证据）：改 `VMAX_HDRM`

- **证据**：`dmesg` 里 RichTap 播放时，驱动会打印：
  ```
  qcom-hv-haptics ...: update VMAX_HDRM to 1000 mv      ← 高清触感专用电压上限
  qcom-hv-haptics ...: Set Vmax to 5072 mV
  ```
  → 系统铃声震动走的是 **`VMAX_HDRM`**（1000 mV），**根本不是** sysfs 的 `vmax`。
- **思路**：`VMAX_HDRM` 是驱动内部变量，来源通常是这三种之一：
  1. **DT 属性**（如 `qcom,vmax-hdrm-mv`）→ 改设备树 / overlay
  2. **`aac_richtap.config` 里的参数** → 改配置（配合路线 A）
  3. **驱动里硬编码** → 需要重新编译内核模块
- **排查步骤**：
  ```bash
  # a) 找 DT 里的属性（节点名已知）
  adb shell su -c 'ls /proc/device-tree/soc@0/*pm8550b*/qcom,hv-haptics@f000/'
  # b) 在高通内核源码里搜 VMAX_HDRM（drivers/haptics/qcom-hv-haptics.c）
  # c) 看 richtap.config 里有没有对应字段
  grep -a -i 'hdrm\|mv' /odm/etc/aac_richtap.config | head
  ```
- **难度**：★★★☆☆　**收益**：★★★★★（真正根治，铃声震动原生变强）
- **风险**：★★☆☆☆（只改电压上限，比动 HAL 温和得多）
- **注意**：DT 修改在很多机型上会被签名/校验拦住，**优先试 (2) 配置法**

### 🅲' 路线 C-备用：彻底绕过 RichTap
- **思路**：让框架**别用** RichTap HAL，改用 `cmd vibrator_manager` 那条路，
  这样现有的 `vmax` 调节就能直接作用于铃声震动。
- **手段**：属性开关 / overlay 屏蔽 HAL 的 VINTF 声明让服务回退
- **难度**：★★★☆☆　**风险**：★★★★☆（改错会完全没震动，需安全模式回滚）

### 🅳 路线 D（最优雅，建议优先试）：从 OPPO 上层入手
- **线索**：本机存在这些属性
  ```
  ro.oplus.haptic.lra_level_low/middle/middle1/middle2/high = 162/166/170/174/178
  ro.oplus.haptic.lra_range_min/max = 158 / 179
  ```
  这几乎可以确定是 ColorOS「设置 → 声音与振动 → 振动强度」滑块的等级映射。
- **待查**：
  1. 这些 `ro.*` 是**开机只读**，但 oplus 震动服务是否有对应的 `persist.*` 或 sysfs？
  2. 在 `vendor.oplus.hardware.vibrator-service` 里 grep 这些 key → 看它到底怎么用
  3. 如果它读取的是可写路径 → **一条命令就能改全系统强度**（含铃声）
- **难度**：★★☆☆☆　**收益**：★★★★★（如果成立，比 A/B/C 都干净）
- **做法**：
  ```bash
  adb pull /vendor/bin/hw/vendor.oplus.hardware.vibrator-service
  strings vendor.oplus.hardware.vibrator-service | grep -i haptic
  # 然后 Ghidra 看它对 lra_level_* 的处理
  ```

---

## D. 其他想做的（锦上添花）

- [ ] **更多场景档**：充电、游戏、闹钟…… 每档独立
- [ ] **音量键快捷调节**：长按音量键切档
- [ ] **App 内实时预览波形**：拖动滑块时直接震动
- [ ] **支持更多机型**：把节点路径做成「驱动 profile」表
- [ ] **Tasker / 快捷方式集成**
- [ ] **模块 zip 内嵌 App**：装模块时自动装 App

---

## E. 不建议做的事（出于安全）

- ❌ 用 `register_write` 直接写马达寄存器
- ❌ 修改 `lra_calibration`
- ❌ 修改任何 `/vendor` `/odm` 下的**真实文件**（要用 overlay / `magisk --clone`）
- ❌ 把 AMP 拉到远超 9500（驱动会截断，但别依赖这个兜底）
