# Android 客户端（灵感匣）

主产品端。代码在 `android/`，与桌面 `src/` 隔离。协议 **v3**（兼容 v2 health）：灵感卡片 + 本机待办同步；Obsidian 待办经 S3。

## 设计

- 底部：**灵感** / **待办** / **设置**
- 灵感：扁平卡片流、总数、搜索、日期与标签筛选
- 待办：本机待办 + Obsidian 日记待办（对象存储）
- 设置：同步、S3 / Obsidian 规则、字号、AI 预留开关

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
