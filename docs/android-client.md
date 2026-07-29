# Android 客户端（灵感匣）

主产品端。代码在 `android/`，与桌面 `src/` 隔离。本地 Vault；可选 WebDAV 云盘镜像。

## 设计

- 底部：**灵感** / **事项** / **设置**
- 灵感：卡片流、搜索、标签筛选
- 事项：本机增强待办（类型 / 到期 / 优先级等）
- 设置：数据存放（本机 / 云盘）、外观、AI 预留

## 数据目录（应用私有存储）

```
files/diary_data/
├── diary/YYYY/MM/<uuid>.md    # 灵感卡片
├── todos/todos.json           # 本机待办
└── assets/<uuid>/<image>
```

## 环境

- JDK 17
- Android SDK（platform 34）
- 模拟器请用 **Google APIs** 镜像（不要用 AOSP ATD）

## 构建

```bat
cd android
gradlew.bat installDebug
```
