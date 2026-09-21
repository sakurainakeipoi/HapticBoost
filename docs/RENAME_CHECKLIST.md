# 改名 & 美化 待办清单（给 Codex）

> **执行顺序建议**：先改名（①→⑥）→ 再美化 UI → 最后重新构建 + 打包 + 验证。
> 每完成一项就 `git commit` 一次，方便回滚。

---

## ⚠️ 先理解：仓库里有 **3 个不同的"名字"**，风险完全不同

| # | 名字 | 当前值 | 影响范围 | 风险 |
|---|---|---|---|---|
| ① | **App 显示名**（桌面图标文字） | `震动调节` | 只影响显示 | 🟢 安全 |
| ② | **App 包名** | `com.operit.vibtuner` | 等于换了一个 App | 🟡 中 |
| ③ | **模块 ID** | `vibrate_boost` | **脚本里到处硬编码** | 🔴 **高危** |
| ④ | 模块显示名 | `Vibration Boost (qcom-hv-haptics)` | 面具里显示 | 🟢 安全 |
| ⑤ | 作者署名 | `法秋` ✅ 已改 | 面具里显示 | 🟢 安全 |
| ⑥ | APK 文件名 | `VibrationTuner-*.apk` | 3 个脚本引用 | 🟢 安全（但别漏） |

**❗️最容易出事的是 ③**：模块 ID 会变成路径 `/data/adb/modules/vibrate_boost`，
而它在 **10+ 个地方被硬编码**。改一个漏一个 → 模块直接哑火。

---

## 🏷️ 命名建议（重要 · 决定开源后的第一印象）

### 核心原则
**名字要表达「兼容边界」，而不是「作者的爱机」。**

```
❌ 错误示范：  RealmeVibrationBoost     ← 用户会以为只有 realme 能用
❌ 错误示范：  RMX3888 Haptic Enhancer  ← 把兼容范围缩到了一个型号
✅ 正确示范：  HapticBoost (qcom-hv-haptics)
✅ 正确示范：  震动增强 · 高通 HV Haptics
```

理由（见 `README.md` 兼容性章节）：
本模块绑定的技术边界是 **`qcom-hv-haptics` 驱动**，不是某个型号。
写机型名 = 主动劝退 90% 本来能用的用户。

### 建议的命名方案

| 位置 | 建议值 | 理由 |
|---|---|---|
| App 桌面名 | `震动增强`（短） | 4 字以内，桌面好排 |
| App 内标题 | `震动增强 · 高通 HV Haptics` | 副标题点明适用范围 |
| 模块 ID | `haptic_boost_qcom` | 表意清楚，比 `vibrate_boost` 更准确 |
| 模块显示名 | `HapticBoost (qcom-hv-haptics)` | 技术用户一眼判断能否用 |
| 包名 | `io.github.<用户名>.hapticboost` | 反向域名，开源惯例 |
| 仓库名 | `HapticBoost` 或 `qcom-haptics-tuner` | 便于搜索 |
| APK 文件名 | `HapticBoost-<版本>.apk` | 与模块名统一 |

### 兼容性必须在 3 处明确写出
1. **`module.prop` 的 `name=`** → 带驱动名
2. **`module.prop` 的 `description=`** → 一句话说清「支持什么 / 实测什么 / 不支持什么」
   > 已改好，可参考当前写法
3. **`README.md` 的兼容性表格** → 三档（🟢 核心 / 🟡 铃声 / 🔴 不支持）
   > 已改好

> 💡 **description 的推荐模板**：
> `高通 qcom-hv-haptics 线性马达增强：<功能>。适用所有该驱动机型（PM8550 系列），已在 <实测机型> 验证。<安全说明>。`

---

## ① App 显示名（🟢 安全）

| 文件 | 行 | 当前内容 | 说明 |
|---|---|---|---|
| `app/mkmanifest.py` | 52 | `("android", "label", "str", "震动调节")` | **桌面图标文字（application 级）** |
| `app/mkmanifest.py` | 57 | `("android", "label", "str", "震动调节")` | Activity 级 label，改完和上面一致 |
| `app/src/com/operit/vibtuner/MainActivity.java` | 88 | `title.setText("震动强度调节器")` | 界面内的大标题（可独立命名） |

> 💡 建议：桌面名短（2-4 字），标题可以长一点。

---

