# HapticBoost 兼容性清单

本页只把实际完成测试的设备标为“已实测”。拥有相同处理器、品牌或马达宣传名称，
都不能单独证明兼容；决定性条件是触觉驱动、sysfs 节点及其在当前内核中的行为。

## 已实测设备

| 结果 | 品牌与机型 | 设备代号 | SoC | 触觉控制器 / 驱动 | 马达 | 系统与内核 | Root 环境 | 测试版本 |
|---|---|---|---|---|---|---|---|---|
| ✅ 通过 | 真我 GT5 Pro | RMX3888 / RE5C37 | Qualcomm SM8650（Snapdragon 8 Gen 3） | PM8550B；`qcom,hv-haptics`；Linux 驱动 `qcom-hv-haptics` | X 轴 LRA；174 Hz；驱动报告标称阻抗 9 Ω | Android 16；realme UI V16.1；Linux 6.1.141 | Kitsune Magisk 27001 | HapticBoost 1.2.1 |

公开资料中，realme 将该机型标为 X 轴线性马达；官方安全页面也将
“真我 GT5 Pro”与“RMX3888”对应。设备运行时信息则由 ADB + Root 直接读取。

- [realme GT5 Pro 官方产品页](https://www.realme.com/cn/realme-gt-5-pro)
- [realme 安全页面中的机型代号](https://www.realme.com/security)

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
su -c 'ls -l /sys/class/qcom-haptics/'
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
