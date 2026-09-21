# HANDOFF · 交接文档

> **给接手者（人或 AI）**：读完这一份，你就能无缝继续这个项目，不用重新踩坑。
> 项目当前**已经可用、正在用户手机上运行**。你的任务是「美化 + 开源发布」以及可选的「深挖路线」。

---

## 0. 一句话说明

一个 **Magisk 模块 + 安卓 App**，通过 sysfs 调节高通线性马达（`qcom-hv-haptics`）的驱动电压，
实现「日常 / 来电分场景增强震动」+「来电循环强震铃音」。

- **作者**：法秋
- **开发环境**：Android 手机上的 AI Agent（root + proot Ubuntu）
- **源码位置（手机）**：`/sdcard/HapticBoost/`（即本目录）
- **运行位置（手机）**：`/data/adb/modules/haptic_boost_qcom/`
- **App 包名**：`io.github.sakurainakeipoi.hapticboost`（显示名「震动增强」）

---

## 1. 当前状态（已实现，已验证）

| 功能 | 状态 | 验证方式 |
|---|---|---|
| 日常 / 来电 双档电压切换 | ✅ 工作 | `cat /data/local/tmp/vibrate_boost.log` |
| 来电检测（系统电话） | ✅ 工作 | `dumpsys telecom` |
| 来电检测（微信/QQ VoIP） | ✅ 工作 | `logcat -b events` |
| 来电循环强震铃音 | ✅ 工作 | `dmesg \| grep -i haptic` |
| 挂断自动恢复 | ✅ 工作 | 日志 `STATE normal` |
| 看门狗（进程自愈） | ✅ 工作 | `keepalive.sh` |
| 开机自启 | ✅ 工作 | `service.sh` |
| 可视化调参 App | ✅ 已迁移 Gradle / Material 3 | `./gradlew :app:assembleDebug` |
| 一键恢复原厂 | ✅ 有 | `restore.sh` |

**当前守护进程**（应为各 1 个实例）：
```bash
# 期望输出：watch / ring_listener / keepalive 各一个
for s in watch ring_listener keepalive; do
  echo "$s: $(pgrep -f "haptic_boost_qcom/$s.sh" | tr '\n' ' ')"
done
```

---

## 2. 设备事实（务必记住，不要重新探测）

```
品牌/型号 : realme / RMX3888
系统      : Android 16 (SDK 36)
内核      : 6.1.141-android14-11-o-g6ed3e67335d5
驱动      : qcom-hv-haptics (PM8550B)
马达      : LRA, 谐振 174 Hz
```

### 2.1 sysfs 节点（本项目的主战场）

```
/sys/class/qcom-haptics/vmax         # 普通震动
/sys/class/qcom-haptics/fifo_vmax    # 触感反馈（点按/滑动）
/sys/class/qcom-haptics/cl_vmax      # 闭环 / 恒定反馈
/sys/class/qcom-haptics/vibrator_type  # = 809
```

- 取值范围 **0 ~ 9500**（mV），超限由驱动自动截断
- **三条通路互相独立**，这是踩了很多坑才确认的（见 `docs/TECH_NOTES.md`）

### 2.2 触发震动的命令

```bash
# 满幅长震（毫秒, 振幅 0~255）—— ring_play.sh 用的就是这条
cmd vibrator_manager synced oneshot <duration_ms> 255

# 系统触感反馈（点按手感）
cmd vibrator_manager feedback <constant_id>
```

### 2.3 ⚠️ 关键结论：来电铃声的震动「不听 sysfs」

系统铃声震动走的是 **AAC RichTap 通路**（一个独立 HAL），**不受上述节点控制**：

```
/odm/etc/aac_richtap.config                                  # 加密的效果配置
/odm/lib64/libaacvibrator.so
/odm/lib64/vendor.aac.hardware.richtap.vibrator-V2-ndk.so
/vendor/bin/hw/vendor.oplus.hardware.vibrator-service        # OPPO/realme 震动 HAL
/vendor/firmware/Hapticsconfig.xml                           # OPPO 触感配置
```

👉 因此「来电时更猛」目前的实现是 **绕过去**：
`ring_play.sh` 用 `cmd vibrator_manager` 自己循环播放满幅震动，而不是去改 RichTap。
**想彻底解决，见 `docs/ROADMAP.md` 的三条路线。**

---

## 3. 文件地图与职责

