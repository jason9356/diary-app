# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.0.0] — 2026-07-30 — 灵感匣第一版

### Added

- Android 应用 ID `com.sparkbox.android`；版本名 `1.0.0`；APK 文件名「灵感匣第一版」
- Vault 本地目录 `files/vault`（自 `diary_data` 自动迁移）；布局内仍用 `diary/` 路径
- 数据存放：仅本机 / 云盘（WebDAV）；事项 FAB 新建与增强字段
- 编辑页：居中标题卡片、分类选择；阅读端 Markdown 标题 / 有序·无序列表

### Changed

- 工程与代码标识统一为 Sparkbox（去掉 personaldiary / Diary* 主路径命名）
- 文档以灵感匣为主产品；同步以本机 Vault + WebDAV 为准
- 深色模式编辑光标跟随主题色

### Removed

- Android 自建 SyncClient、Obsidian/S3 待办桥
- 自建 FastAPI 同步服务端（`server/`）与 sync-protocol 文档

## [Unreleased]

### Added (earlier product pivot)

- **产品转向「灵感匣」**：灵感卡片、本机事项、云盘同步预留
- Android：底部灵感 / 事项 / 设置；青笺主题与全面屏状态栏

### Added (earlier)

- **同步协议 v2**：一天多条笔记，以 UUID `id` 为主键；日天气独立为 `.day.json`；API `/entries/{id}`、`/days/{date}`
- **同步服务端**（`server/` FastAPI）：Bearer 鉴权、笔记/日上下文/assets、增量 `changes`、Docker Compose；已支持 v1→v2 迁移
- 桌面：当日多笔记下拉 +「新建笔记」；按 id 同步
- Android：时间线 → 日列表 → 所见即所得编辑（richeditor）；内联插图与键盘跟滚
- 工具栏「图片」按钮（`Ctrl+Shift+I`）与当日图条缩略图
- 预览图片版式：单图通栏、连续多图双列
- 日记地点 / 天气 / 温度（标题下展示；YAML + SQLite；手机来源优先生效）
- 电脑端 Open-Meteo 自动取天气（可配置 `weather_city`，可选手填）
- **Android 客户端**（`android/`）：同设计语言、本地 Markdown 协议、自动定位天气、插图；与桌面代码隔离

### Changed (earlier)

- 同步从「一天一篇 / 按日期路由」升级为「一天多条 / 按 id 路由」（protocol 2）
- 视觉重设：冷静纸色 + 苔绿点缀（取代奶油底/电蓝）
- 侧栏收窄（约 256px），顶部品牌字与下划线导航
- 日历改为自绘日期格（柔选中、今日描边、小圆点），固定高度避免撑满
- 全局字体改为统一无衬线（微软雅黑 UI / 系统 Sans），不再使用霞鹜文楷
- 日历周末取消刺眼红色，与平日同色、表头略淡
- 标题/品牌/选中导航等用合成加粗拉开层级（文楷仅 Regular）

### Planned

- 可选：标签、统计、加密；同步 E2E 加密与自动后台推送

## [0.2.0] - 2026-07-27

### Added

- Markdown **实时渲染预览**（编辑 / 分栏 / 预览三模式，`Ctrl+1/2/3`）
- 预览支持标题、加粗、列表、引用、代码块、表格、本地图片路径解析
- 编辑模式偏好持久化到 `data/config.json`

## [0.1.0] - 2026-07-27

### Added

- Windows 桌面日记应用初版（Python + PySide6）
- 打开即写今天的日记；Markdown 源码编辑与工具栏
- 拖入图片保存到 `data/assets/日期/`
- 自动保存；记录创建时间、修改时间、写作时长
- 日历圆点、时间线、年/月筛选、全文搜索高亮
- SQLite 索引 + 本地 Markdown 双存储（`YYYY/MM/YYYY-MM-DD.md`）
- 导出 ZIP（Markdown + 图片）
- 深色 / 浅色 / 跟随系统；系统字体 / 等宽字体
- `run.bat` 开发启动；README 含打包说明
