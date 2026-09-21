# HapticBoost · 震动增强

> 让线性马达「用尽全力」——通过驱动电压（sysfs）精确调节安卓线性马达的震动强度，
> 并在响铃时自动切换到**来电增强**，接听或挂断立即停止。

一个 Root + Magisk 的轻量模块（纯 sysfs 写入，**不修改任何系统文件**），
配一个原生小 App 用来可视化调参。

---

## ✨ 特性

- **三条通路独立调节**（这是本项目的核心发现）
  | 通路 | 影响的震动 |
  |---|---|
  | `vmax` | 普通震动（oneshot / 通知 / App 震动） |
  | `fifo_vmax` | **触感反馈**（点按、滑动、系统交互手感） |
  | `cl_vmax` | 闭环 / 恒定反馈（部分长震） |
- **场景自动切换**：日常一档、响铃一档；接听、拒接或挂断后立即恢复。
- **来电增强**：一个开关同时控制来电电压和补偿震动，绕开系统铃声震感偏弱的问题。
- **Material 3 调参 App**：动态取色、深色模式、实时状态卡、滑块松手预览；应用后立即生效并在重启后保留。
- **纯 sysfs + `cmd`，零系统文件改动**：不影响开机，可安全模式一键回滚。

---

## 📱 兼容性

> **一句话**：本模块绑定的是**驱动与 sysfs 节点**，不是只看品牌或处理器。
> 出现 `qcom-hv-haptics` 只能说明“可能兼容”；必须同时核对节点并实际测试。
> **PM8550B 是高概率候选，不是单独的兼容保证。**

### 已实测机型

| 状态 | 手机 | 处理器 / 触觉控制器 | 马达信息 | 系统 / Root | 已验证范围 |
|---|---|---|---|---|---|
| ✅ 实机通过 | **真我 GT5 Pro（RMX3888）** | Snapdragon 8 Gen 3（SM8650） / **PM8550B** / `qcom-hv-haptics` | 官方标注 X 轴线性马达；驱动报告 **LRA 174 Hz、标称 9 Ω**；供应商与具体料号未公开 | Android 16、realme UI V16.1、Kitsune Magisk 27001 | 三路电压、日常触感、系统电话、微信来电与接听停止、App 调参 |

