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

### 获取指定用户公开资料

`GET /api/user/{userId}`

**需要登录**：是

**Path 参数**

| 参数   | 类型   | 说明    |
|--------|--------|---------|
| userId | number | 用户 ID |

**请求体**：无

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 7,
    "nickname": "taffy",
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

> 不返回 `email` 字段（隐私保护）。

| 字段            | 类型   | 说明                                        |
|-----------------|--------|---------------------------------------------|
| id              | number | 用户 ID                                     |
| nickname        | string | 昵称                                        |
| handle          | string | 唯一标识符                                   |
| bio             | string | 个人简介，可为 null                          |
| avatar          | string | 头像图片 URL，可为 null                      |
| backgroundImage | string | 个人主页背景图 URL，可为 null                |
| gender          | number | 性别：0=未设置，1=男，2=女，可为 null        |
| birthday        | string | 生日，格式 `yyyy-MM-dd`，可为 null           |
| region          | string | 地区，可为 null                              |
| role            | string | 角色，当前固定为 `USER`                      |

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

---

### 修改密码

`PUT /api/user/me/password`

支持两种身份验证方式，`oldPassword` 与 `code` 二选一。使用验证码方式前需先调用 `/api/auth/send-code` 向当前账号绑定的邮箱发送验证码。修改成功后当前 Token 立即失效，客户端需重新登录获取新 Token。

**原密码方式**

```json
{
  "oldPassword": "abc123",
  "newPassword": "newpass456"
}
```

**验证码方式**

```json
{
  "code": "123456",
  "newPassword": "newpass456"
}
```

| 字段        | 类型   | 必填   | 说明                          |
|-------------|--------|--------|-------------------------------|
| oldPassword | string | 二选一 | 当前密码                      |
| code        | string | 二选一 | 邮箱验证码                    |
| newPassword | string | 是     | 新密码，6-32 位               |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

### 注销账号

`DELETE /api/user/me`

账号软删除，数据库记录保留但标记为已删除，注销后 Token 立即失效。需提供当前密码二次确认身份。

**请求体**

```json
{
  "password": "abc123"
}
```

| 字段     | 类型   | 必填 | 说明                     |
|----------|--------|------|--------------------------|
| password | string | 是   | 当前账号密码，用于身份确认 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

---

## 笔记接口 `/api/notes`

> 以下接口均需登录。

---

### 获取用户点赞笔记列表

`GET /api/notes/user/{userId}/likes`

**需要登录**：是

若目标用户已开启点赞列表隐私，非本人访问时返回业务错误（code 500，message "该用户已开启点赞列表隐私"）。

**Path 参数**

| 参数   | 类型   | 说明    |
|--------|--------|---------|
| userId | number | 用户 ID |

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                     |
|--------|--------|------|------------------------------------------|
| cursor | number | 否   | 上一页游标（`nextCursor` 字段值），首次不传 |
| size   | number | 否   | 每页条数，默认 10，最大 50                |

**响应**：结构同「获取信息流」，`nextCursor` 基于点赞记录 ID（按点赞时间倒序）。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 42,
        "title": "关注科协喵",
        "coverImage": "http://minio-host/lime/notes/uuid1.jpg",
        "likeCount": 128,
        "liked": true,
        "author": {
          "id": 7,
          "nickname": "taffy",
          "avatar": "http://minio-host/lime/avatars/uuid.jpg"
        }
      }
    ],
    "nextCursor": 15,
    "hasMore": true
  }
}
```

---

### 获取用户收藏笔记列表

`GET /api/notes/user/{userId}/favorites`

**需要登录**：是

若目标用户已开启收藏列表隐私，非本人访问时返回业务错误（code 500，message "该用户已开启收藏列表隐私"）。

**Path 参数**

| 参数   | 类型   | 说明    |
|--------|--------|---------|
| userId | number | 用户 ID |

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                     |
|--------|--------|------|------------------------------------------|
| cursor | number | 否   | 上一页游标（`nextCursor` 字段值），首次不传 |
| size   | number | 否   | 每页条数，默认 10，最大 50                |

**响应**：结构同「获取用户点赞笔记列表」，`nextCursor` 基于收藏记录 ID（按收藏时间倒序）。

---

### 获取当前用户浏览历史

`GET /api/notes/history`

**需要登录**：是

返回当前登录用户自己的浏览历史，按最近浏览时间倒序排列，Cursor 分页。每条笔记只出现一次，重复浏览同一笔记时会将其更新至历史顶部。

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                               |
|--------|--------|------|----------------------------------------------------|
| cursor | number | 否   | 上一页最后一条记录的浏览时间（epoch 毫秒），首次不传 |
| size   | number | 否   | 每页条数，默认 10，最大 50                          |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 42,
        "title": "关注科协喵",
        "coverImage": "http://minio-host/lime/notes/uuid1.jpg",
        "likeCount": 18,
        "liked": false,
        "author": {
          "id": 7,
          "nickname": "taffy",
          "avatar": "http://minio-host/lime/avatars/uuid.jpg"
        }
      }
    ],
    "nextCursor": 1706745600123,
    "hasMore": true
  }
}
```

