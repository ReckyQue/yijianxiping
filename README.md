# 一键息屏

桌面点击图标即可息屏，无需进入应用界面。免 Root，依赖 [Shizuku](https://github.com/RikkaApps/Shizuku) 以 shell/adb 身份调用 `IPowerManager.goToSleep`。

| 项 | 值 |
|----|-----|
| 包名 | `com.recky.yijianxiping` |
| 版本 | 1.1.0（versionCode 2） |
| minSdk / targetSdk | 26 / 36 |

---

## 使用方法

1. 安装并启动 **Shizuku**（电脑 USB/无线调试，或 Root）
2. 安装本应用，首次打开时在弹窗中 **授权本应用**
3. 之后在桌面点「一键息屏」图标 → 立即息屏

**注意：** 手机整机重启后需重新启动 Shizuku，授权一般会保留。

### 用电脑启动 Shizuku（示例）

设备已通过 adb 连接时：

```bat
adb shell /data/local/tmp/shizuku_starter
```

（若没有该文件，先在手机上打开 Shizuku，按应用内提示用电脑配对/启动。）

---

## 原理

```
点击桌面图标
  → 透明 MainActivity
  → 检查 Shizuku 是否运行、是否已授权
  → 经 ShizukuBinderWrapper 调用 IPowerManager.goToSleep
  → finish（不进入可见界面）
```

- 未启动 Shizuku / 未授权时：显示一次性引导页（打开 Shizuku、请求授权）
- `PowerActions` 中另有 `reboot()` / `shutdown()`，当前桌面入口仅使用息屏

---

## 构建

| 组件 | 版本 |
|------|------|
| JDK | 17 |
| AGP / Kotlin | 8.9.1 / 2.0.21 |
| Gradle Wrapper | 8.11.1 |
| compileSdk / build-tools | 36 / 36.1.0 |

```bat
set JAVA_HOME=<JDK17安装目录>
gradlew.bat assembleDebug
gradlew.bat installDebug
```

Debug APK：

```
app\build\outputs\apk\debug\app-debug.apk
```

`local.properties`（勿提交，由 Android Studio 自动生成）：

```properties
sdk.dir=<本机 Android SDK 路径>
```

---

## 主要依赖

- `dev.rikka.shizuku:api` / `provider` **13.1.5**
- `org.lsposed.hiddenapibypass:hiddenapibypass` **4.3**（反射调用隐藏 `IPowerManager`）

---

## 工程结构

```
app/src/main/java/com/recky/yijianxiping/
  App.kt              # Hidden API 豁免
  MainActivity.kt     # 透明入口：授权检查 + 息屏
  PowerActions.kt     # IPowerManager：goToSleep / reboot / shutdown
```

---

## 常见问题

| 现象 | 处理 |
|------|------|
| 点击无反应 / 提示启动 Shizuku | 重新启动 Shizuku 服务 |
| 提示授权 | 点「授权并息屏」，在 Shizuku 弹窗中允许 |
| 桌面图标未更新 | 刷新 Launcher，或移除图标后从应用列表重新添加 |
