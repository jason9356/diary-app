# 日记同步协议（v1）

单用户、本地优先。服务器是副本与中转；断网可写，联网后同步。

## 身份

| 概念 | 说明 |
|------|------|
| `entry_date` | 业务唯一键，一天一篇（`YYYY-MM-DD`） |
| `id` | 稳定 UUID，首次创建生成，写入 Markdown front matter，之后不改 |
| 同日不同 `id` | 以**先到达服务器**的 `id` 为准；后到的客户端改为服务器 `id` |

鉴权：`Authorization: Bearer <token>`。Token 由服务端环境变量 `DIARY_SYNC_TOKEN` 注入，不进仓库。

## Markdown front matter

必选 / 常用字段：

```yaml
---
date: 2026-07-28
title: 标题
id: 550e8400-e29b-41d4-a716-446655440000
created_at: 2026-07-28T01:00:00+00:00
updated_at: 2026-07-28T08:30:00+00:00
location: 上海
weather: 晴
temp_c: 28
context_source: phone
context_updated_at: 2026-07-28T01:05:00+00:00
writing_duration_sec: 120
---
```

正文在 front matter 之后。图片相对 **data root**：`assets/YYYY-MM-DD/<file>`。

## 冲突策略

| 字段 | 规则 |
|------|------|
| `body` / `title` | Last-Write-Wins：比较 `updated_at`（UTC ISO），较新覆盖 |
| `location` / `weather` / `temp_c` | `phone` > `desktop` > `manual`；同级再比 `context_updated_at` |
| `writing_duration_sec` | 取较大值 |
| 图片文件 | 并集保留；Markdown 引用以胜出正文为准 |
| 删除 | tombstone：`deleted=true` + `deleted_at`；若 tombstone 新于对方 `updated_at` 则删，否则内容胜出 |

## API

Base URL 例：`https://diary.example.com`（客户端配置为 `sync_endpoint`，勿尾随多余路径时默认挂 `/v1`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/v1/health` | 探活（可无鉴权） |
| `GET` | `/v1/changes?since=<revision>` | 增量变更列表；`since=0` 表示全量索引 |
| `GET` | `/v1/entries/{date}` | 拉取单日 Markdown + 资源清单 |
| `PUT` | `/v1/entries/{date}` | 上传单日；服务端按冲突规则合并 |
| `PUT` | `/v1/assets/{date}/{name}` | 上传图片字节；`X-Content-SHA256` 可选 |
| `GET` | `/v1/assets/{date}/{name}` | 下载图片 |

### `GET /v1/changes`

```json
{
  "revision": 42,
  "changes": [
    {
      "entry_date": "2026-07-28",
      "id": "...",
      "updated_at": "...",
      "deleted": false,
      "revision": 42
    }
  ]
}
```

只返回 `revision > since` 的条目。

### `GET /v1/entries/{date}`

```json
{
  "entry_date": "2026-07-28",
  "id": "...",
  "updated_at": "...",
  "created_at": "...",
  "deleted": false,
  "writing_duration_sec": 0,
  "markdown": "---\n...\n",
  "assets": [
    {"name": "a1b2c3d4e5f6.jpg", "sha256": "...", "size": 12345}
  ],
  "revision": 42
}
```

404：服务器无该日记录。

### `PUT /v1/entries/{date}`

请求体：

```json
{
  "id": "...",
  "updated_at": "...",
  "created_at": "...",
  "writing_duration_sec": 0,
  "deleted": false,
  "deleted_at": null,
  "markdown": "---\n...\n",
  "assets": [{"name": "...", "sha256": "..."}]
}
```

响应：合并后的条目（同 GET 形状）。客户端应用返回的 `id` / `markdown`，并更新本地 `synced_at`。

### Assets

- `PUT`：body 为原始字节；`Content-Type: application/octet-stream`
- 若同名且 sha256 相同则跳过写入
- Markdown 内路径保持 `assets/{date}/{name}`

## 客户端职责

1. 每次保存将 `id`、`created_at`、`updated_at` 写入 front matter。
2. 同步顺序建议：上传本地缺失的 assets → `PUT` 当日条目 → `GET /changes` → 拉取并合并远端更新 → 下载缺失 assets。
3. `device_id`：每台设备生成一次 UUID，仅用于日志/调试（v1 不参与冲突）。
4. Token / endpoint 存本地配置，勿提交真实密钥。

## 非目标（v1）

多用户、WebSocket 推送、E2E 加密、上传桌面整库 SQLite。
