# HapticBoost 兼容性清单

本页只把实际完成测试的设备标为“已实测”。拥有相同处理器、品牌或马达宣传名称，
都不能单独证明兼容；决定性条件是触觉驱动、sysfs 节点及其在当前内核中的行为。

## 已实测设备

| 结果 | 品牌与机型 | 设备代号 | SoC | 触觉控制器 / 驱动 | 马达 | 系统与内核 | Root 环境 | 测试版本 |
|---|---|---|---|---|---|---|---|---|
| ✅ 通过 | 真我 GT5 Pro | RMX3888 / RE5C37 | Qualcomm SM8650（Snapdragon 8 Gen 3） | PM8550B；`qcom,hv-haptics`；Linux 驱动 `qcom-hv-haptics` | X 轴 LRA；174 Hz；驱动报告标称阻抗 9 Ω | Android 16；realme UI V16.1；Linux 6.1.141 | Kitsune Magisk 27001 | HapticBoost 1.2.2（watcher v10 静音修复；2026-10-01 复测） |

公开资料中，realme 将该机型标为 X 轴线性马达；官方安全页面也将
“真我 GT5 Pro”与“RMX3888”对应。设备运行时信息则由 ADB + Root 直接读取。

- [realme GT5 Pro 官方产品页](https://www.realme.com/cn/realme-gt-5-pro)
- [realme 安全页面中的机型代号](https://www.realme.com/security)

## 理论兼容范围

### 先说结论

**在相同驱动实现下，PM8550B 理论上通用。** 这里的“通用”特指手机厂商已经
启用 PM8550B 内置的 HV Haptics、绑定 `qcom-hv-haptics`，并实际暴露本项目所需的
三个可写节点；PM8550B 型号本身不是充分条件。本模块的完整兼容条件是：

```text
高通 HV Haptics 硬件已启用
  + 当前内核实际绑定 qcom-hv-haptics
  + /sys/class/qcom-haptics/ 存在
  + vmax、fifo_vmax、cl_vmax 三个节点均存在且可写
  = 当前版本的核心功能可进入实测
```

高通公开的 PM8550B 设备树确实定义了 `qcom,hv-haptics` 硬件单元，但默认状态为
`disabled`，需要手机厂商在具体设备树中启用。更重要的是，高通基线版
`qcom-hv-haptics` 驱动公开的 class 属性中没有本项目依赖的三个电压节点；
这些节点是部分 OEM 内核增加的扩展。因此不能把“PM8550B”“骁龙”或
“X 轴线性马达”直接等同于“可用”。

- [高通 PM8550B 设备树：HV Haptics 单元](https://android.googlesource.com/kernel/msm-extra/devicetree/+/refs/heads/android-msm-eos-5.15-tm-wear-kr3-pixel-watch/qcom/pm8550b.dtsi)
- [高通基线驱动：支持的 compatible 与原始 class 属性](https://android.googlesource.com/kernel/msm.git/+/9671bc00773d7f73172f1cf38a7cd638091d0214/drivers/input/misc/qcom-hv-haptics.c)

### 控制器 / PMIC 分级

| 控制器或平台条件 | 理论判断 | 当前结论 |
|---|---|---|
| **PM8550B** + OEM 扩展版 `qcom-hv-haptics` + 三个可写节点 | 与实测机结构相同，核心接口理论通用 | 🟡 理论通用；仅 RMX3888 已完成整机实测 |
| **PM8350B** + OEM 扩展版驱动 + 三个可写节点 | 高通驱动有原生匹配项，部分 OPlus 内核也加入三节点 | 🟡 高概率候选，尚无本项目实机报告 |
| **PM5100** + OEM 扩展版驱动 + 三个可写节点 | 高通驱动有原生匹配项，但具体手机启用情况未知 | 🟡 理论候选，必须实机核对 |
| 任意高通 SoC，但使用外置 Awinic、Cirrus 等触觉芯片 | 驱动路径和控制接口不同 | 🔴 当前版本不支持，需要单独适配 |
| 联发科、三星 Exynos、Google Tensor | 不使用本模块硬编码的高通驱动路径 | 🔴 当前版本不支持；并非马达硬件一定不能增强，而是需要另一套驱动适配 |

> PMIC/控制器型号比 SoC 名称更有参考价值，但最终仍以手机上实际存在的节点为准。

### 已发现三节点源码的高概率候选

下表是**公开 OEM 内核源码证据**，不是零售机实测名单。源码中同时定义
`vmax`、`fifo_vmax`、`cl_vmax`，说明该平台具备适配基础；这些属性位于
OPlus 功能编译条件内，具体机型、地区版本和系统版本仍可能未启用。

| OEM 公开内核分支 | 对应产品分支 | 源码结果 | 判定 |
|---|---|---|---|
| OnePlus SM8350 | OnePlus 9 系列分支 | 三节点均有定义 | 🟡 优先征集实测 |
| OnePlus SM8450 | OnePlus 10 Pro 分支 | 三节点均有定义 | 🟡 优先征集实测 |
| OnePlus SM8550 | OnePlus 11 分支 | 三节点均有定义 | 🟡 优先征集实测 |
| OnePlus SM8650 | OnePlus 12 分支 | 三节点均有定义 | 🟡 优先征集实测 |
| realme SM8650 / PM8550B | 真我 GT5 Pro（RMX3888） | 三节点实机存在并完成完整测试 | ✅ 已实测 |

源码证据：

- [OnePlus SM8350 `qcom-hv-haptics.c`](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8350/blob/oneplus/sm8350_t_13.0.0_op9/drivers/input/misc/qcom-hv-haptics.c)
- [OnePlus SM8450 `qcom-hv-haptics.c`](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8450/blob/oneplus/sm8450_v_15.0.0_oneplus_10_pro/drivers/input/misc/qcom-hv-haptics.c)
- [OnePlus SM8550 `qcom-hv-haptics.c`](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8550/blob/oneplus/sm8550_v_15.0.0_oneplus11/drivers/input/misc/qcom-hv-haptics.c)
- [OnePlus SM8650 `qcom-hv-haptics.c`](https://github.com/OnePlusOSS/android_kernel_oneplus_sm8650/blob/oneplus/sm8650_v_15.0.0_oneplus12/drivers/input/misc/qcom-hv-haptics.c)

### 骁龙也不一定兼容：两个反例

| 设备 / 平台源码 | 检查结果 | 当前判断 |
|---|---|---|
| Nothing Phone (1) / SM7325 | 公开驱动中可见 `fifo_vmax`，但没有当前版本要求的完整三节点 | 🟠 不能直接使用，需针对该驱动适配 |
| Nothing Phone (2) / SM8475 | 所核对公开分支未定义本项目要求的三节点 | 🔴 当前版本不兼容 |

- [Nothing Phone (1) SM7325 驱动源码](https://github.com/NothingOSS/android_kernel_msm-5.4_nothing_sm7325/blob/master/drivers/input/misc/qcom-hv-haptics.c)
- [Nothing Phone (2) SM8475 驱动源码](https://github.com/NothingOSS/android_kernel_msm-5.10_nothing_sm8475/blob/master/drivers/input/misc/qcom-hv-haptics.c)

这两个反例说明：**“高通系可能性较大”是筛选方向，不是兼容承诺。**

### RMX3888 硬件证据

| 项目 | 实机读取结果 | 取证位置 |
|---|---|---|
| SoC | `QTI / SM8650` | `ro.soc.manufacturer`、`ro.soc.model` |
| Device Tree compatible | `qcom,hv-haptics` | `/sys/firmware/devicetree/base/.../qcom,hv-haptics@f000/compatible` |
| 触觉 PMIC | `qcom,pm8550b@7` | 设备树路径与 `dmesg` |
| Linux 驱动 | `qcom-hv-haptics` | platform driver 与 `dmesg` |
| 马达类型 | X 轴 LRA | realme 官方规格；内核节点名包含 `lra_*` |
| LRA 频率 | `174 Hz` | `/sys/class/qcom-haptics/lra_frequency_hz` |
| LRA 周期 | `5882 µs`（校准闭环值 `5720 µs`） | `t_lra_us`、`lra_cal_cl_t_lra_us` |
| 标称阻抗 | `9 Ω` | `dmesg` 中 `LRA nominal impedance is 9 ohm` |
| 厂商内部类型值 | `809` | `/sys/class/qcom-haptics/vibrator_type` |
| 马达供应商 / 具体料号 | **未知，系统和公开规格均未暴露** | 不推测、不把内部类型值当成料号 |
| 可调节点 | `vmax`、`fifo_vmax`、`cl_vmax` | `/sys/class/qcom-haptics/` |

### RMX3888 已验证项目

| 测试项 | 结果 |
|---|---|
| Kitsune Magisk 27001 安装模块 ZIP | ✅ |
| 配套 App 安装、Root 读写配置 | ✅ |
| `vmax` / `fifo_vmax` / `cl_vmax` 写入与实时读取 | ✅ |
| 日常通知、触感反馈与预览 | ✅ |
| 系统电话：响铃增强，接听 / 拒接 / 挂断后停止 | ✅ |
| 微信语音通话：响铃增强，接听后进入音频通信模式并停止 | ✅ |
| 系统电话 / 微信来电：静音时不增强，响铃与振动模式正常 | ✅ watcher v10；2026-10-01 用户复测通过 |
| 来电过程中切入静音：停止补偿震动并恢复日常档 | ✅ 离线行为回归通过；系统铃声模式由 AudioService 读取 |
| QQ、Telegram、WhatsApp、Discord 等第三方通话 | ⬜ 尚未实测 |

## 兼容等级

| 等级 | 条件 | 含义 |
|---|---|---|
| ✅ 已实测 | 出现在上方“已实测设备”表，并完成对应测试项 | 可以按记录中的系统、内核和 Root 环境使用 |
| 🟡 待验证 | 使用 `qcom-hv-haptics`，且三个可调节点全部存在 | 核心结构相符，但节点范围、厂商 HAL 和来电行为仍可能不同；必须从低值测试 |
| 🟠 部分兼容 | 只有 `vmax`，缺少 `fifo_vmax` 或 `cl_vmax` | 当前版本不能保证完整工作，需要适配脚本和 App |
| 🔴 不兼容 | `/sys/class/qcom-haptics/` 不存在，或使用其他驱动 | 当前模块不会支持，不能靠相同“X 轴线性马达”宣传名称判断 |

## 如何采集一台新设备的信息

在 Root 终端中执行：

```sh
su -c 'for n in vmax fifo_vmax cl_vmax; do p=/sys/class/qcom-haptics/$n; if [ -e "$p" ] && [ -w "$p" ]; then echo "$n: OK ($(cat "$p"))"; else echo "$n: MISSING_OR_READ_ONLY"; fi; done'
su -c 'ls -l /sys/class/qcom-haptics/ 2>/dev/null'
su -c 'cat /sys/class/qcom-haptics/lra_frequency_hz 2>/dev/null'
su -c 'cat /sys/class/qcom-haptics/vibrator_type 2>/dev/null'
su -c 'dmesg | grep -m 5 -i qcom-hv-haptics'
getprop ro.product.manufacturer
getprop ro.product.model
getprop ro.soc.model
getprop ro.build.version.release
uname -a
```

请勿把 IMEI、序列号、电话号码或完整日志中的账号信息提交到公开 Issue。

## 提交兼容性结果

请使用仓库的“设备兼容性报告”Issue 表单，并至少填写：

- 品牌、公开机型名与设备代号；
- SoC、Android、内核和 Root 方案；
- `qcom-haptics` 目录中的节点；
- 安装、日常震动、系统电话和第三方通话的实际结果；
- 失败时的最短复现步骤。

只有能够复现或带有充分设备证据的报告，才会加入“已实测设备”表。