| 字段        | 类型    | 说明                                                       |
|-------------|---------|-------------------------------------------------------------|
| items       | array   | 浏览过的笔记卡片列表，结构同「获取信息流」                  |
| items[].viewTime | string | 最近浏览时间，格式 `yyyy-MM-ddTHH:mm:ss`             |
| nextCursor  | number  | 下一页游标（最后一条记录的浏览时间 epoch 毫秒），无更多数据时为 null |
| hasMore     | boolean | 是否还有下一页                                             |

---

### 批量删除浏览历史记录

`DELETE /api/notes/history`

**需要登录**：是

删除当前用户浏览历史中的一条或多条记录，记录不存在时幂等处理。

**请求体**

```json
{
  "noteIds": [42, 99, 108]
}
```

| 字段    | 类型           | 必填 | 说明             |
|---------|----------------|------|------------------|
| noteIds | array\<number\> | 是   | 要删除的笔记 ID 列表 |

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 清空浏览历史

`DELETE /api/notes/history/all`

**需要登录**：是

清空当前用户的全部浏览历史。

**请求体**：无

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 获取用户笔记列表

`GET /api/notes/user/{userId}`

返回指定用户的笔记列表，Cursor 分页。查看草稿需要登录且只能查看自己的草稿，否则返回业务错误。

**需要登录**：是

**Path 参数**

| 参数   | 类型   | 说明    |
|--------|--------|---------|
| userId | number | 用户 ID |

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                                    |
|--------|--------|------|---------------------------------------------------------|
| status | string | 否   | 筛选状态：`published`（默认）/ `draft`；`draft` 仅限本人 |
| cursor | number | 否   | 上一页最后一条笔记的 ID，不传则从最新开始                |
| size   | number | 否   | 每页条数，默认 10，最大 50                               |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 42,
        "title": "关注科协喵",
        "coverImage": "http://minio-host/lime/notes/uuid1.jpg",
        "likeCount": 128,
        "liked": false,
        "status": 1,
        "author": {
          "id": 7,
          "nickname": "taffy",
          "avatar": "http://minio-host/lime/avatars/uuid.jpg"
        }
      }
    ],
    "nextCursor": 38,
    "hasMore": true
  }
}
```

| 字段               | 类型    | 说明                                                     |
|--------------------|---------|----------------------------------------------------------|
| items[].status     | number  | 笔记状态：`0`=草稿，`1`=已发布                           |
| items[].liked      | boolean | 当前用户是否已点赞该笔记                                  |
| items[].viewCount  | number  | 浏览量；**仅本人查看自己的列表时返回**，非本人为 null 不输出 |
| nextCursor         | number  | 下一页游标，无更多数据时为 null                           |
| hasMore            | boolean | 是否还有更多数据                                          |

---

### 获取信息流（Feed）

`GET /api/notes/feed`

返回已发布笔记的卡片列表，采用 Cursor 分页，以笔记 ID 作为游标，每次返回最新的一批笔记。首次请求不传 `cursor`，后续翻页将上次响应的 `nextCursor` 作为下一次请求的 `cursor` 传入。

**需要登录**：是

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                    |
|--------|--------|------|-----------------------------------------|
| cursor | number | 否   | 上一页最后一条笔记的 ID，不传则从最新开始 |
| size   | number | 否   | 每页条数，默认 10，最大 50               |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 42,
        "title": "关注科协喵",
        "coverImage": "http://minio-host/lime/notes/uuid1.jpg",
        "likeCount": 128,
        "author": {
          "id": 7,
          "nickname": "taffy",
          "avatar": "http://minio-host/lime/avatars/uuid.jpg"
        }
      }
    ],
    "nextCursor": 38,
    "hasMore": true
  }
}
```