## ② App 包名（🟡 中 · 改完等于换了一个 App）

⚠️ **改包名后**：新 App 与旧 App **数据不共享、签名不共享**，
用户需要**先卸载旧版**再装新版（否则会共存两个图标）。
`config.sh` 在 `/data/adb/modules/...` 下，**不受影响**，所以调参不会丢。

需要同步修改（**全部**）：

| 文件 | 位置 | 当前值 |
|---|---|---|
| `app/src/com/operit/vibtuner/MainActivity.java` | 第 1 行 | `package com.operit.vibtuner;` |
| `app/mkmanifest.py` | 40 | `(None, "package", "str", "com.operit.vibtuner")` |
| `app/mkmanifest.py` | 56 | `("android","name","str","com.operit.vibtuner.MainActivity")` |
| `app/build.sh` | 13 | `$W/out/com/operit/vibtuner/MainActivity.class` |
| `app/build.sh` | 20 | `$W/out/com/operit/vibtuner/*.class` |
| **目录名** | — | `app/src/com/operit/vibtuner/` → 改成新包名的目录结构 |
| `app/build.sh` | 顶部 | 编译时的源码路径也要跟着改 |

> 💡 包名规范：全小写、反向域名，例如 `io.github.你的用户名.hapticboost`。

---

## ③ 模块 ID（🔴 高危 · 必须全量替换）

当前 ID：`vibrate_boost` → 路径 `/data/adb/modules/vibrate_boost`

### 必须同步的文件（**一个都不能漏**）

| 文件 | 行 | 内容 |
|---|---|---|
| `module/module.prop` | 1 | `id=vibrate_boost` |
| `module/start.sh` | 2 | `M=/data/adb/modules/vibrate_boost` |
| `module/keepalive.sh` | 2 | `M=/data/adb/modules/vibrate_boost` |
| `module/run_watch.sh` | 4 | `sh /data/adb/modules/vibrate_boost/watch.sh` |
| `module/customize.sh` | 63 | `ui_print "  配置：/data/adb/modules/vibrate_boost/config.sh"` |
| `app/src/.../MainActivity.java` | 26 | `MOD = "/data/adb/modules/vibrate_boost"` |
| `README.md` | 55,56,89,90 | 安装/使用命令 |
| `HANDOFF.md` | 15,161,164,235,246 | 文档 |
| `docs/BUILD.md` | 130 | 代码示例 |

### 检查方法（改完后跑）
```bash
grep -rn "vibrate_boost" . | grep -v "^./docs/RENAME_CHECKLIST.md"
# 期望：只剩「运行时日志文件名」，不应再有「模块路径」
```

### ⚠️ 运行时文件名**可以不改**（但改了要同步）
这些放在 `/data/local/tmp/`，和模块 ID 无关，**建议保持原样**减少改动面：
```
vibrate_boost.log          ① service.sh:6 ② watch.sh:7 ③ README:74
vibrate_boost_heartbeat    ① service.sh:7 ② watch.sh:8 ③ ring_play.sh:5
                           ④ keepalive.sh:3 ⑤ HANDOFF 多处
vibrate_boost.pid          watch.sh:11
vib_call_hint / vib_ring_active / vib_ring_play.pid   （本来就是简写，可不动）
```
> 💡 如果一定要改，**5 个文件里的同名常量必须一次性全改**。

### 🔴 改完 ID 的后果（提醒用户）
- 手机上旧模块目录 `/data/adb/modules/vibrate_boost/` **不会自动消失**，
  会和新模块**同时存在并同时运行**（两个 watcher 打架）！
- **正确做法**：先删旧目录 → 再重启。

---

## ④ 模块显示名（🟢 安全）
| 文件 | 行 | 当前值 |
|---|---|---|
| `module/module.prop` | 2 | `name=Vibration Boost (qcom-hv-haptics)` |
| `module/customize.sh` | 8 | `ui_print "   Vibration Boost · 震动增强"` |
| `module/watch.sh` | 2 | 注释 `# Vibration Boost scene watcher v6` |
| `module/ring_listener.sh` | 2 | 注释 |
| `module/config.sh` | 2 | 注释 |
| `README.md` | 1 | 标题 |
| `HANDOFF.md` | 14 | 源码路径说明 |

---

