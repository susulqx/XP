# XposedBypass

Xposed 模块，Hook `app.unique.one` 的订阅检测，让付费功能直接可用。

> 仅供学习与个人测试使用，请勿用于商业用途。

## 项目结构

```
xposed_module/
├── app/
│   ├── build.gradle                       # 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml            # Xposed 元数据声明
│       ├── assets/xposed_init             # 入口类声明
│       ├── java/com/bypass/BypassHook.java # Hook 实现
│       └── res/values/arrays.xml          # Hook 作用范围
├── settings.gradle                        # AGP 8.7.0
├── gradle/wrapper/                        # Gradle 8.7 wrapper
├── gradlew / gradlew.bat                  # Wrapper 启动脚本
└── .github/workflows/build.yml            # GitHub Actions 自动构建
```

## 编译方式

### 方式 A：GitHub Actions（推荐，无需本地环境）

1. 把仓库 push 到 GitHub
2. 进入仓库的 **Actions** 页面
3. 等待 `Build Xposed Module APK` workflow 跑完
4. 在 workflow 详情页底部下载 `XposedBypass-<commit-sha>` artifact
5. 把 APK 推到手机，安装后在 LSPosed / EdXposed 里启用即可

### 方式 B：本地命令行

需要 JDK 17、Android SDK、Gradle 8.7（或直接用 wrapper）：

```bash
# 1. 设置环境变量
export ANDROID_HOME=/path/to/android-sdk     # Windows: set ANDROID_HOME=...
export JAVA_HOME=/path/to/jdk-17

# 2. 接受 SDK licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

# 3. 安装必要组件
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0"

# 4. 编译
./gradlew assembleRelease     # Linux/Mac
gradlew.bat assembleRelease   # Windows
```

产物路径：`app/build/outputs/apk/release/app-release-unsigned.apk`

## 安装与启用

1. 在手机上安装 **LSPosed**（推荐）或 LSPosed 兼容框架
2. 安装本 APK
3. 在 LSPosed 管理器中启用本模块，作用域勾选 `app.unique.one`
4. 强制停止目标 App，重新打开

## 编译参数

- `compileSdk`: 34
- `minSdk`: 29（Android 10+）
- `targetSdk`: 34
- AGP: 8.7.0
- Gradle: 8.7
- JDK: 17

## License

仅供个人学习使用。
