# 灵感匣同步协议（v3）

单用户、本地优先。服务器是副本与中转；断网可写，联网后同步。

产品：**灵感匣**（灵感卡片收集器，非日记替代品）。

> 客户端应接受 `GET /v1/health` 返回 `"protocol": 2` 或 `3`。v3 增加卡片 `tags` / `pinned` 语义与 `/v1/todos`；卡片正文仍按 Markdown LWW。

## 身份

| 概念 | 说明 |
|------|------|
| `id` | **主键**（UUID）。文件名、API 路径、changes、LWW 均按 `id` |
| `date` | 日历日 `YYYY-MM-DD`，用于筛选与排序，可重复 |
| `tags` | 卡片标签列表（front matter） |
| 日上下文 | 可选遗留：天气/地点按日存储，主产品路径不再依赖 |

鉴权：`Authorization: Bearer <token>`。Token 由服务端环境变量 `DIARY_SYNC_TOKEN` 注入，不进仓库。

## 本地路径

| 类型 | 路径 |
|------|------|
| 灵感卡片 | `diary/YYYY/MM/<id>.md`（路径暂沿用；语义为卡片） |
| 资源 | `assets/<id>/<file>` |
| 本机待办 | 客户端 `todos/todos.json`；服务端 `todos.json`（文档级 LWW） |
| 日上下文（遗留） | `diary/YYYY/MM/<date>.day.json` |

统一目录约定见 [vault-schema.md](vault-schema.md)（Vault schema v1）。

### 本机待办文档（增强）

本地与 API 载荷中的 `json` 字段均为同一文档形状：

```json
{
  "updated_at": "<ISO>",
  "items": [
    {
      "id": "<uuid>",
      "text": "标题",
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

`GET/PUT /v1/todos` 仍使用外壳 `{"updated_at","json"}`：`json` 为上述文档的字符串（或对象，服务端规范化）。旧版裸数组客户端读入时迁移。

### 卡片 front matter

```yaml
---
date: 2026-07-28
title: 标题
id: 550e8400-e29b-41d4-a716-446655440000
created_at: 2026-07-28T01:00:00+00:00
updated_at: 2026-07-28T08:30:00+00:00
tags: [灵感, 工作]
pinned: false
---
```

正文在 front matter 之后。正文中的 `#标签` 也会被客户端合并进 `tags`。

## 冲突策略

| 对象 | 规则 |
|------|------|
| 卡片 `body` / `title` / `tags` | LWW：比较 `updated_at`（UTC ISO），较新覆盖（整份 Markdown） |
| 卡片 `id` | 以 URL / 已存在记录为准 |
| 图片文件 | 按卡片 `id` 目录并集保留 |
| 删除卡片 | tombstone：`deleted=true` + `deleted_at` |
| 本机待办文档 | LWW：比较文档级 `updated_at` |
| Obsidian 待办 | **不以本同步协议传输**；经对象存储读写日记 Markdown，原文权威 |

## API

Base URL 例：`https://diary.example.com`（客户端 `sync_endpoint`，默认挂 `/v1`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/health` | 探活；含 `"protocol": 3`、`"product": "sparkbox"` |
| `GET` | `/v1/changes?since=<revision>` | 增量：卡片与日上下文变更 |
| `GET` | `/v1/entries/{id}` | 拉取单条卡片 |
| `PUT` | `/v1/entries/{id}` | 上传/合并单条卡片 |
| `GET` | `/v1/days/{date}` | 拉取日上下文（遗留） |
| `PUT` | `/v1/days/{date}` | 合并日上下文（遗留） |
| `GET` | `/v1/todos` | 拉取本机待办文档 |
| `PUT` | `/v1/todos` | 上传本机待办文档 `{"updated_at","json"}` |
| `PUT` | `/v1/assets/{id}/{name}` | 上传图片 |
| `GET` | `/v1/assets/{id}/{name}` | 下载图片 |

## Obsidian 桥（客户端）

不经过本同步服务。Android 使用 S3 兼容对象存储列出日记目录 `.md`，按与 [obsidian-diary-todo-board](https://github.com/) 相同规则抽取有序列表句首 `【状态】`，完成时回写 `**【已完成】**`。

## AI（预留）

客户端提供 `AiHooks`（总结 / 日报 / 建议标签），默认关闭，不绑定具体模型。