```
module/
  module.prop        Magisk 模块声明（author = 法秋 ✅）
  service.sh         开机入口：等 sys.boot_completed=1 后再 sleep 12 秒启动
  start.sh           清理旧进程 + 拉起 run_watch / ring_listener / keepalive
  watch.sh           ★核心：轮询场景 → 写节点 → 拉起/停止 ring_play
  ring_listener.sh   监听 logcat events，写「来电提示文件」hint
  ring_play.sh       ★来电循环强震（读 RING_DUR / RING_GAP）
  keepalive.sh       看门狗：发现进程不在就重启
  run_watch.sh       watch.sh 的 wrapper（把 stdout 重定向到日志）
  apply.sh           按 normal/call 写三个节点
  restore.sh         恢复原厂值（1280 / 7700 / 1280）
  config.sh          配置（默认模板已给出，App 会改写）

app/
  build.gradle.kts                            Android App 模块配置
  src/main/java/io/github/sakurainakeipoi/hapticboost/MainActivity.java
  src/main/res/layout/activity_main.xml       Material 3 主界面
  src/main/res/                               主题 / adaptive icon / 文案资源
  legacy/                                     已停用的手搓构建链

docs/   TECH_NOTES.md / BUILD.md / ROADMAP.md
```

### 3.1 运行时文件（都在 `/data/local/tmp/`）

| 文件 | 作用 |
|---|---|
| `vibrate_boost.log` | 场景切换记录 |
| `vibrate_boost_heartbeat` | 心跳 + 事件（`STATE` / `alive` / `ring_play`） |
| `vib_call_hint` | 一次性来电提示（内容 = unix 时间戳） |
| `vib_ring_active` | ring_play 的「继续震」令牌，watcher 每轮刷新 |
| `vib_ring_play.pid` | ring_play 的 pid 锁（防重复启动） |

### 3.2 进程间「协议」（改代码前必须理解）

```
ring_listener.sh  --(写 vib_call_hint)-->  watch.sh
watch.sh          --(写 vib_ring_active, 每轮刷新)-->  ring_play.sh
watch.sh          --(检查 vib_ring_play.pid 不存在才启动)-->  ring_play.sh
ring_play.sh      --(令牌消失 或 超过 20s 未刷新 就退出)-->
keepalive.sh      --(每 20s 检查，掉了就拉起)-->  run_watch.sh / ring_listener.sh
```

> 设计要点：**没有任何"共享内存/锁"**，全靠 `/data/local/tmp/` 下的文件做状态同步。
> 好处是简单、崩溃可恢复；坏处是**测试时要小心手动造文件会被 watcher 清掉**。

---

## 4. 常用调试命令（照抄即可）

```bash
# 看实时状态
tail -f /data/local/tmp/vibrate_boost_heartbeat

# 内核级证据（最硬的证据）
dmesg -C && sleep 5 && dmesg | grep -iE 'qcom-hv-haptics'

# 当前节点值
for n in vmax fifo_vmax cl_vmax; do echo "$n=$(cat /sys/class/qcom-haptics/$n)"; done

# 手动模拟一次来电（注意：watcher 会刷新自己的令牌，别手动造 vib_ring_active）
date +%s > /data/local/tmp/vib_call_hint
sleep 12 && dmesg | grep -c 'upload constant effect'
rm -f /data/local/tmp/vib_call_hint      # 模拟挂断

# 重启所有守护进程
sh /data/adb/modules/haptic_boost_qcom/start.sh

# 一键恢复原厂
sh /data/adb/modules/haptic_boost_qcom/restore.sh
```

---

## 5. 已知的坑（血泪，别重犯）

1. **`vmax` ≠ 手感**：用户感知最明显的是 `fifo_vmax`（触感反馈）。只调 `vmax` 会觉得「没变化」。
2. **系统铃声震动不受 sysfs 控制**（见 §2.3），只能循环播放绕过。
3. **`setsid` 必须用**：否则进程会随父 shell 退出被杀。
4. **`service.sh` 里不要干重活**：Magisk 的开机阶段很敏感，必须等 `sys.boot_completed=1` 再 `sleep` 一下。
5. **旧手搓 AXML 已停用**：标准 Gradle 工程是唯一 App 发布入口；旧脚本仅保存在 `app/legacy/`。
6. **`sleep` 支持小数**：`sleep 0.5` 可用（toybox），所以 `RING_GAP` 用 ms 换算成秒是安全的。
7. **`pm install` 不能读 `/sdcard`**：system_server 读不了 fuse 上下文，必须先把 apk 拷到 `/data/local/tmp/`。
8. **重复实例问题**：曾经出现过 3 个 `watch.sh` 同时跑（keepalive 与 start.sh 竞争）。
   目前靠 `start.sh` 先 `pkill` 再启动规避，**但根因未彻底解决**（同时启动两个 keepalive 会互相打架）。