## ⑤ 作者署名（🟢 安全）—— ✅ 已完成
| 文件 | 行 | 当前值 |
|---|---|---|
| `module/module.prop` | 5 | `author=法秋` ✅ |
| `LICENSE` | 3 | `Copyright (c) 2026 法秋` ✅ |
| `module/module.prop` | 6 | `description=...`（可顺便重写） |

> ✅ **2026-09 已由手机侧完成**：`author=法秋`、`LICENSE` 著作权人同步改为 `法秋`。
> 其余名字（App 名 / 包名 / 模块 ID）**保持不变**。

> 💡 建议 `description` 写得像"商品页"：一句话说清功能 + 支持机型 + 风险提示。

---

## ⑥ APK 文件名（🟢 安全，但有 3 处联动）
| 文件 | 行 | 当前值 |
|---|---|---|
| `dist/VibrationTuner-1.1-debug.apk` | — | 文件本身（重命名） |
| `app/build.sh` | 27,31,35 | 中间产物 `VibrationTuner-unsigned.apk` |
| `tools/build-module-zip.sh` | 16 | `ls dist/VibrationTuner-*.apk` |
| `tools/build-module-zip.sh` | 25 | `cp "$APK" "$TMP/VibrationTuner.apk"` |
| `module/customize.sh` | 32,37,38,39,46 | `$MODPATH/VibrationTuner.apk` 等 5 处 |
| `README.md` | 59,60,133 | 安装命令 |

> ⚠️ `customize.sh` 里的 `VibrationTuner.apk` **必须**和打包脚本塞进 zip 的名字一致，
> 否则"刷模块自动装 App"会静默失效。

---

## ⑦ 仓库名（🟢 安全）
- `README.md:106`、`HANDOFF.md:205` 里出现的 `VibrationBoost` 目录名
- GitHub 上建的仓库名（建议和 App 名统一）

---

## 🎨 美化待办（改完名再做）

建议直接迁移到 **标准 Gradle 工程**（见 `BUILD.md` 方式 ②），然后：

- [ ] **图标**：adaptive icon（前景 + 背景），建议用「震动波纹 / 波形」意象
- [ ] **主题**：Material 3 + 动态取色（Android 12+）+ 深色模式跟随系统
- [ ] **布局分组**：用圆角卡片分成 3 组
      `① 日常强度` `② 来电强度` `③ 来电强震铃音` + `危险操作` 单独区
- [ ] **滑块**：拖动时显示实时数值；松手时**轻震动预览**（真机手感反馈）
- [ ] **状态卡**：把「当前生效」做成醒目卡片（现在是 `status` 一行小字）
- [ ] **危险按钮**：`恢复原厂默认` 加二次确认弹窗
- [ ] **无障碍**：`contentDescription` / 触控目标 ≥ 48dp
- [ ] **关于页**：版本号 + 项目 GitHub 链接 + 开源许可

### ⚠️ 美化时的红线（改错了模块就失联）
见 `BUILD.md` §2.3。核心是 4 条：
1. `MOD = /data/adb/modules/<模块ID>`
2. 必须用 `su -c` 执行
3. `config.sh` 的 **11 个字段名**不能变
4. 保存后要 `sh $MOD/apply.sh normal`

### 🐛 顺手可修的体验问题（可选）
`MainActivity.java:319` 保存时会**整个重写 `config.sh`**，
把 `module/config.sh` 里的**注释和排版全冲掉**。
建议：写之前先备份成 `config.sh.bak`，或把注释也一起写出去。

---

## ✅ 改完后的验证清单

```bash
# 1) 无残留（除了运行时日志名）
grep -rn "vibrate_boost" . | grep -v RENAME_CHECKLIST   # 应只剩 /data/local/tmp/ 的日志名

# 2) 脚本语法
for f in module/*.sh; do sh -n "$f" || echo "语法错: $f"; done

# 3) 重新打包
bash tools/build-module-zip.sh

# 4) 检查 zip 结构（module.prop 在根、.sh 为 0755）
python3 -c "import zipfile;z=zipfile.ZipFile('dist/vibration-boost-v1.2.zip');print(z.namelist())"
```
**真机验证**（重要）：
- [ ] 刷 zip → 桌面出现 App（新名字 / 新图标）
- [ ] App 能读到配置、保存后 `config.sh` 真的变了
- [ ] 打电话 → 震动增强 → 挂断恢复
- [ ] 重启后仍然工作