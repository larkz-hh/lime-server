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

### 注册

`POST /api/auth/register`

**请求体**

```json
{
  "email": "user@example.com",
  "password": "abc123",
  "phone": "13800138000"
}
```

| 字段     | 类型   | 必填 | 说明                       |
|----------|--------|------|----------------------------|
| email    | string | 是   | 邮箱，作为登录账号，全局唯一 |
| password | string | 是   | 6-32 位，需同时包含字母和数字 |
| phone    | string | 否   | 11 位手机号                 |

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

**请求体**

```json
{
  "email": "user@example.com",
  "password": "abc123"
}
```

| 字段     | 类型   | 必填 | 说明   |
|----------|--------|------|--------|
| email    | string | 是   | 登录邮箱 |
| password | string | 是   | 登录密码 |

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
