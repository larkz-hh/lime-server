# Lime Server

Lime 服务端部分。

为 Lime App 提供后端服务。开发中，梦到什么写什么。

## 技术栈

| 类别       | 技术                                          |
| ---------- | --------------------------------------------- |
| 框架       | Spring Boot 3.4.1                             |
| 语言       | Java 21                                       |
| 数据库     | MySQL + MyBatis-Plus 3.5.7                    |
| 缓存       | Redis                                         |
| 对象存储   | MinIO 8.5.12                                  |
| 消息队列   | RocketMQ 2.3.1                                |
| 安全       | Spring Security + JWT (jjwt 0.12.6)           |
| 邮件       | Spring Boot Mail                              |
| 工具库     | Hutool 5.8.25, Lombok                         |

## 快速开始

```bash
# 环境：JDK 21, MySQL, Redis, MinIO, RocketMQ

# 建库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS lime_server DEFAULT CHARACTER SET utf8mb4;"

# 导入表结构
mysql -u root -p lime_server < src/main/resources/schema.sql

# 配置 application.yaml，然后启动（默认端口 8080）
./mvnw spring-boot:run
```

## 项目结构

```
src/main/java/com/lzz/lime_server/
├── common/           # 统一响应、异常、错误码
├── config/           # 配置中心
├── controller/       # 接口层
├── dto/              # 请求 / 响应 DTO
├── entity/           # 实体
├── mapper/           # 数据访问层
├── service/          # 业务层
└── util/             # 工具类

src/main/resources/
├── schema.sql
└── application*.yml
```

## API 文档

[我是文档](./API.md)

## 功能计划 （Preview）

### 认证与账号

- [x] 邮箱验证码
- [x] 注册 / 登录 / 登出
- [x] JWT 双 Token 鉴权与刷新
- [x] 修改密码 / 注销账号

### 个人主页

- [x] 获取用户信息
- [x] 修改个人资料
- [x] 头像 / 背景图上传
- [ ] 收藏列表
- [ ] 浏览历史

### 笔记

- [x] 上传笔记图片
- [x] 发布图文笔记
- [ ] 首页信息流（分页）
- [ ] 笔记详情
- [ ] 编辑 / 删除笔记
- [ ] 草稿箱

### 搜索

- [ ] 关键词搜索笔记

### 互动

- [ ] 点赞 / 取消点赞
- [ ] 收藏 / 取消收藏
- [ ] 评论列表 / 发布评论
- [ ] 关注 / 取消关注
- [ ] 消息通知

### ...
