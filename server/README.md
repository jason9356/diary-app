# Diary Sync API

单用户同步服务（协议 **v2**：按笔记 `id` 同步，一天可多条）。契约见 [docs/sync-protocol.md](../docs/sync-protocol.md)。

## 本地运行

```powershell
cd server
pip install -r requirements.txt
$env:DIARY_SYNC_TOKEN = "dev-change-me"
$env:DIARY_SYNC_DATA = "$PWD\data"
uvicorn app.main:app --reload --port 8000
```

探活：`GET http://127.0.0.1:8000/v1/health`

## Docker（VPS）

```powershell
cd server
$env:DIARY_SYNC_TOKEN = "replace-with-long-random-token"
docker compose up -d --build
```

数据卷：`server/data/`（diary / assets / sync.db）。生产环境请在反向代理（Caddy/Nginx）后启用 HTTPS。
