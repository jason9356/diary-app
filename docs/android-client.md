# Android 客户端（灵感匣 / Sparkbox）

主产品端。代码在 `android/`，包名 `com.sparkbox.android`。

## 设计

- 底部：**灵感** / **事项** / **设置**
- 灵感：卡片流、搜索、标签筛选
- 事项：本机增强待办（类型 / 到期 / 优先级等）
- 设置：数据存放（本机 / 云盘）、外观、AI 预留

## 数据目录（应用私有存储）

```
files/vault/                 # 自 diary_data 自动迁移（若存在）
├── diary/YYYY/MM/<uuid>.md  # 灵感卡片（布局名保留）
├── diary/YYYY/MM/<date>.day.json
├── todos/todos.json
├── assets/<uuid>/<image>
└── manifest.json            # 可选
```

Vault 内 `diary/` 路径是协议布局，**不要为改名而改掉**，以免 WebDAV 备份对不上。

## 环境

- JDK 17
- Android SDK（platform 34）
- 模拟器请用 **Google APIs** 镜像

## 构建

```bat
cd android
gradlew.bat installDebug
gradlew.bat assembleRelease
```

第一版 APK：

- `app/build/outputs/apk/release/Sparkbox-1.0-release.apk`
- `dist/Sparkbox-1.0.apk`
- `dist/灵感匣-第一版.apk`
