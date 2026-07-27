# Android 客户端（日记）

与桌面端（`src/` Python / PySide6）**同一产品的另一端**，代码隔离在 `android/`，互不混用。

## 设计

- 视觉语言与桌面一致：冷静纸色 + 苔绿点缀 + 霞鹜文楷
- 打开即写今天；时间线浏览
- **自动定位 + 天气**（`context_source=phone`）：仅在**新建当天日记且尚无地点/天气**时采集一次，之后不再刷新
- OPPO / ColorOS 国行走系统 `LocationManager` 回退（不依赖 GMS）
- 相册插图，本地目录与桌面协议对齐

## 数据目录（应用私有存储）

```
files/diary_data/
├── diary/YYYY/MM/YYYY-MM-DD.md
└── assets/YYYY-MM-DD/<image>
```

Markdown front matter 字段与桌面相同：`location` / `weather` / `temp_c` / `context_source` / `context_updated_at`。  
权威优先级：`phone` > `desktop` > `manual`。

## 环境

- JDK 17（已验证：`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`）
- Android SDK（`%LOCALAPPDATA%\Android\Sdk`，platform 34）
- 推荐 Android Studio 打开 **`android/`** 目录（不要打开仓库根当 Android 工程）

## 构建 / 安装

在 `android/` 下：

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
gradlew.bat installDebug
```

或用 Android Studio：Open → 选择 `diary-app/android` → Run。

真机（Find X6 Pro）需开启开发者选项与 USB 调试；首次使用会请求定位权限。

## 字体

霞鹜文楷位于：

`app/src/main/res/font/lxgw_wenkai.ttf`

若缺失，可从 [LxgwWenKai releases](https://github.com/lxgw/LxgwWenKai/releases) 下载 `LXGWWenKai-Regular.ttf` 并重命名放入上述路径。

## 与桌面同步

本版只做本地日记 + 手机天气权威字段预留。跨设备同步服务尚未落地；协议已按共用目录 / front matter 设计，日后对接即可。
