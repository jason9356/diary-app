# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Planned

- 视觉美化（纸感主题、字体层级、侧栏减负）
- 可选：标签、统计、加密、同步接口落地

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
