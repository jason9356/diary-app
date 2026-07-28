# Android 客户端（日记）

与桌面端（`src/` Python / PySide6）**同一产品的另一端**，代码隔离在 `android/`，互不混用。协议 **v2**：一天可多条笔记。

## 设计

- 视觉语言与桌面一致：冷静纸色 + 苔绿点缀 + 霞鹜文楷
- **导航**：时间线（按日分组）→ 当日笔记列表 → 单条所见即所得编辑
- **自动定位 + 天气**写入**日上下文**（`*.day.json`，`context_source=phone`）：当日尚无上下文时采集一次
- OPPO / ColorOS 国行走系统 `LocationManager` 回退（不依赖 GMS）
- 相册插图写入当前笔记 `assets/<id>/`，正文内联 Markdown 图片

## 数据目录（应用私有存储）

```
files/diary_data/
├── diary/YYYY/MM/<uuid>.md
├── diary/YYYY/MM/YYYY-MM-DD.day.json
└── assets/<uuid>/<image>
```

启动时会把 v1 的 `YYYY-MM-DD.md` / `assets/YYYY-MM-DD/` 迁移为上述结构。

## 环境

- JDK 17（已验证：`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`）
- Android SDK（`%LOCALAPPDATA%\Android\Sdk`，platform 34）
- 推荐 Android Studio 打开 **`android/`** 目录（不要打开仓库根当 Android 工程）
- 开发期优先用模拟器联调；定型后再装真机

## 构建

在 `android/` 下：

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK：`android/app/build/outputs/apk/debug/app-debug.apk`

## 同步

同步设置中填写 `https://diary.xybkwd.top` 与 Token（与桌面 `data/sync_secrets.json` 相同）。服务端须为 protocol 2。
