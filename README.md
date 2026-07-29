# 灵感匣（Sparkbox）

本地优先的**灵感收集器**：记下想法与片段，按日期 / 标签检索；事项可同步到 WebDAV 云盘。

> 产品定位是灵感匣，不是日记替代品。日记请继续用你现有的工具。

当前主客户端：**Android**（[`android/`](./android/)）。  
桌面 Python/PySide6（[`src/`](./src/)）为遗留参考，**不是产品主路径**；同步以本机 Vault + 可选 WebDAV 为准。

技术栈：Android Kotlin · Jetpack Compose · 本地 Markdown Vault · 可选 WebDAV。

版本与变更见 [CHANGELOG.md](./CHANGELOG.md) · 许可 [MIT](./LICENSE)

## 功能概览

- **灵感卡片**：列表、搜索、标签筛选、Markdown 编辑、图片附件
- **事项**：类型 / 到期 / 优先级等增强字段；进入 Vault 一并备份
- **数据存放**：仅本机，或云盘镜像（WebDAV 可用；其它厂商配置预留）
- **AI**：设置中可开关接口预留，暂不接模型

## Android 快速开始

```powershell
cd android
.\gradlew.bat installDebug
# 或产出第一版 APK：
.\gradlew.bat assembleRelease
# 产物：
#   android/app/build/outputs/apk/release/Sparkbox-1.0-release.apk
#   android/dist/Sparkbox-1.0.apk
#   android/dist/灵感匣-第一版.apk
```

模拟器建议使用 **Google APIs** 镜像（勿用 ATD）。

应用 ID：`com.sparkbox.android` · 版本：`1.0.0`（灵感匣第一版）

## 文档

| 文档 | 说明 |
|------|------|
| [docs/vault-schema.md](./docs/vault-schema.md) | Vault 目录约定 |
| [docs/android-client.md](./docs/android-client.md) | Android 说明 |
| [docs/RELEASE.md](./docs/RELEASE.md) | 发布备注 |

## 许可

MIT