| 字段               | 类型    | 说明                                             |
|--------------------|---------|--------------------------------------------------|
| items              | array   | 笔记卡片列表                                      |
| items[].id         | number  | 笔记 ID                                           |
| items[].title      | string  | 笔记标题，可为 null                               |
| items[].coverImage | string  | 封面图（第一张图片 URL），无图片时为 null          |
| items[].likeCount  | number  | 点赞数                                            |
| items[].liked      | boolean | 当前用户是否已点赞该笔记                           |
| items[].author.id       | number | 作者用户 ID                                  |
| items[].author.nickname | string | 作者昵称                                     |
| items[].author.avatar   | string | 作者头像 URL，可为 null                      |
| nextCursor         | number  | 下一页游标（最后一条笔记的 ID），无更多数据时为 null |
| hasMore            | boolean | 是否还有更多数据                                  |

---

### 上传笔记图片

`POST /api/notes/images`

上传单张笔记图片至 MinIO，返回可用于发布笔记的图片 URL。发布笔记前先调用此接口上传全部图片，再将返回的 URL 列表一并提交发布接口。

**需要登录**：是

**Content-Type**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明                                      |
|------|------|------|-------------------------------------------|
| file | file | 是   | 图片文件，支持 JPG / PNG / WebP / GIF，最大 10MB |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "url": "http://minio-host/lime/notes/uuid.jpg"
  }
}
```

---

### 获取笔记详情

`GET /api/notes/{id}`

**需要登录**：是

**Path 参数**

| 参数 | 类型   | 说明    |
|------|--------|---------|
| id   | number | 笔记 ID |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 42,
    "title": "今天又水了一天代码",
    "content": "收到...",
    "status": 1,
    "images": [
      { "id": 1, "url": "http://minio-host/lime/notes/uuid1.jpg", "sortOrder": 0 }
    ],
    "likeCount": 128,
    "favCount": 36,
    "viewCount": 1024,
    "commentCount": 42,
    "liked": false,
    "favorited": true,
    "author": {
      "id": 7,
      "nickname": "taffy",
      "avatar": "http://minio-host/lime/avatars/uuid.jpg"
    },
    "createTime": "2026-07-20T10:30:00",
    "updateTime": "2026-07-20T10:30:00"
  }
}
```

| 字段         | 类型    | 说明                              |
|--------------|---------|-----------------------------------|
| liked        | boolean | 当前用户是否已点赞                 |
| favorited    | boolean | 当前用户是否已收藏                 |
| viewCount    | number  | 浏览量（每次请求该接口自动 +1）    |
| commentCount | number  | 评论总数（含回复）                 |

---

### 点赞 / 取消点赞

`POST /api/notes/{id}/like` — 点赞

`DELETE /api/notes/{id}/like` — 取消点赞

**需要登录**：是。两个接口均为幂等操作，重复调用不报错。

**请求体**：无

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 收藏 / 取消收藏

`POST /api/notes/{id}/favorite` — 收藏

`DELETE /api/notes/{id}/favorite` — 取消收藏

**需要登录**：是。两个接口均为幂等操作，重复调用不报错。

**请求体**：无

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---
---

### 发布图文笔记

`POST /api/notes`

提交笔记正文与已上传的图片 URL 列表，创建并发布笔记。标题和正文至少填写一项。

**需要登录**：是

**请求体**

```json
{
  "title": "今天又水了一天代码",
  "content": "收到...",
  "images": [
    { "url": "http://minio-host/lime/notes/uuid1.jpg", "sortOrder": 0 },
    { "url": "http://minio-host/lime/notes/uuid2.jpg", "sortOrder": 1 }
  ]
}
```

