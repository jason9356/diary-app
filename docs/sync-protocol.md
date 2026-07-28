# 日记同步协议（v2）

单用户、本地优先。服务器是副本与中转；断网可写，联网后同步。

> v1（一天一篇、按 `entry_date` 路由）已废弃。客户端应要求 `GET /v1/health` 返回 `"protocol": 2`。

## 身份

| 概念 | 说明 |
|------|------|
| `id` | **主键**（UUID）。文件名、API 路径、changes、LWW 均按 `id` |
| `date` | 日历日 `YYYY-MM-DD`，可重复，仅用于分组与日上下文 |
| 天气/地点 | **按日**单独存储，不写在单条笔记 front matter 里 |

鉴权：`Authorization: Bearer <token>`。Token 由服务端环境变量 `DIARY_SYNC_TOKEN` 注入，不进仓库。

## 本地路径

| 类型 | 路径 |
|------|------|
| 笔记 | `diary/YYYY/MM/<id>.md` |
| 资源 | `assets/<id>/<file>`（Markdown 内写 `assets/<id>/<file>`） |
| 日上下文 | `diary/YYYY/MM/<date>.day.json` |

### 笔记 front matter

```yaml
---
date: 2026-07-28
title: 标题
id: 550e8400-e29b-41d4-a716-446655440000
created_at: 2026-07-28T01:00:00+00:00
updated_at: 2026-07-28T08:30:00+00:00
writing_duration_sec: 120
---
```

正文在 front matter 之后。天气字段不再出现在笔记 front matter。

### 日上下文 JSON

```json
{
  "date": "2026-07-28",
  "location": "上海",
  "weather": "晴",
  "temp_c": 28,
  "context_source": "phone",
  "context_updated_at": "2026-07-28T01:05:00+00:00",
  "updated_at": "2026-07-28T01:05:00+00:00"
}
```

## 冲突策略

| 对象 | 规则 |
|------|------|
| 笔记 `body` / `title` | LWW：比较 `updated_at`（UTC ISO），较新覆盖 |
| `writing_duration_sec` | 取较大值 |
| 笔记 `id` | 以 URL / 已存在记录为准，不可被另一 UUID 覆盖 |
| 图片文件 | 按笔记 `id` 目录并集保留；Markdown 引用以胜出正文为准 |
| 删除笔记 | tombstone：`deleted=true` + `deleted_at`；若 tombstone 新于对方 `updated_at` 则删 |
| 日上下文 | `phone` > `desktop` > `manual`；同级再比 `context_updated_at` |

同日多条笔记互不合并。

## API

Base URL 例：`https://diary.example.com`（客户端 `sync_endpoint`，默认挂 `/v1`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/health` | 探活（可无鉴权）；含 `"protocol": 2` |
| `GET` | `/v1/changes?since=<revision>` | 增量：笔记与日上下文变更 |
| `GET` | `/v1/entries/{id}` | 拉取单条笔记 |
| `PUT` | `/v1/entries/{id}` | 上传/合并单条笔记 |
| `GET` | `/v1/days/{date}` | 拉取日上下文 |
| `PUT` | `/v1/days/{date}` | 合并日上下文 |
| `PUT` | `/v1/assets/{id}/{name}` | 上传图片 |
| `GET` | `/v1/assets/{id}/{name}` | 下载图片 |

### `GET /v1/health`

```json
{ "ok": true, "revision": 42, "protocol": 2 }
```

### `GET /v1/changes`

```json
{
  "revision": 42,
  "changes": [
    {
      "kind": "entry",
      "id": "...",
      "date": "2026-07-28",
      "updated_at": "...",
      "deleted": false,
      "revision": 41
    },
    {
      "kind": "day",
      "date": "2026-07-28",
      "updated_at": "...",
      "revision": 42
    }
  ]
}
```

只返回 `revision > since` 的项。`since=0` 为全量索引。

### `GET/PUT /v1/entries/{id}`

PUT 请求体：

```json
{
  "date": "2026-07-28",
  "updated_at": "...",
  "created_at": "...",
  "writing_duration_sec": 0,
  "deleted": false,
  "deleted_at": null,
  "markdown": "---\n...\n",
  "assets": [{"name": "...", "sha256": "..."}]
}
```

响应含合并后的条目（含 `markdown`、`assets`、`revision`）。路径中的 `id` 必须与 front matter `id` 一致（不一致时以路径为准改正）。

### `GET/PUT /v1/days/{date}`

PUT 请求体即日上下文 JSON 字段。响应为合并后的日上下文 + `revision`。

### Assets

- `PUT`：原始字节；`X-Content-SHA256` 可选
- 同名且 sha256 相同则跳过写入
- Markdown 内路径：`assets/{id}/{name}`

## 从 v1 迁移

1. 旧文件 `diary/YYYY/MM/YYYY-MM-DD.md` → 读取 front matter `id`（无则生成）→ 写入 `diary/YYYY/MM/<id>.md`，`date` 保留。
2. 旧资源 `assets/YYYY-MM-DD/*` → `assets/<id>/`，并改写正文中的 `assets/YYYY-MM-DD/` 引用。
3. 若旧 front matter 含天气字段 → 写入 `<date>.day.json`（仅当尚无日上下文或旧上下文更弱时）。
4. 删除旧日期命名的 `.md`（迁移成功后）。
5. SQLite：`entries` 以 `id` 为主键；`entry_date` 改为可重复列 `date`。

客户端与服务器启动时可自检并执行迁移。

## 客户端职责

1. 保存时写入 `id` / `date` / `created_at` / `updated_at`；内容未变不刷新 `updated_at`。
2. 同步建议：`GET /changes` → 对变更条目/日上下文拉取合并 → 推送本地较新笔记与日上下文 → 补齐 assets。
3. 「今天」同步：确保当日所有本地笔记与日上下文都参与比对。
4. Token / endpoint 存本地，勿提交密钥。

## 非目标

多用户、WebSocket、E2E 加密、正文三路合并、长期双轨兼容 v1 路由。