---

## 6. 你的任务（交接重点）

### 任务 A：美化 App（✅ 已完成）
- 已迁移到标准 Gradle 工程（AGP 8.5.2 / Gradle 8.7 / JDK 17）。
- 已加入 adaptive icon、Material 3 动态取色、深色模式、圆角参数组和实时状态卡。
- 滑块实时显示数值并在松手时预览；恢复原厂已加入二次确认。
- Root 操作移至后台线程；13 个配置字段会完整读取和保留。
- 仍需在目标真机完成视觉、Root 授权、来电切档与震感验收。

### 任务 B：开源自建 GitHub 仓库并发布
- `README.md` / `LICENSE` / `.gitignore` **已写好**，直接用。
- ✅ **模块 zip 已可一键产出**：
  ```bash
  bash tools/build-module-zip.sh
  # → dist/haptic-boost-v1.2.1.zip（内嵌 Material 3 App）
  #   内嵌 App + customize.sh（刷模块时自动 pm install 装 App）
  ```
  zip 内部结构已验证：`module.prop` 在根、`.sh` 为 0755、`module.prop` 为 0644。
  ⚠️ 打包脚本用 python 写 zip，**目的就是绕开"手机存储丢可执行位"的问题**，别改成 `zip` 命令。
- ✅ 已替换：
  - `LICENSE` 与 `module.prop` 里的**作者名** → **法秋**（2026）
- 需要用户确认后替换：
  - 仓库名：`HapticBoost`
- 建议流程：
  ```bash
  git init
  git add .
  git commit -m "feat: initial open-source release"
  gh auth login          # 或 git remote add origin + PAT
  git push -u origin main
  # 打 tag 并发 Release，上传 Magisk 模块 zip 与新 APK
  ```
- ⚠️ **用你自己的 release keystore 重新签名 APK**（现有 APK 是调试密钥签的）。
- ⚠️ `.gitignore` 已排除密钥/token，**推送前再 `git status` 检查一遍**。

> ⚠️ **可执行位丢失问题（很重要）**
> 这些文件是从 `/sdcard` 拷出来的，而 **`/sdcard` 是 fuse 模拟存储，不支持 Unix 权限位**，
> 所以 `module/*.sh` 的执行权限**没有被保留**。
> 到电脑上之后请先执行：
> ```bash
> chmod +x module/*.sh
> git update-index --chmod=+x module/*.sh    # 让 git 记录可执行位
> ```
> 否则别人克隆后模块可能因为「脚本不可执行」而无法启动。

### 任务 C（可选，硬核）：真正控制 RichTap 通路
见 `docs/ROADMAP.md`。这是本项目「未完成的那一半」，需要 PC + Ghidra。

---

## 7. 安全红线（务必遵守）

- 🔴 **只操作**：`/sys/class/qcom-haptics/*`、`/data/local/tmp/`、`/data/adb/modules/haptic_boost_qcom/`
- 🔴 **绝不**写入系统分区、绝不 `rm -rf` 系统目录
- 🔴 若用 `adb shell su -c` 让 AI 自动执行命令，**请限制在上述范围**
- 🔴 不要把 `/data/user/0/com.ai.assistance.operit/files/pkcs12.keystore` 之类的密钥提交进仓库
- ✅ 回滚手段：`restore.sh` / 删除模块目录 / 开机进安全模式

---

## 8. 快速自检清单（接手后 5 分钟）

- [ ] `su -c 'cat /sys/class/qcom-haptics/vmax'` 有输出 → 驱动在
- [ ] `pgrep -f haptic_boost_qcom` 有 watch/ring_listener/keepalive → 模块在跑
- [ ] `date +%s > /data/local/tmp/vib_call_hint && sleep 5 && dmesg | grep -c haptic` > 0 → 闭环通
- [ ] 打开 App，能读到配置、能写入（需 Root 授权）