| 字段               | 类型   | 必填 | 说明                             |
|--------------------|--------|------|----------------------------------|
| status             | number | 否   | 0=草稿，1=已发布，默认 1         |
| title              | string | 否   | 笔记标题，最多 100 字符；与 content 至少填一项 |
| content            | string | 否   | 笔记正文，最多 1000 字符；与 title 至少填一项  |
| images             | array  | 是   | 图片列表，1 ~ 9 张               |
| images[].url       | string | 是   | 图片 URL（由上传接口返回）       |
| images[].sortOrder | number | 否   | 排列顺序，从 0 开始，默认 0      |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "userId": 42,
    "title": "今天又水了一天代码",
    "content": "收到...",
    "status": 1,
    "images": [
      { "id": 1, "url": "http://minio-host/lime/notes/uuid1.jpg", "sortOrder": 0 },
      { "id": 2, "url": "http://minio-host/lime/notes/uuid2.jpg", "sortOrder": 1 }
    ],
    "createTime": "2026-07-20T10:30:00",
    "updateTime": "2026-07-20T10:30:00"
  }
}
```

---

## 评论接口

> 以下接口均需登录（`Authorization: Bearer <accessToken>`）。

---

### 上传评论图片

`POST /api/comments/images`

先上传图片，获得 URL 后再发布评论。

**请求体**：`multipart/form-data`，字段名 `file`，支持 JPG/PNG/WebP/GIF，最大 10MB。

**响应**
```json
{ "code": 200, "message": "操作成功", "data": { "url": "https://..." } }
```

---

### 上传评论语音

`POST /api/comments/voices`

先上传语音，获得 URL 后再发布评论（时长由客户端在发布时传入）。

**请求体**：`multipart/form-data`，字段名 `file`，支持 mp3/m4a/aac/wav/ogg，最大 20MB。

**响应**
```json
{ "code": 200, "message": "操作成功", "data": { "url": "https://..." } }
```

---

### 发布评论

`POST /api/notes/{noteId}/comments`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 否 | 文字内容（含 emoji），与 images / voiceUrl 至少填一项 |
| images | string[] | 否 | 图片 URL 列表，最多 9 张；与 voiceUrl 互斥 |
| voiceUrl | string | 否 | 语音 URL；与 images 互斥 |
| voiceDuration | int | 否* | 语音时长（秒），传 voiceUrl 时必填 |

**响应**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "author": { "id": 10, "nickname": "用户A", "avatar": "https://..." },
    "content": "好棒！",
    "images": null,
    "voiceUrl": null,
    "voiceDuration": null,
    "likeCount": 0,
    "replyCount": 0,
    "liked": false,
    "isNoteAuthor": false,
    "createTime": "2026-08-03T10:00:00",
    "ipLocation": "湖南",
    "topReplies": null
  }
}
```

---

### 发布回复

`POST /api/notes/{noteId}/comments/{commentId}/replies`

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 否 | 文字内容（与 images / voiceUrl 至少填一项） |
| images | string[] | 否 | 图片 URL 列表，最多 9 张；与 voiceUrl 互斥 |
| voiceUrl | string | 否 | 语音 URL；与 images 互斥 |
| voiceDuration | int | 否* | 语音时长（秒），传 voiceUrl 时必填 |
| replyToUserId | long | 否 | 被回复的用户 ID（回复某条回复时传，用于显示"回复@xxx"） |

---

### 获取笔记评论列表

`GET /api/notes/{noteId}/comments`

**Query 参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| sort | string | hot | 排序：`hot`=热度降序，`time`=最新在前 |
| cursor | string | - | 游标；热度排序格式 `{hotScore}:{id}`，时间排序格式 `{id}`；首次不传 |
| size | int | 10 | 每页条数，最大 50 |

**响应**（每条评论带前 1 条回复预览，展开全部回复需调用回复列表接口）
```json
{
  "code": 200,
  "data": {
    "items": [
      {
        "id": 1,
        "author": { "id": 10, "nickname": "用户A", "avatar": "https://..." },
        "content": "很棒！",
        "images": ["https://..."],
        "voiceUrl": null,
        "voiceDuration": null,
        "likeCount": 42,
        "replyCount": 8,
        "liked": false,
        "isNoteAuthor": true,
        "createTime": "2026-08-03T10:00:00",
        "topReplies": [
          {
            "id": 5,
            "author": { "id": 20, "nickname": "用户B", "avatar": "https://..." },
            "replyToUserId": 10,
            "replyToNickname": "用户A",
            "content": "谢谢",
            "images": null,
            "voiceUrl": null,
            "voiceDuration": null,
            "likeCount": 3,
            "liked": false,
            "isNoteAuthor": false,
            "createTime": "2026-08-03T10:05:00",
            "ipLocation": "广东"
          }
        ]
      }
    ],
    "nextCursor": "42:1",
    "hasMore": true
  }
}
```

---

### 获取回复列表

`GET /api/comments/{commentId}/replies`

时间正序（最早在底部），用于"查看全部回复"。

**Query 参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| cursor | long | - | 游标（上一页最后一条回复的 id），首次不传 |
| size | int | 20 | 每页条数，最大 50 |

---

### 点赞评论 / 回复

`POST /api/comments/{commentId}/like`

幂等，重复点赞直接返回成功。

---

### 取消点赞

`DELETE /api/comments/{commentId}/like`

幂等，未点赞时直接返回成功。

---

### 删除评论 / 回复

`DELETE /api/comments/{commentId}`

评论者本人或笔记作者均可删除。一级评论被删后，其下回复仍保留（逻辑删除）。
