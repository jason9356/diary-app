# 灵感匣（Sparkbox）

本地优先的**灵感收集器**：随手记下想法与片段，按日期 / 标签检索；本机待办 + Obsidian 日记待办（经对象存储）可在同一 App 里完成。

> 本项目已从「个人日记」转向灵感匣。日记请继续用你现有的工具；这里不再做日记替代品。

当前主客户端：**Android**（[`android/`](./android/)）。  
桌面 Python/PySide6 代码仍在 [`src/`](./src/)，仅作调试 / 备份参考，**不再是产品主路径**。

技术栈：Android Kotlin · Jetpack Compose · 本地 Markdown · FastAPI 同步服务 · 可选 S3 兼容对象存储。

版本与变更见 [CHANGELOG.md](./CHANGELOG.md) · 许可 [MIT](./LICENSE)

## 功能概览

- **灵感卡片**：扁平列表、搜索、日期 / 标签筛选、Markdown 编辑、图片附件
- **同步**：自建服务端副本（协议见 [docs/sync-protocol.md](./docs/sync-protocol.md)，protocol 3）
- **待办**：应用内待办；从 Obsidian 日记抽取并回写完成态（设置里配置 S3）
- **AI**：设置中可开关接口预留（日报钩子等），暂不接模型

## Android 快速开始

```powershell
cd android
.\gradlew.bat installDebug
```

模拟器建议使用 **Google APIs** 镜像（勿用 ATD，易黑屏）。

## 同步服务

见 [`server/README.md`](./server/README.md)。健康检查返回 `"protocol": 3`、`"product": "sparkbox"`。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/sync-protocol.md](./docs/sync-protocol.md) | 卡片同步协议 v3 |
| [docs/android-client.md](./docs/android-client.md) | Android 说明 |
| [docs/RELEASE.md](./docs/RELEASE.md) | 发布备注 |

## 桌面端（降级）

```powershell
.\setup.bat
.\run.bat
```

桌面端仍可读写同一套 Markdown 布局，但产品文案与迭代以 Android 灵感匣为准。