> 上表中的机型名称和 X 轴线性马达来自 [realme 官方产品页](https://www.realme.com/cn/realme-gt-5-pro)，
> 驱动、频率、阻抗和 PMIC 信息来自该测试机的 sysfs、设备树与内核日志。
> 更完整的硬件证据、测试项目和机型反馈格式见 **[兼容性清单](docs/COMPATIBILITY.md)**。

| 层级 | 判定依据 | 覆盖范围 | 状态 |
|---|---|---|---|
| 🟢 **核心功能**<br>（电压调节 / 场景切换） | `/sys/class/qcom-haptics/` 下同时存在且可写 `vmax`、`fifo_vmax`、`cl_vmax` | 使用 **`qcom-hv-haptics`** 且节点语义一致的设备 | ✅ 仅 RMX3888 / PM8550B 已实测；其他机型属于待验证 |
| 🟡 **来电强震铃音**<br>（`ring_play` 绕过方案） | AAC RichTap HAL 存在 | 目前确认 **OPPO / realme** 需要它<br>其他品牌铃声震动可能本来就受 `vmax` 控制，**不需要**这个补偿 | ⚠️ 仅 OPPO/realme 验证 |
| 🔴 **当前不支持** | 驱动或节点不同 | 联发科 / 三星 Exynos / Google Tensor，以及采用外置 Awinic、Cirrus 等触觉芯片的高通机型 | ❌ 需要另做驱动适配 |

### 理论候选范围（不等于已支持）

| 范围 | 公开证据 | 当前判断 |
|---|---|---|
| **PM8550B** | 高通设备树包含 `qcom,hv-haptics`；RMX3888 已验证 | 🟡 高概率候选，但仍必须检查三节点 |
| **PM8350B / PM5100** | 高通驱动包含对应匹配项 | 🟡 理论候选，尚无本项目实机报告 |
| OnePlus 9 / 10 Pro / 11 / 12 对应的 SM8350 / SM8450 / SM8550 / SM8650 公开内核分支 | OEM 源码同时定义三个电压节点 | 🟡 优先征集实测；不保证每个地区与系统版本均启用 |
| 其他骁龙设备 | SoC 型号不能说明触觉控制器和 OEM 节点实现 | ⚪ 先检查节点，不能直接判断 |

高通基线驱动本身没有本项目使用的三个 OEM 扩展节点，因此“骁龙”或“PM8550B”
都不能单独作为兼容结论。完整源码证据、反例和分级表见
**[兼容性清单](docs/COMPATIBILITY.md#理论兼容范围)**。

### 怎么 30 秒判断自己能不能用？

```bash
su -c 'for n in vmax fifo_vmax cl_vmax; do p=/sys/class/qcom-haptics/$n; [ -e "$p" ] && [ -w "$p" ] && echo "$n: OK" || echo "$n: MISSING_OR_READ_ONLY"; done'
```

| 结果 | 含义 |
|---|---|
| 三项均显示 `OK` | 🟡 **满足核心条件，仍需从低电压开始实测** |
| 只有部分节点 | 🟠 **当前安装包会拒绝安装，需要针对该驱动适配** |
| 任一项显示 `MISSING_OR_READ_ONLY` | 🔴 **当前版本不能完整支持** |

> 模块的 `customize.sh` 会自动做这个检测并给出提示。

### 要求

| 项目 | 要求 |
|---|---|
| Root | ✅ 必需（Magisk / KernelSU / APatch 均可） |
| 系统 | Android 11+（`cmd vibrator_manager` 要求）<br>已在 **Android 16** 验证 |
| 驱动 | **Qualcomm `qcom-hv-haptics`** |
| 已验证机型 | 真我 GT5 Pro（RMX3888）· SM8650 · PM8550B · X 轴 LRA 174 Hz · Android 16 · 内核 6.1 |

> ⚠️ **节点名可能随驱动版本变化**：本项目使用的是
> `vmax` / `fifo_vmax` / `cl_vmax` 三个节点。
> 如果你的驱动里名字不同，需要自行调整（见 `docs/TECH_NOTES.md`）。

---

## 🔧 安装

### 方式一：刷模块 zip（推荐 · 一步到位）

1. 到 **Release** 下载 `haptic-boost-vX.Y.zip`
2. 面具 → 模块 → 从本地安装 → 选择该 zip → **重启**
3. ✅ **配套 App 会自动装好**（zip 内嵌了 App，`customize.sh` 会在安装时执行 `pm install`）

> 安装时 `customize.sh` 会自动做三件事：
> ① 机型检测（无 `qcom-haptics` 驱动会提示）② 补脚本可执行权限 ③ 安装配套 App

### 方式二：手动安装（开发调试用）

```bash
# 1) 推脚本
adb push module/* /data/adb/modules/haptic_boost_qcom/
adb shell su -c 'chmod 0755 /data/adb/modules/haptic_boost_qcom/*.sh'

# 2) 装 App（必须先拷到 /data/local/tmp，system_server 读不了 /sdcard）
adb push dist/HapticBoost-1.2.1-release.apk /data/local/tmp/
adb shell su -c 'pm install -r /data/local/tmp/HapticBoost-1.2.1-release.apk'
```

### 自己打包 zip

```bash
bash tools/build-module-zip.sh
# → dist/haptic-boost-v1.2.1.zip
```

发布包同时包含新式根目录模块结构与 `META-INF` 兼容入口，可由 KernelSU、
新版 Magisk，以及仍使用 Magisk 27 安装流程的 Kitsune Manager 安装。

### 验证是否生效

```bash
su -c 'cat /sys/class/qcom-haptics/vmax'        # 当前电压
su -c 'cat /data/local/tmp/vibrate_boost.log'   # 场景切换日志
su -c 'tail /data/local/tmp/vibrate_boost_heartbeat'   # 心跳
```

---

## 🎛️ 使用

### 方式一：App（推荐）
打开「震动增强」即可在顶部查看当前档位和三个实时电压节点。
- **震感设置**：日常触感与来电增强集中在同一张控制卡
- **来电增强**：一个开关同时启用来电电压和补偿震动，支持 3 轮预览
- 所有滑块都会实时显示数值，松手时轻震预览；「恢复原厂」需要二次确认

### 方式二：手改配置
```bash
su -c 'vi /data/adb/modules/haptic_boost_qcom/config.sh'
su -c 'sh /data/adb/modules/haptic_boost_qcom/start.sh'   # 重载
```

**调参参考**
| 想要的效果 | 建议 |
|---|---|
| 更猛的回弹手感 | 提高 `NORMAL_FIFO_VMAX`（**最明显的一档**） |
| 通知更"有感" | 提高 `NORMAL_VMAX` |
| 来电更炸 | 提高 `CALL_*`（9500 = 硬件上限） |
| 铃声震动更密集 | `RING_DUR`↓ + `RING_GAP`↓ |

---

## 🗂️ 目录结构

```
HapticBoost/
├── settings.gradle.kts     # Gradle 工程入口
├── build.gradle.kts        # Android Gradle Plugin 版本
├── gradlew / gradlew.bat   # Gradle Wrapper
├── module/                 # Magisk 模块（核心）
│   ├── module.prop         # 模块声明
│   ├── customize.sh        # ★ 安装时执行：机型检测 + 补权限 + 自动装 App
│   ├── service.sh          # 开机自启入口（等 boot_completed 后才启动）
│   ├── start.sh            # 拉起全部守护进程
│   ├── watch.sh            # ★ 场景监听 + 电压切换主逻辑
│   ├── ring_listener.sh    # 推送式来电监听（含微信/QQ VoIP）
│   ├── ring_play.sh        # ★ 来电循环强震
│   ├── keepalive.sh        # 看门狗（进程掉了就拉起来）
│   ├── run_watch.sh        # watch 的 wrapper
│   ├── apply.sh            # 按场景写节点
│   ├── restore.sh          # 一键恢复原厂
│   └── config.sh           # 配置（App 会改这个文件）
├── app/                    # Android App 源码
│   ├── build.gradle.kts
│   ├── src/main/java/io/github/sakurainakeipoi/hapticboost/MainActivity.java
│   ├── src/main/res/       # Material 3 布局、主题与 adaptive icon
│   └── legacy/             # 已停用的手搓构建链，仅供参考
├── docs/
│   ├── TECH_NOTES.md       # ★ 技术原理与关键发现
│   ├── BUILD.md            # 构建说明（含迁移 Gradle 的建议）
│   ├── COMPATIBILITY.md    # 已实测机型、硬件证据与反馈格式
│   ├── ROADMAP.md          # 未攻克项与攻坚路线
│   └── screenshot.png
├── tools/
│   └── build-module-zip.sh # 一键打包模块 zip
├── dist/                   # 预编译产物（已被 .gitignore 忽略）
│   ├── haptic-boost-v1.2.1.zip       # ← 可直接刷的模块包
│   └── HapticBoost-1.2.1-release.apk
├── HANDOFF.md              # ★ 交接文档（给协作者 / 其他 AI）
├── LICENSE                 # MIT
└── README.md
```

---

## 🧠 工作原理（一句话版）

```
正在响铃        → watch.sh 检测（dumpsys telecom + logcat 事件 + 推送 hint）
                → 写入 vmax / fifo_vmax / cl_vmax = 满幅
                → 同时拉起 ring_play.sh 循环播放满幅震动
接听/拒接/挂断  → 取消震动并立即恢复日常档
```

详细原理、踩过的坑、以及「为什么系统铃声震动不听 sysfs 的」——
请见 **[docs/TECH_NOTES.md](docs/TECH_NOTES.md)**。

---

## 🛡️ 安全性

- ✅ **只写 `/sys`**，不碰任何系统分区文件 → **不会影响开机**
- ✅ 写入值由驱动硬限幅（>9500 会自动截断）
- ✅ 配置非法时自动回退默认值（`watch.sh` 有 `ok()` 校验）
- ✅ `restore.sh` 一键恢复原厂
- 🔴 极端情况可**开机按音量下进安全模式**禁用全部模块

---

## ⚠️ 免责声明

本项目会修改马达的驱动电压。虽然驱动有限幅保护、且不修改系统文件，
但**任何对硬件参数的操作都存在风险**（发热、耗电增加、马达寿命）。
请自行评估，**风险自担**。请务必备份数据并确认会使用安全模式回滚。

---

## 📄 许可

[MIT](LICENSE)
