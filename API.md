# Lime Server API 文档

## 通用说明

**Base URL**：`/api`

### 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| code | 含义 |
|------|------|
| 200  | 成功 |
| 400  | 参数错误 |
| 401  | 未登录或 Token 已过期 |
| 403  | 账号被封禁 |
| 500  | 操作失败（业务异常） |

### 鉴权方式

需要登录的接口，请求头携带 Access Token：

```
Authorization: Bearer <accessToken>
```

---

## 认证接口 `/api/auth`

> 以下接口无需登录即可访问。

---

### 发送验证码

`POST /api/auth/send-code`

注册和验证码登录前调用，验证码有效期 5 分钟，同一邮箱 60 秒内只能发一次。

**请求体**

```json
{
  "email": "user@example.com"
}
```

| 字段  | 类型   | 必填 | 说明     |
|-------|--------|------|----------|
| email | string | 是   | 接收验证码的邮箱 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 注册

`POST /api/auth/register`

注册前需先调用发送验证码接口。

**请求体**

```json
{
  "email": "user@example.com",
  "password": "abc123",
  "code": "123456",
  "phone": "13800138000"
}
```

| 字段     | 类型   | 必填 | 说明                          |
|----------|--------|------|-------------------------------|
| email    | string | 是   | 邮箱，作为登录账号，全局唯一   |
| password | string | 是   | 6-32 位，需同时包含字母和数字  |
| code     | string | 是   | 邮箱验证码                    |
| phone    | string | 否   | 11 位手机号                   |

注册成功后系统自动生成昵称（`用户xxxxxx`）和 handle（`user_xxxxxxxx`），用户可在个人设置中修改。

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 登录

`POST /api/auth/login`

支持密码登录和验证码登录，`password` 与 `code` 二选一，验证码登录前需先调用发送验证码接口。

**密码登录**

```json
{
  "email": "user@example.com",
  "password": "abc123"
}
```

**验证码登录**

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

| 字段     | 类型   | 必填 | 说明                        |
|----------|--------|------|-----------------------------|
| email    | string | 是   | 登录邮箱                    |
| password | string | 二选一 | 登录密码                  |
| code     | string | 二选一 | 邮箱验证码                |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "expiresIn": 7200
  }
}
```

| 字段         | 类型   | 说明                        |
|--------------|--------|-----------------------------|
| accessToken  | string | 访问令牌，携带在请求头中     |
| refreshToken | string | 刷新令牌，用于无感续期       |
| expiresIn    | number | accessToken 有效期（秒）     |

---

### 登出

`POST /api/auth/logout`

**需要登录**，请求头携带 `Authorization: Bearer <accessToken>`。

**请求体**：无

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 刷新 Token

`POST /api/auth/refresh`

Access Token 过期后，用 Refresh Token 换取新的双 Token。

**请求体**

```json
{
  "refreshToken": "eyJhbGci..."
}
```

| 字段         | 类型   | 必填 | 说明       |
|--------------|--------|------|------------|
| refreshToken | string | 是   | 刷新令牌   |

**响应**：同登录接口，返回新的 `accessToken`、`refreshToken`、`expiresIn`。

---

## 用户接口 `/api/user`

> 以下接口均**需要登录**，请求头携带 `Authorization: Bearer <accessToken>`。

---

### 获取当前用户信息

`GET /api/user/me`

**请求体**：无

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "用户abc123",
    "handle": "user_xxxxxxxx",
    "bio": "这是我的简介",
    "avatar": "http://localhost:9000/lime-bucket/avatars/uuid.jpg",
    "backgroundImage": "http://localhost:9000/lime-bucket/backgrounds/uuid.jpg",
    "gender": 1,
    "birthday": "2000-01-01",
    "region": "上海",
    "role": "USER"
  }
}
```

| 字段            | 类型   | 说明                                        |
|-----------------|--------|---------------------------------------------|
| id              | number | 用户 ID                                     |
| email           | string | 登录邮箱                                    |
| nickname        | string | 昵称                                        |
| handle          | string | 唯一标识符（类似用户名）                     |
| bio             | string | 个人简介，可为 null                          |
| avatar          | string | 头像图片 URL，可为 null                      |
| backgroundImage | string | 个人主页背景图 URL，可为 null                |
| gender          | number | 性别：0=未设置，1=男，2=女，可为 null        |
| birthday        | string | 生日，格式 `yyyy-MM-dd`，可为 null           |
| region          | string | 地区，可为 null                              |
| role            | string | 角色，当前固定为 `USER`                      |

---

### 修改个人资料

`PUT /api/user/me`

所有字段均为可选，只传需要修改的字段即可。`bio` 和 `region` 传空字符串 `""` 可清空对应字段。

**请求体**

```json
{
  "nickname": "新昵称",
  "bio": "新的个人简介",
  "gender": 1,
  "birthday": "2000-01-01",
  "region": "上海"
}
```

| 字段     | 类型   | 必填 | 说明                                  |
|----------|--------|------|---------------------------------------|
| nickname | string | 否   | 昵称，1-20 个字符                      |
| bio      | string | 否   | 简介，最多 200 个字符，传 `""` 可清空  |
| gender   | number | 否   | 性别：0=未设置，1=男，2=女             |
| birthday | string | 否   | 生日，格式 `yyyy-MM-dd`               |
| region   | string | 否   | 地区，最多 50 个字符，传 `""` 可清空   |

**响应**：返回更新后的用户信息，结构同「获取当前用户信息」。

---

### 上传 / 更换头像

`POST /api/user/me/avatar`

**Content-Type**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明                                     |
|------|------|------|------------------------------------------|
| file | file | 是   | 图片文件，支持 JPG / PNG / WebP / GIF，最大 5MB |

**响应**：返回更新后的用户信息，结构同「获取当前用户信息」，`avatar` 字段为新头像的访问 URL。

---

### 上传 / 更换背景图

`POST /api/user/me/background`

**Content-Type**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明                                     |
|------|------|------|------------------------------------------|
| file | file | 是   | 图片文件，支持 JPG / PNG / WebP / GIF，最大 5MB |

**响应**：返回更新后的用户信息，结构同「获取当前用户信息」，`backgroundImage` 字段为新背景图的访问 URL。
