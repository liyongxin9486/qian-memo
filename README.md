# 七安备忘（qian-memo）

轻量级 Android 备忘录 + 购物清单应用：**账号登录、多设备实时云同步、离线优先**。支持桌面小组件、手写笔记、图片附件、标签、搜索、回收站和 Excel 导出。

## 功能

- **备忘录**：富文本清单（勾选项）、置顶/收藏、标签分类、图片附件、**手写笔记**（手指直接写）
- **购物清单**：独立列表，数量/单位/标签，与备忘录共用标签体系
- **待办**：独立待办清单
- **桌面小组件**：Glance 实现，桌面直接查看/勾选/新建备忘，支持单条便签样式
- **云同步**：自建后端（Retrofit + OkHttp + WebSocket），本地 Room 离线优先，后台 WorkManager 定时对账，前后台切换强制重连实时通道；软删除 + ID 永不复用（PurgedIds）防多端冲突
- **搜索**：标题/内容/清单项全文检索
- **回收站**：软删除数据可恢复
- **导出**：备忘录导出 .xlsx
- Material 3 + Jetpack Compose，深色模式

## 技术栈

Kotlin · Jetpack Compose (Material 3) · Room + KSP · Glance 小组件 · Retrofit/OkHttp/WebSocket · WorkManager · DataStore · EncryptedSharedPreferences

- 最低支持 Android 8.0（API 26），目标 Android 15（API 35）
- 当前版本 1.0（versionCode 1）

## 后端说明

- 后端服务端代码**不在本仓库**；本仓库附带 `sql/init.sql`（MySQL 建表脚本，与 App 实体对齐：users / admins / memos / shopping_items / tags / memo_checklist_items / sync_logs / deleted_users_backup）
- API 基址通过 `gradle.properties` 的 `API_BASE_URL` 注入 BuildConfig，默认指向自建服务器（Nginx HTTPS 反代）
- 传输安全：自签证书 + OkHttp 证书锁定（`app/src/main/res/raw/server_cert`，示例域名占位）；登录凭据存储于 EncryptedSharedPreferences
- 自建后端时：修改 `API_BASE_URL`、替换 `server_cert` 为你自己的证书、按 `init.sql` 初始化数据库即可

## 构建

```bash
# 需 JDK 17 + Android SDK 35
# 编辑 local.properties 指向本机 SDK（sdk.dir=...）
./gradlew :app:assembleDebug
```

后端基址在 `gradle.properties` 的 `API_BASE_URL` 处修改；离线功能不依赖后端也可使用。

## 项目结构

```
app/src/main/java/com/example/beiwang/
├── data/
│   ├── database/     Room 数据库、实体、DAO、示例数据
│   ├── remote/       Retrofit API、网络模块（证书锁定）
│   ├── sync/         同步管理器、实时通道（WebSocket）、ID 治理
│   ├── repository/   备忘/待办/购物/标签数据仓库
│   ├── backup/       本地 JSON 备份/恢复
│   └── export/       Excel 导出
├── ui/               Compose 界面（登录/备忘/待办/购物/标签/搜索/回收站/我的）
└── widget/           Glance 桌面小组件
sql/init.sql          后端 MySQL 建表脚本
```

## 项目文档

- [七安备忘_Android端产品需求说明.md](七安备忘_Android端产品需求说明.md) —— 产品需求说明
- [BUG_REVIEW.md](BUG_REVIEW.md) —— 全量源码静态审查 + 实机压力测试的 Bug 报告

## 免责声明

本项目为个人备忘工具，按"现状"提供；同步后端需自行搭建与维护，请妥善保管服务器与数据库凭据。
