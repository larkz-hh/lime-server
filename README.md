# Lime Server

<div align="center">

![Language](https://img.shields.io/badge/language-Java%2021-blue)
![Framework](https://img.shields.io/badge/framework-Spring%20Boot%203.4.1-green)
![Database](https://img.shields.io/badge/database-MySQL%20%2B%20Redis-orange)
![MQ](https://img.shields.io/badge/messaging-RocketMQ-red)
![Storage](https://img.shields.io/badge/storage-MinIO-lightgrey)
![AI](https://img.shields.io/badge/AI-OpenAI%20Compatible-purple)
![IM](https://img.shields.io/badge/IM-Tencent%20Cloud-blue)
![Stars](https://img.shields.io/github/stars/larkz-hh/lime-server)
![Forks](https://img.shields.io/github/forks/larkz-hh/lime-server)
![Last Commit](https://img.shields.io/github/last-commit/larkz-hh/lime-server)
![PRs](https://img.shields.io/badge/PRs-welcome-brightgreen)
![License](https://img.shields.io/badge/license-Apache_2.0-blue)

基于 Spring Boot 的图文 / 视频社区后端，为仿小红书 App **Lime** 提供笔记、信息流、搜索、互动通知、AI 辅助与腾讯云 IM 私信能力。

[🚀 快速开始](#-快速开始) • [🗺️ 功能全景](#-功能全景) • [🛠️ 技术栈](#tech) • [🤝 贡献](#-贡献指南)

</div>

---

## 📱 项目介绍

**Lime Server** 是社区 App **Lime** 的服务端，Java 21 + Spring Boot 3.4 构建，提供图文 / 视频笔记、社区互动、AI 写作对话与即时通信等完整后端能力，客户端接入自建 REST + SSE 接口与腾讯云 IM。

### ✨ 核心特性

- 🏠 **信息流 / 视频流** - 游标分页，GET 接口自动 ETag 缓存
- 📝 **发布与草稿箱** - 图文 / 视频发布编辑删除，已发布笔记可另存草稿副本
- 💬 **互动** - 点赞、收藏（可设隐私）、评论回复（文字 / 图片 / 语音）、弹幕
- 👥 **关注体系** - 关注列表、互关好友、关注动态 feed、主页互动统计
- 🔔 **通知** - RocketMQ 异步落库 + SSE 实时推送未读与强制下线
- 🔍 **搜索** - 笔记 / 用户 / 联想 / 热搜
- 🤖 **AI** - SSE 流式写作与聊天、流内工具调用、联网搜索、多模型
- 🔐 **账号安全** - JWT 双 Token、单设备登录、游客免登录浏览、对外 uid 防枚举
- 💬 **腾讯云 IM** - UserSig 下发、互关私信授权、群头像上传

---

## 🗺️ 功能全景

### 📝 笔记

- 图文 / 视频发布、编辑、删除；草稿箱与草稿副本
- 首页信息流 / 视频流 / 关注动态，Cursor 分页
- 浏览历史；游客只计浏览、不写历史

### 💬 互动

- 点赞 / 收藏，列表隐私开关
- 评论与回复（文字 / 图片 / 语音）
- 视频弹幕（按播放时间点拉取）
- 关注 / 取关、关注与粉丝列表、互关好友、主页统计

### 🔔 通知

- 点赞、收藏、评论、回复、关注事件
- RocketMQ 异步落库，SSE 推送未读
- 异地登录强制下线 kick

### 🔍 搜索

- 笔记：排序（综合 / 最新 / 点赞 / 评论 / 收藏）+ 类型、时间过滤
- 用户搜索、搜索联想、热搜榜

### 👤 用户与账号

- 邮箱验证码注册登录、JWT 双 Token、改密、注销
- 资料编辑、头像 / 背景上传
- 对外 uid：二维码 / 外链直达主页
- 单设备登录；游客只读浏览公开内容

### 🤖 AI

- 看图配文、润色、续写、起标题、翻译
- 多轮聊天（发图 / 引用笔记）、联网搜索、天气等工具
- SSE 流式 + 流内识别工具调用

### 💬 即时通信

- 腾讯云 IM UserSig 下发
- 互关双方私信授权
- 群头像上传等辅助接口

---

<a id="tech"></a>
## 🛠️ 技术栈

| 分类     | 技术 |
| -------- | --- |
| 语言     | Java 21 |
| 框架     | Spring Boot 3.4.1、Spring Security、Spring MVC |
| 数据     | MySQL 8 + MyBatis-Plus 3.5.7（FULLTEXT ngram） |
| 缓存     | Redis 7（JWT 黑名单、限流、热搜、ETag） |
| 消息队列 | RocketMQ（2.3.1） |
| 存储     | MinIO |
| 鉴权     | JWT（jjwt 0.12.6）双 Token |
| 实时     | SSE（SseEmitter） |
| AI       | 自研 OpenAI 兼容 Provider |
| IM       | 腾讯云 IM（TLSSigAPIv2） |
| 其他     | Spring Mail、Hutool、Lombok、ip2region |

---

## 🚀 快速开始

**前置**：JDK 21+、MySQL、Redis、MinIO、RocketMQ（可用 Docker Compose 一键拉起）

```bash
# 1. 起依赖
cp .env.example .env     # 改密码 / MinIO 地址等
docker compose up -d mysql redis minio rocketmq-namesrv rocketmq-broker

# 2. 建库导表
mysql -h 127.0.0.1 -u root -p -e "CREATE DATABASE IF NOT EXISTS lime_db DEFAULT CHARACTER SET utf8mb4;"
mysql -h 127.0.0.1 -u root -p lime_db < src/main/resources/schema.sql

# 3. 启动（默认 8080）
./mvnw spring-boot:run
```

> 密钥类配置（JWT、SMTP、MinIO、AI、IM）从 `.env` 注入，见 [.env.example](.env.example)；存量库升级按需执行 `src/main/resources/migrate_add_uid.sql`。

---

## 💡 技术亮点

- **SSE 流式 + 流内工具调用**：单次流式边收边转发，按 index 累积增量 `tool_calls`，识别后执行工具再续流
- **RocketMQ 通知 + SSE 推送**：互动事件异步落库，同一连接下发异地登录 kick
- **游标分页 + 批量回填**：关注关系与粉丝数 `IN + GROUP BY` 一次回填，避免 N+1
- **游客与隐私分层**：Security 放行只读 GET，服务层再校验草稿与列表隐私
- **对外 uid**：注册生成，二维码 / 外链直达主页，不暴露自增 id
- **ETag + 热搜缓存**：GET JSON 自动 ETag，热搜 ZSET 按天轮转限时缓存

---

## 📄 API 文档

完整接口文档见 [API.md](API.md)。

---

## 🤝 贡献指南

欢迎 Issue、PR 和讨论！

1. Fork 并创建特性分支
2. 提交遵循 Conventional Commits（`feat(xxx): …` / `fix(xxx): …`）
3. 新接口同步更新 [API.md](API.md)
4. 提交前确保 `./mvnw -q compile` 通过

---

## 📄 许可证

本项目采用 **Apache License 2.0**，详见 [LICENSE](LICENSE)。

```text
Copyright 2026 larkz-hh
Licensed under the Apache License, Version 2.0 (the "License");
...
```

---

## 📞 联系方式

- 🐛 **Issues**：[GitHub Issues](https://github.com/larkz-hh/lime-server/issues)
- 👤 **作者**：[@larkz-hh](https://github.com/larkz-hh)

---

<div align="center">

[⬆ 回到顶部](#lime-server)

**⭐ 如果觉得项目有帮助，请给个 Star！**

Made with ❤️ by [larkz-hh](https://github.com/larkz-hh)

</div>
