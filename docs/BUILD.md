# 构建说明 · BUILD

App 已迁移为标准 Android Gradle 工程。旧的 `javac + d8 + 手写 AXML` 工具链仅保存在
`app/legacy/` 供考古，不再用于发布产物。

---

## 1. 工具链

| 组件 | 版本 |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| Gradle | 8.7（仓库包含 Wrapper） |
| JDK | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 21 |

首次构建前应配置 `local.properties`：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

`local.properties`、密钥和本地工具链均已在 `.gitignore` 中排除。

---

## 2. App 构建

### Windows

```powershell
.\gradlew.bat :app:assembleDebug
```

### Linux / macOS

```bash
./gradlew :app:assembleDebug
```

输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

用于模块联调时，将它复制为：

```text
dist/HapticBoost-1.2.1-release.apk
```

发布版签名见本文 §5；不要发布调试密钥签名的 APK。

---

## 3. 标准工程结构

```text
HapticBoost/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    ├── legacy/                         # 已停用的手搓构建链
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/io/github/sakurainakeipoi/hapticboost/MainActivity.java
        └── res/
            ├── layout/activity_main.xml
            ├── values/                 # Material 3 主题、颜色、文案
            ├── values-night/           # 深色模式回退色
            ├── drawable/               # 自绘矢量图标
            └── mipmap-anydpi-v26/      # adaptive icon
```

---

## 4. ⚠️ App 与模块通信红线

迁移或改 UI 时必须同时满足以下四条：

```java
// 1) 配置路径必须指向当前模块 ID
String MOD = "/data/adb/modules/haptic_boost_qcom";
String CFG = MOD + "/config.sh";

// 2) 所有 Root 命令必须经 su -c
new ProcessBuilder("su", "-c", cmd).start();

// 3) config.sh 的 13 个字段名不能改变
// NORMAL_VMAX / NORMAL_FIFO_VMAX / NORMAL_CL_VMAX
// CALL_VMAX   / CALL_FIFO_VMAX   / CALL_CL_VMAX
// CALL_BOOST / POLL / HOLD / HINT_WINDOW
// RING_PLAY / RING_DUR / RING_GAP

// 4) 保存后必须重新应用日常档
// sh $MOD/apply.sh normal
```

当前 App 会读取并原样保留未显示在 UI 中的 `NORMAL_CL_VMAX`、`CALL_CL_VMAX`、
`POLL`、`HOLD`、`HINT_WINDOW`，避免保存时把高级配置意外冲掉。

---

## 5. Release 签名

Release keystore 和密码不得进入仓库。推荐从环境变量或忽略的
`keystore.properties` 读取：

> 首次生成后，请把 keystore 与密码分别备份到安全位置。丢失 keystore
> 或密码后，将无法用同一签名升级已经安装的 App。

```text
KEYSTORE_PATH
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

工程会优先从环境变量读取上述四项；环境变量未设置时，读取仓库根目录中
被 Git 忽略的 `keystore.properties`。四项齐全时，`release` 变体自动使用
正式签名；缺少任意一项时仍可构建 unsigned release：

```powershell
.\gradlew.bat :app:assembleRelease
```

---

## 6. Magisk 模块打包

先确保 `dist/` 中存在当前版本 APK，然后运行：

```bash
bash tools/build-module-zip.sh
# → dist/haptic-boost-v1.2.1.zip
```

结构要求：

```text
haptic-boost-v1.2.1.zip
├── module.prop
├── HapticBoost.apk
├── service.sh
├── customize.sh
└── 其他模块脚本
```

打包脚本会加入 `META-INF/com/google/android/` 兼容入口。该入口用于仍沿用
Magisk 27 安装流程的 Kitsune 等管理器；KernelSU 与新版 Magisk 会安全忽略
或兼容处理它。不要从发布包中删除该目录。

打包脚本通过 Python `zipfile` 明确写入权限位：所有 `.sh` 为 `0755`，其余文件为
`0644`。不要换回普通 `zip` 命令，否则从手机存储转移源码时可能再次丢失执行位。

---

## 7. 验证

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

```bash
for f in module/*.sh; do sh -n "$f" || exit 1; done
bash tools/build-module-zip.sh
```

发布前还应执行真机验证：读取配置、保存配置、滑块预览、来电切档、挂断恢复和重启自启。

---

## 附：旧轻量构建链

迁移前的 `app/build.sh` 与 `app/mkmanifest.py` 已移入 `app/legacy/`。它们不支持 Android
资源系统，因此无法构建 adaptive icon、Material 3 主题或 XML 布局，仅保留作历史参考。
