# 灵感匣 Vault Schema v1

本地优先的统一数据目录。仅本机或云盘（WebDAV）镜像，都围绕同一套文件布局。

## 目录

```text
vault/   （Android: filesDir/diary_data）
  manifest.json
  diary/YYYY/MM/<uuid>.md
  diary/YYYY/MM/<date>.day.json   # 可选日上下文
  assets/<uuid>/<file>
  todos/todos.json
```

## manifest.json

```json
{
  "schema_version": 1,
  "exported_at": "<ISO-8601>",
  "device": "<短设备名>",
  "revision": 0
}
```

- `revision`：可选单调计数；WebDAV/导出时写入，便于对照。
- 日常读写可不强制更新 manifest；导出或云盘镜像完成时刷新。

## 灵感卡片

见 [sync-protocol.md](sync-protocol.md) front matter。路径：`diary/YYYY/MM/<id>.md`。

## 本机待办 `todos/todos.json`

```json
{
  "updated_at": "<ISO-8601>",
  "items": [
    {
      "id": "<uuid>",
      "text": "标题/短句",
      "detail": "",
      "done": false,
      "kind": "task",
      "due_at": null,
      "priority": 0,
      "urgency": 0,
      "created_at": "<ISO>",
      "updated_at": "<ISO>"
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `kind` | `task` \| `note` \| `errand` \| `other` |
| `due_at` | ISO 日期或日期时间；无则 `null` / 省略 |
| `priority` | `0` 无，`1..3` 低中高 |
| `urgency` | `0` 无，`1..3` 低中高 |
| `detail` | 可选长说明 |

**兼容**：若文件为旧版裸数组 `[{...}]`，读入时迁移为上述文档，并为缺省字段填默认值。

**同步 LWW**：比较文档级 `updated_at`（整份替换）。

## 存放目标（客户端）

| `storage_target` | 行为 |
|------------------|------|
| `local` | 仅本机；不同步上行 |
| `cloud` | 云盘适配器；`webdav` 可镜像目录；其它厂商配置预留 |

## 云盘镜像（WebDAV）

将本地 `diary/`、`assets/`、`todos/`（及可选 `manifest.json`）同步到 `webdav_root`。冲突：按文件 `updated_at` 或远端 `Last-Modified` 整文件 LWW，不做段落级合并。
