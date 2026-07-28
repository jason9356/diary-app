# 个人日记应用（Windows 桌面端）

美观、私密、数据完全本地的个人日记。打开即写，自动保存。  
技术栈：**Python 3.10+ / PySide6 / SQLite / Markdown**。

当前版本：**0.2.0** · 变更记录见 [CHANGELOG.md](./CHANGELOG.md) · 许可 [MIT](./LICENSE)

> Android 端见独立目录 [`android/`](./android/) 与 [docs/android-client.md](./docs/android-client.md)，请勿与桌面 `src/` 混用。

## 功能（第一版）

- 打开应用直接进入今天的日记编辑区
- Markdown 写作（标题、加粗、列表、引用、代码块）+ **实时渲染预览**（编辑 / 分栏 / 预览）
- **插入图片**：工具栏按钮 / 拖入；当日图条浏览；预览单图通栏、多图双列
- 自动保存；记录创建/修改时间与写作时长（标题下短格式）
- **地点 · 天气 · 温度**：标题下展示；电脑端可联网获取（Open-Meteo），日后手机端可覆盖为权威
- 日历圆点标记有日记的日期；时间线倒序浏览
- 按年/月筛选；全文搜索并高亮
- 导出 ZIP（Markdown + 图片）
- 深色 / 浅色 / 跟随系统；霞鹜文楷

## 快速开始

### 1. 环境要求

- Windows 10/11
- Python 3.10 及以上（推荐 3.11+），安装时勾选 **Add python.exe to PATH**

### 2. 安装依赖（推荐）

在项目根目录双击或运行：

```powershell
.\setup.bat
```

会在本机创建 `.venv` 并安装 `requirements.txt`。换电脑 / 新 clone 后都重新跑一次即可（**不要**把别人的 `.venv` 拷过来）。

手动等价命令：

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
# 国内镜像可选：
# pip install -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt
```

### 3. 运行 / 调试

```powershell
.\run.bat
# 或
.\.venv\Scripts\python.exe src\main.py
```

`run.bat` 若发现 `.venv` 不可用，会自动调用 `setup.bat`。

### 换机继续开发

1. `git pull`
2. 本机有 Python 后执行 `setup.bat`（仅首次或 `.venv` 损坏时）
3. 改代码 → 本地跑 → `git commit` / `push`  
日记正文在 `data/diary/`，默认不进 Git；换机要带日记请自行拷贝 `data/`（勿提交本机绝对路径）。

### 4. 打包为可执行文件（正式分发）

开发阶段用 `run.bat` 即可，改完马上看效果。  
给别人用、或想双击一个图标启动时，再用 PyInstaller 打成独立程序：

```powershell
pip install pyinstaller
pyinstaller --noconfirm --windowed --name DiaryApp --paths src src\main.py
```

生成：`dist\DiaryApp\DiaryApp.exe`。  
注意：打包后仍建议把 `data\` 放在可写位置（默认仍是项目旁的数据目录；可在 `data\config.json` 改 `data_dir`）。

> `run.bat` ≠ 正式安装包，只是开发启动器。正式版一般是编译/打包后的 `.exe`。

## 数据目录

```
data/
├── config.json          # 窗口/主题等偏好
├── diary.db             # SQLite 索引与全文搜索
├── diary/
│   └── YYYY/MM/YYYY-MM-DD.md
└── assets/
    └── YYYY-MM-DD/
        └── <image>.jpg
```

- **Markdown**：纯文本可读；front matter 含 `id` / `updated_at` / 地点天气等（同步协议）
- **SQLite**：标题、日期、字数、写作时长、地点/天气、内容哈希、`synced_at`
- **天气**：可选在 `config.json` 设置 `weather_city`（如 `"上海"`）；未设置时尝试 IP 粗定位。需联网；手机端同步后优先生效
- **同步服务**：见 [docs/sync-protocol.md](./docs/sync-protocol.md) 与 [server/README.md](./server/README.md)；桌面菜单「同步…」/ `Ctrl+Shift+S`

## 项目结构

```
diary-app/
├── README.md
├── requirements.txt
├── docker-compose.yaml
├── run.bat
├── src/
│   ├── main.py              # 入口
│   ├── app/                 # 配置、日记服务、写作计时
│   ├── storage/             # SQLite / Markdown / 图片 / 导出
│   ├── ui/                  # 主窗口、编辑器、日历、时间线、搜索
│   └── utils/               # 路径、日志
├── data/                    # 本地数据（gitignore）
├── logs/                    # 运行日志
└── docs/
```

## 使用说明

| 操作 | 说明 |
|------|------|
| 开始写 | 启动后光标已在今天的编辑区 |
| 换一天 | 左侧日历点日期，或切到时间线 |
| 插图片 | 工具栏「图片」、`Ctrl+Shift+I`，或拖入编辑区 |
| 天气 | 点标题下地点行；或菜单「获取天气 / 编辑地点」 |
| 搜索 | 侧边栏「搜索」，点结果跳转并高亮 |
| 导出 | 侧边栏「导出」或 `Ctrl+E` |
| 同步 | 菜单「同步…」`Ctrl+Shift+S`；先「同步设置」填 endpoint + token |
| 主题 | 「主题」按钮或 `Ctrl+Shift+L` |
| 今天 | `Ctrl+T` |

## 验收对照

1. 安装依赖后运行 / 双击 `run.bat` 可打开，直接写今天  
2. 关闭再打开，当天内容仍在  
3. 写过的日期日历有圆点，没写的没有  
4. 搜索能命中正文关键词  
5. 导出 ZIP 内含 `diary/**/*.md` 与 `assets/**`  
6. 本 README 说明了运行与打包方式  

## 设计说明（后续扩展）

- 每条日记有稳定 `UUID`、`content_hash`、`synced_at`；front matter 同步 `id` / 时间戳
- 文件路径约定与移动端共用；同步 API 见 `docs/sync-protocol.md`
- 仍未做：多用户、标签、统计、E2E 加密

## 日志与排错

日志文件：`logs/diary-app.log`。  
若窗口无法启动，请在终端运行 `python src\main.py` 查看报错；常见原因是未安装 PySide6。
