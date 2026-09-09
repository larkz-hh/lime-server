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
    "role": "USER",
    "likePrivate": false,
    "favPrivate": false,
    "followingCount": 12,
    "followerCount": 34,
    "isFollowing": true
  }
}
```

> 不返回 `email` 字段（隐私保护）。

| 字段            | 类型    | 说明                                        |
|-----------------|---------|---------------------------------------------|
| id              | number  | 用户 ID                                     |
| nickname        | string  | 昵称                                        |
| handle          | string  | 唯一标识符                                   |
| bio             | string  | 个人简介，可为 null                          |
| avatar          | string  | 头像图片 URL，可为 null                      |
| backgroundImage | string  | 个人主页背景图 URL，可为 null                |
| gender          | number  | 性别：0=未设置，1=男，2=女，可为 null        |
| birthday        | string  | 生日，格式 `yyyy-MM-dd`，可为 null           |
| region          | string  | 地区，可为 null                              |
| role            | string  | 角色，当前固定为 `USER`                      |
| likePrivate     | boolean | 点赞列表是否私密：false=公开，true=私密      |
| favPrivate      | boolean | 收藏列表是否私密：false=公开，true=私密      |
| followingCount  | number  | 关注数                                      |
| followerCount   | number  | 粉丝数                                      |
| noteCount       | number  | 已发布笔记数                                |
| totalLikeCount  | number  | 笔记收到的总点赞数（含自己给自己点赞）      |
| totalFavCount   | number  | 笔记收到的总收藏数（含自己给自己收藏）      |
| isFollowing     | boolean | 当前登录用户是否已关注该用户（看自己为 null）|
| isFollowedBack  | boolean | 该用户是否已关注当前登录用户（配合 isFollowing 判断互关，看自己为 null）|

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
    "role": "USER",
    "likePrivate": false,
    "favPrivate": false,
    "followingCount": 12,
    "followerCount": 34
  }
}
```

| 字段            | 类型    | 说明                                        |
|-----------------|---------|---------------------------------------------|
| id              | number  | 用户 ID                                     |
| email           | string  | 登录邮箱                                    |
| nickname        | string  | 昵称                                        |
| handle          | string  | 唯一标识符（类似用户名）                     |
| bio             | string  | 个人简介，可为 null                          |
| avatar          | string  | 头像图片 URL，可为 null                      |
| backgroundImage | string  | 个人主页背景图 URL，可为 null                |
| gender          | number  | 性别：0=未设置，1=男，2=女，可为 null        |
| birthday        | string  | 生日，格式 `yyyy-MM-dd`，可为 null           |
| region          | string  | 地区，可为 null                              |
| role            | string  | 角色，当前固定为 `USER`                      |
| likePrivate     | boolean | 点赞列表是否私密：false=公开，true=私密      |
| favPrivate      | boolean | 收藏列表是否私密：false=公开，true=私密      |
| followingCount  | number  | 关注数                                      |
| followerCount   | number  | 粉丝数                                      |
| noteCount       | number  | 已发布笔记数                                |
| totalLikeCount  | number  | 笔记收到的总点赞数（含自己给自己点赞）      |
| totalFavCount   | number  | 笔记收到的总收藏数（含自己给自己收藏）      |
| isFollowing     | boolean | 当前登录用户是否已关注该用户（看自己为 null）|
| isFollowedBack  | boolean | 该用户是否已关注当前登录用户（配合 isFollowing 判断互关，看自己为 null）|

---

### 修改个人资料

`PUT /api/user/me`

所有字段均为可选，只传需要修改的字段即可。`bio` 和 `region` 传空字符串 `""` 可清空对应字段；`likePrivate`、`favPrivate` 传 `true`/`false` 可开关列表隐私。

**请求体**

```json
{
  "nickname": "新昵称",
  "bio": "新的个人简介",
  "gender": 1,
  "birthday": "2000-01-01",
  "region": "上海",
  "likePrivate": true,
  "favPrivate": false
}
```

| 字段        | 类型    | 必填 | 说明                                  |
|-------------|---------|------|---------------------------------------|
| nickname    | string  | 否   | 昵称，1-20 个字符                      |
| bio         | string  | 否   | 简介，最多 200 个字符，传 `""` 可清空  |
| gender      | number  | 否   | 性别：0=未设置，1=男，2=女             |
| birthday    | string  | 否   | 生日，格式 `yyyy-MM-dd`               |
| region      | string  | 否   | 地区，最多 50 个字符，传 `""` 可清空   |
| likePrivate | boolean | 否   | 点赞列表是否私密：false=公开，true=私密 |
| favPrivate  | boolean | 否   | 收藏列表是否私密：false=公开，true=私密 |

**响应**：返回更新后的用户信息，结构同「获取当前用户信息」。

> 开启隐私后，他人访问 `GET /api/notes/user/{userId}/likes`（或 `/favorites`）时返回业务错误「该用户已开启点赞列表隐私」（「该用户已开启收藏列表隐私」）；本人查看不受影响。

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
        "noteType": 1,
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
| items[].coverImage | string  | 封面图（图文=第一张图片 URL；视频=视频封面 URL），无图时为 null |
| items[].coverWidth / coverHeight | number | 封面宽高（客户端上报，瀑布流卡片按比例布局用）；历史数据可能为 null |
| items[].likeCount  | number  | 点赞数                                            |
| items[].liked      | boolean | 当前用户是否已点赞该笔记                           |
| items[].noteType   | number  | 笔记类型：1=图文，2=视频                           |
| items[].video      | object  | 视频摘要（仅视频笔记输出；图文笔记无该字段）        |
| items[].video.durationMs | number | 视频时长（毫秒），用于卡片时长角标             |
| items[].video.width / height | number | 视频宽高，用于横屏判断                    |
| items[].video.orientation | string | `PORTRAIT` / `LANDSCAPE`（宽 > 高为横屏）   |
| items[].video.playUrl     | string | 播放地址（直放模式为原始 mp4）              |
| items[].author.id       | number | 作者用户 ID                                  |
| items[].author.nickname | string | 作者昵称                                     |
| items[].author.avatar   | string | 作者头像 URL，可为 null                      |
| items[].author.isFollowing | boolean | 当前用户是否已关注该作者                  |
| items[].author.isFollowedBack | boolean | 该作者是否已关注当前用户（配合 isFollowing 判断互关） |
| nextCursor         | number  | 下一页游标（最后一条笔记的 ID），无更多数据时为 null |
| hasMore            | boolean | 是否还有更多数据                                  |

> 用户列表 / 点赞 / 收藏 / 浏览历史 / 搜索等接口返回的卡片结构与 feed 一致，视频笔记同样携带 `noteType` 与 `video` 字段。
>
> 笔记详情（`GET /api/notes/{id}`）返回的 `author` 对象同样包含 `isFollowing` / `isFollowedBack`（看自己的笔记时为 null）。

---

### 获取关注动态

`GET /api/notes/following-feed`

返回**当前登录用户关注的人**发布的笔记，Cursor 分页，按笔记 ID 倒序（最新在前）。卡片结构同「获取信息流」，作者均为已关注的人（`author.isFollowing` 恒为 true，`isFollowedBack` 标记是否互关）。

**需要登录**：是

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                |
|--------|--------|------|-------------------------------------|
| cursor | number | 否   | 上一页最后一条笔记 ID，首次传空     |
| size   | number | 否   | 每页条数，默认 10，最大 50          |

**响应**：结构同「获取信息流」。

---

### 获取视频流（Video Feed）

`GET /api/notes/video-feed`

返回已发布视频笔记列表，供独立视频 Tab 与详情页上滑「下一条视频」共用。Cursor 分页，按笔记 ID 倒序（最新在前）。一次返回多条即天然的「预加载下一条」数据源。

**需要登录**：是

**Query 参数**

| 参数       | 类型   | 必填 | 说明                                              |
|------------|--------|------|---------------------------------------------------|
| cursor     | number | 否   | 上一页最后一条视频笔记的 ID，不传则从最新开始      |
| seedNoteId | number | 否   | 起始锚点：返回 ID ≤ 它的视频（**含自身**），用于进入视频页时首屏定位与上滑保持顺序 |
| orientation | string | 否  | 横竖屏过滤：`landscape`（仅横屏，宽>高）/ `portrait`（仅竖屏）；不传则不限。全屏横屏会话时传 `landscape` 保证滑到的下一条都是横屏 |
| size       | number | 否   | 每页条数，默认 10，最大 20                          |

**响应**：结构同「获取信息流」，items 全部为视频笔记（`noteType=2` 且携带完整 `video` 对象）。

> **横屏全屏会话用法（推荐流场景）**：横屏视频进全屏后，用 `orientation=landscape&seedNoteId=当前视频id` 拉独立的横屏流；退出全屏时由 App 把会话中播过的横屏视频插回主队列入口之后。个人主页等「有限列表」场景无需此参数——App 在已拉取的列表内本地过滤即可。

---

### 发弹幕

`POST /api/notes/{noteId}/danmaku`

发布一条弹幕（仅视频笔记支持）。时间点 `videoTimeMs` 由客户端按当前播放位置传入，需落在视频时长范围内。

**需要登录**：是

**Path 参数**

| 参数   | 类型   | 说明        |
|--------|--------|-------------|
| noteId | number | 视频笔记 ID |

**请求体**

```json
{ "content": "前方高能", "videoTimeMs": 12500, "color": "#FFFFFF" }
```

| 字段        | 类型   | 必填 | 说明                                          |
|-------------|--------|------|-----------------------------------------------|
| content     | string | 是   | 弹幕文字，1-200 字符                            |
| videoTimeMs | number | 是   | 弹幕出现时间点（毫秒，相对视频开头），≥ 0       |
| color       | string | 否   | 弹幕颜色（#RRGGBB），不传则客户端用默认白色      |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "content": "前方高能",
    "videoTimeMs": 12500,
    "color": "#FFFFFF",
    "author": { "id": 10, "nickname": "用户A", "avatar": "https://..." },
    "createTime": "2026-08-10T10:00:00"
  }
}
```

---

### 拉取弹幕列表

`GET /api/notes/{noteId}/danmaku`

返回视频笔记的全部弹幕，按出现时间点升序（同时间点按 id 升序）。MVP 全量返回，单视频上限 2000 条。

**需要登录**：是

**Path 参数**

| 参数   | 类型   | 说明        |
|--------|--------|-------------|
| noteId | number | 视频笔记 ID |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      { "id": 1, "content": "前方高能", "videoTimeMs": 12500, "color": "#FFFFFF",
        "author": { "id": 10, "nickname": "用户A", "avatar": "https://..." },
        "createTime": "2026-08-10T10:00:00" }
    ],
    "count": 1
  }
}
```

---

### 删除弹幕

`DELETE /api/notes/{noteId}/danmaku/{danmakuId}`

逻辑删除，幂等。仅弹幕发送者本人或视频笔记作者可删，否则返回业务错误。

**需要登录**：是

**请求体**：无

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

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

### 上传笔记视频

`POST /api/notes/videos`

上传单个视频文件至 MinIO，返回可用于发布视频笔记的视频 URL。发布视频笔记前先调用此接口上传视频，再将返回的 URL 与客户端采集的元数据（时长/宽高）一并提交发布接口。

**需要登录**：是

**Content-Type**：`multipart/form-data`

| 字段 | 类型 | 必填 | 说明                                   |
|------|------|------|----------------------------------------|
| file | file | 是   | 视频文件，仅支持 mp4，最大 200MB，建议 ≤ 10 分钟 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "url": "http://minio-host/lime/videos/uuid.mp4"
  }
}
```

---

### 获取笔记详情

`GET /api/notes/{id}`

**需要登录**：是

> 已发布笔记人人可看；草稿（status=0）仅作者本人可看，供草稿箱继续编辑。草稿不累计浏览量、不写浏览历史。

**Path 参数**

| 参数 | 类型   | 说明    |
|------|--------|---------|
| id   | number | 笔记 ID |

**Query 参数**

| 参数   | 类型    | 必填 | 说明                                                          |
|--------|---------|------|---------------------------------------------------------------|
| noView | boolean | 否   | 传 `true` 时本次请求**不累计浏览量、不写浏览历史**（视频流补水 hydrate 场景使用）；默认 `false`，行为与历史一致 |

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
    "noteType": 1,
    "images": [
      { "id": 1, "url": "http://minio-host/lime/notes/uuid1.jpg", "sortOrder": 0 }
    ],
    "likeCount": 128,
    "favCount": 36,
    "viewCount": 1024,
    "commentCount": 42,
    "danmakuCount": 0,
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

**视频笔记（noteType=2）额外返回 `video` 字段**（图文笔记不输出该字段）：

```json
{
  "noteType": 2,
  "images": [],
  "danmakuCount": 12,
  "video": {
    "durationMs": 15000,
    "width": 1080,
    "height": 1920,
    "orientation": "PORTRAIT",
    "playUrl": "http://minio-host/lime/videos/uuid.mp4",
    "coverUrl": "http://minio-host/lime/notes/cover-uuid.jpg"
  }
}
```

| 字段                  | 类型    | 说明                                                |
|-----------------------|---------|-----------------------------------------------------|
| video.durationMs      | number  | 视频时长（毫秒）                                     |
| video.width/height    | number  | 视频宽高（客户端上报），用于横屏判断与卡片布局         |
| video.orientation     | string  | 横竖屏：`PORTRAIT` / `LANDSCAPE`（宽 > 高为横屏）     |
| video.playUrl         | string  | 播放地址（直放模式为原始 mp4；未来转码模式切换为 HLS，字段名不变） |
| video.coverUrl        | string  | 封面地址，可为 null                                  |
| danmakuCount          | number  | 弹幕数（仅视频笔记有意义，独立于评论数）              |

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

### 编辑笔记

`PUT /api/notes/{id}`

仅作者本人，请求体同「发布笔记」。编辑行为按目标笔记状态和 `status` 字段区分：

| 目标笔记 | 请求 status | 行为 |
|----------|-------------|------|
| 已发布(1) | 0（存草稿） | **新建草稿副本**（线上版不动），返回草稿 id |
| 已发布(1) | 1（发布） | 就地覆盖线上版，id/计数/评论不变 |
| 草稿(0) | 0（存草稿） | 就地更新草稿 |
| 草稿(0) | 1（发布） | 有来源笔记→覆盖原笔记并删草稿；全新草稿→就地发布 |

编辑后 `updateTime` 更新，点赞/收藏/评论/浏览等计数不变。

**需要登录**：是

**响应**：同「发布笔记」返回最新笔记（存草稿时返回的是草稿 id）。

---

### 删除笔记

`DELETE /api/notes/{id}`

仅作者本人，逻辑删除，草稿和已发布均可删。删除后不再出现在任何列表与详情。

**需要登录**：是

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

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
    { "url": "http://minio-host/lime/notes/uuid1.jpg", "width": 1080, "height": 1440, "sortOrder": 0 },
    { "url": "http://minio-host/lime/notes/uuid2.jpg", "width": 1080, "height": 1080, "sortOrder": 1 }
  ]
}
```

| 字段               | 类型   | 必填 | 说明                             |
|--------------------|--------|------|----------------------------------|
| status             | number | 否   | 0=草稿，1=已发布，默认 1         |
| noteType           | number | 否   | 笔记类型：1=图文（默认），2=视频；传 2 走视频发布逻辑 |
| title              | string | 否   | 笔记标题，最多 100 字符；与 content 至少填一项 |
| content            | string | 否   | 笔记正文，最多 1000 字符；与 title 至少填一项  |
| images             | array  | 是   | 图片列表，1 ~ 9 张               |
| images[].url       | string | 是   | 图片 URL（由上传接口返回）       |
| images[].width     | number | 否   | 图片宽（客户端读取本地图片后上报）；仅封面（第一张）建议填写，其余图片可省略 |
| images[].height    | number | 否   | 图片高（客户端读取本地图片后上报）；仅封面（第一张）建议填写，其余图片可省略 |
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
    "noteType": 1,
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

### 发布视频笔记

`POST /api/notes`（`noteType = 2`）

提交已上传的视频 URL 与客户端采集的元数据，创建并发布视频笔记。标题/正文可选（与图文不同，可全空）；封面 `coverUrl` **必填**，由客户端上传（用户选图或自动截第一帧，均走「上传笔记图片」接口）。

**需要登录**：是

**请求体**

```json
{
  "status": 1,
  "noteType": 2,
  "title": "周末骑行 vlog",
  "content": "第一次剪视频",
  "video": {
    "url": "http://minio-host/lime/videos/uuid.mp4",
    "durationMs": 15000,
    "width": 1080,
    "height": 1920,
    "coverUrl": "http://minio-host/lime/notes/cover-uuid.jpg",
    "coverWidth": 1080,
    "coverHeight": 1920
  }
}
```

| 字段              | 类型   | 必填 | 说明                                                |
|-------------------|--------|------|-----------------------------------------------------|
| status            | number | 否   | 0=草稿，1=已发布，默认 1                             |
| noteType          | number | 是   | 固定传 2（视频）                                      |
| title             | string | 否   | 笔记标题，最多 100 字符                               |
| content           | string | 否   | 笔记正文，最多 1000 字符                              |
| video             | object | 是   | 视频信息                                             |
| video.url         | string | 是   | 视频 URL（由上传视频接口返回）                         |
| video.durationMs  | number | 是   | 视频时长（毫秒，客户端 MediaMetadataRetriever 采集）   |
| video.width       | number | 是   | 视频宽（客户端采集，用于横屏判断）                     |
| video.height      | number | 是   | 视频高（客户端采集）                                   |
| video.coverUrl    | string | 是   | 封面 URL（客户端上传或截帧），为空时报「视频封面不能为空」 |
| video.coverWidth  | number | 否   | 封面图宽（客户端读取后上报，瀑布流布局用）；建议填写     |
| video.coverHeight | number | 否   | 封面图高（客户端读取后上报，瀑布流布局用）；建议填写     |

**响应**：结构同「发布图文笔记」，`noteType` 为 2，额外含 `video` 对象（durationMs/width/height/orientation/playUrl/coverUrl），`images` 为空数组。

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

**响应**
```json
{
  "code": 200,
  "data": {
    "id": 5,
    "author": { "id": 20, "nickname": "用户B", "avatar": "https://..." },
    "replyToUserId": 10,
    "replyToNickname": "用户A",
    "content": "谢谢！",
    "images": ["https://..."],
    "voiceUrl": null,
    "voiceDuration": null,
    "likeCount": 0,
    "liked": false,
    "isNoteAuthor": false,
    "createTime": "2026-08-09T10:05:00",
    "ipLocation": "广东"
  }
}
```

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

**响应**
```json
{
  "code": 200,
  "data": {
    "items": [
      {
        "id": 5,
        "author": { "id": 20, "nickname": "用户B", "avatar": "https://..." },
        "replyToUserId": 10,
        "replyToNickname": "用户A",
        "content": "谢谢",
        "images": ["https://..."],
        "voiceUrl": null,
        "voiceDuration": null,
        "likeCount": 3,
        "liked": false,
        "isNoteAuthor": false,
        "createTime": "2026-08-03T10:05:00",
        "ipLocation": "广东"
      }
    ],
    "nextCursor": 5,
    "hasMore": false
  }
}
```

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

评论者本人或笔记作者均可删除。逻辑删除：删一级评论会连带删除其下所有回复；删回复则父评论 `reply_count` 同步减一。笔记总评论数（`comment_count`）始终同步减少。

---

## 搜索接口 `/api/search`

> 以下接口均需登录（`Authorization: Bearer <accessToken>`）。

---

### 搜索笔记

`GET /api/search/notes`

按关键词搜索已发布笔记，Cursor 分页。匹配范围：笔记标题、笔记正文、作者昵称 / handle（命中作者则召回其已发布笔记，统一返回笔记卡片）。关键词为整串匹配，不做分词。`sort`（排序依据）、`within`（发布时间范围）与 `type`（笔记类型）可自由组合。视频笔记与图文笔记混排在结果中，卡片携带 `noteType` 与 `video` 字段。

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 是 | 搜索关键词，1-50 个字符；全空白报错 |
| sort | string | 否 | 排序：`composite`（综合，默认，相关度 + 热度）/ `latest`（最新）/ `likes`（最多赞）/ `comments`（最多评论）/ `favs`（最多收藏） |
| within | string | 否 | 发布时间范围：`all`（不限，默认）/ `day`（一天内）/ `week`（一周内）/ `halfYear`（半年内） |
| type | string | 否 | 笔记类型过滤：`all`（全部，默认）/ `image`（图文）/ `video`（视频） |
| cursor | string | 否 | 上一页游标（`nextCursor` 值），格式 `{score}:{createTimeMs}:{id}`，首次不传 |
| size | number | 否 | 每页条数，默认 10，最大 50 |

**响应**：结构同「获取信息流」，`nextCursor` 为字符串。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 42,
        "title": "关注科协",
        "coverImage": "http://minio-host/lime/notes/uuid1.jpg",
        "likeCount": 128,
        "liked": false,
        "author": {
          "id": 7,
          "nickname": "taffy",
          "avatar": "http://minio-host/lime/avatars/uuid.jpg"
        }
      }
    ],
    "nextCursor": "10350:1754094600000:42",
    "hasMore": true
  }
}
```

---

### 搜索用户

`GET /api/search/users`

按昵称 / handle 搜索用户，返回用户卡片，Cursor 分页。匹配度优先排序：精确匹配 > 前缀匹配 > 包含匹配，同精度按已发布笔记数降序。结果中的 `isMe` 标记当前登录用户本人。

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 是 | 搜索关键词，1-50 个字符；全空白报错 |
| cursor | string | 否 | 上一页游标（`nextCursor` 值），格式 `{matchRank}:{noteCount}:{id}`，首次不传 |
| size | number | 否 | 每页条数，默认 10，最大 50 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 7,
        "nickname": "taffy",
        "handle": "user_xxxxxxxx",
        "avatar": "http://minio-host/lime/avatars/uuid.jpg",
        "isMe": false,
        "isFollowing": true,
        "isFollowedBack": true,
        "followerCount": 34
      }
    ],
    "nextCursor": "3:12:7",
    "hasMore": true
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 用户 ID |
| nickname | string | 昵称 |
| handle | string | 唯一标识符 |
| avatar | string | 头像 URL，可为 null |
| isMe | boolean | 是否为当前登录用户本人 |
| isFollowing | boolean | 当前登录用户是否已关注该用户（本人为 null） |
| isFollowedBack | boolean | 该用户是否已关注当前登录用户（配合 isFollowing 判断互关，本人为 null） |
| followerCount | number | 粉丝数（实时统计，本页一次批量查询回填） |
| nextCursor | string | 下一页游标，无更多数据时为 null |
| hasMore | boolean | 是否还有下一页 |

---

### 搜索联想

`GET /api/search/suggest`

输入前缀时实时返回提示关键词。提示词来源：已发布笔记标题（前缀匹配，按热度取）+ 当日热搜词，已去重，不含用户。

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| q | string | 否 | 输入前缀；为空返回空列表 |
| size | number | 否 | 返回条数，默认 10，最大 20 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": ["北京美食攻略", "北京美食探店"]
}
```

---

### 热搜榜

`GET /api/search/hot`

返回当日热搜关键词，按点击次数降序。点击次数由「上报搜索」接口累计（Redis 按天轮转，跨天自动重置）。

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| size | number | 否 | 返回条数，默认 10，最大 50 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { "keyword": "北京美食", "count": 128 },
    { "keyword": "猫", "count": 96 }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| keyword | string | 热搜词 |
| count | number | 当日点击次数 |

---

### 上报搜索

`POST /api/search/report`

前端在用户确认搜索时（回车 / 点击联想词）调用一次，用于热搜统计。翻页请求不调用，避免重复计数。

**请求体**

```json
{
  "keyword": "北京美食"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 是 | 被搜索的关键词，1-50 个字符 |

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

## AI 接口 `/api/ai`

> 以下接口均需登录。流式接口返回 `text/event-stream`（SSE），每条事件的 data 为 JSON 字符串。

### SSE 事件格式

写作辅助、聊天两个流式接口逐条推送以下事件：

| type | 说明 | data 字段 |
|------|------|-----------|
| delta | 增量文本 | content：本轮增量 |
| tool | 工具调用开始 | name：工具名（get_weather 查天气 / search_web 联网搜索） |
| done | 生成结束 | 各接口不同，见下文 |
| error | 出错 | message：可展示的错误提示 |

### 内置模型列表

`GET /api/ai/models`

App 模型选择器用，当前仅提供支持文字+图片的视觉模型。流式 `done` 事件的 `model` 字段返回实际使用的模型。

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { "name": "deepseek-v4-flash-vision-exp", "displayName": "DeepSeek V4 Flash Vision", "description": "支持图片理解（识图、看图写文案）", "supportsVision": true },
    { "name": "dots3-note-prev", "displayName": "Dots 3 Note", "description": "小红书 Dots 模型，支持文字与图片理解", "supportsVision": true },
    { "name": "kimi-k2.6", "displayName": "Kimi K2.6", "description": "Moonshot Kimi 模型，支持文字与图片理解", "supportsVision": true }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 模型名，传给下方接口的 model 字段 |
| displayName | string | 展示名 |
| description | string | 说明 |
| supportsVision | boolean | 是否支持图片输入 |

---

### AI 翻译

`POST /api/ai/translate`

非流式接口，短文本一次返回（用于评论翻译等场景）。限流：每用户每分钟 10 次、每天 100 次。

**请求体**

```json
{
  "text": "This is so beautiful!",
  "targetLang": "中文",
  "sourceLang": "英语"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | string | 是 | 待翻译文本，≤1000 字 |
| targetLang | string | 是 | 目标语言（如 中文/英语/日语/韩语/法语） |
| sourceLang | string | 否 | 源语言，不传则自动检测 |
| model | string | 否 | 指定模型，不传用默认文本模型 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "translatedText": "太美了！",
    "sourceLang": "英语",
    "targetLang": "中文"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| translatedText | string | 译文 |
| sourceLang | string | 源语言（请求未指定时为 null，指定时原样返回） |
| targetLang | string | 目标语言 |

---

### 写作辅助 / 看图写文案

`POST /api/ai/write/assist`

SSE 流式返回。content 与 imageUrls 至少提供一项；action=caption 时必须携带 imageUrls；polish/continue/condense 模式下 content 少于 5 个字、title 无图时少于 2 个字时返回 `内容太短了…` 提示（title 带图时可纯看图起标题，不限制文字长度）。限流：每用户每分钟 5 次、每天 50 次。

**请求体**

```json
{
  "content": "今天去了故宫",
  "action": "polish",
  "imageUrls": ["http://oss.example.com/lime-bucket/xxx.jpg"],
  "model": "deepseek-v4-flash-vision-exp"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| action | string | 是 | polish=润色 / continue=续写 / title=起标题 / condense=精简 / caption=看图写文案 |
| content | string | 否 | 待处理文字，最多 2000 字（caption 时可为空） |
| imageUrls | string[] | 否 | 图片 URL，最多 4 张；带图时自动使用视觉模型 |
| model | string | 否 | 指定模型（见模型列表接口），不传用默认；带图时自动切换视觉模型，即使指定了文本模型也会自动升级 |

**SSE 事件示例**

```
data: {"type":"delta","content":"今天"}
data: {"type":"delta","content":"去了故宫"}
data: {"type":"done","content":"今天去了故宫，红墙金瓦……"}
```

done 事件字段：`content` 为生成全文，`model` 为实际使用的模型（带图时可能已自动切换为视觉模型）。

---

### AI 聊天

`POST /api/ai/chat`

SSE 流式返回。conversationId、messageClientId 由客户端生成（UUID）：首次使用新 conversationId 即新建会话；messageClientId 为消息幂等键（断线重试不重复落库）。带 imageUrls 时自动使用视觉模型。同一会话携带最近 20 条历史消息作为上下文。限流：每用户每分钟 5 次、每天 50 次。

**请求体**

```json
{
  "conversationId": "会话id（客户端生成UUID，新值=新会话）",
  "messageClientId": "消息幂等键（客户端生成UUID）",
  "message": "这篇笔记讲了什么？",
  "imageUrls": ["http://oss.example.com/lime-bucket/outfit.jpg"],
  "noteId": 66,
  "search": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| conversationId | string | 是 | 客户端生成的会话 id（UUID），首次使用新值即新建会话 |
| messageClientId | string | 是 | 客户端生成的消息幂等键（UUID），用于去重与断线续传 |
| message | string | 是 | 消息内容，最多 2000 字 |
| imageUrls | string[] | 否 | 图片 URL，最多 4 张；带图时自动使用视觉模型 |
| noteId | number | 否 | 引用提问的笔记 id（仅已发布笔记），见下方「引用笔记提问」 |
| model | string | 否 | 指定模型（见模型列表接口） |
| search | boolean | 否 | 是否启用联网搜索，默认 true（开启）；false 关闭 |

**引用笔记提问（noteId）**

传 noteId 后，后端检索该笔记的以下内容加入上下文，模型基于笔记信息回答，且可在该会话中继续追问：

- 标题、正文（截断 2000 字）
- 图片（图文笔记前 4 张 / 视频笔记封面），带图自动切换视觉模型
- 点赞/收藏/浏览/评论数
- 精选评论（热度前 20 条一级评论文字）

笔记内容在发送当轮检索一次并存入消息快照，后续轮次复用（笔记被删后历史对话仍可引用）。笔记不存在或未发布返回 `笔记不存在或未发布`。

**会话记忆与滚动摘要压缩**

- 每轮请求把会话最近 20 条消息原文 + 系统提示重发给模型（多轮记忆）；
- 当「未被压缩的消息」超过 30 条时，自动把最早的一批（最多 10 条，压缩后至少保留 20 条原文）用文本模型压缩成一段 200 字内的摘要，存到会话上；后续轮次上下文 = 系统提示 + 历史摘要 + 最近 20 条原文；
- 被压缩的消息仍保留在历史记录中（可回显、可删除），只是不再原文参与上下文；
- 清空会话消息时，摘要一并清空。

**SSE 事件示例**

```
data: {"type":"delta","content":"这身"}
data: {"type":"delta","content":"搭配不错"}
data: {"type":"tool","name":"get_weather"}
data: {"type":"done","conversationId":"uuid-会话id","userMessageId":101,"assistantMessageId":102}
```

done 事件字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| conversationId | number | 会话 id（新会话时为新建的 id） |
| userMessageId | number | 本次用户消息 id |
| assistantMessageId | number | 本次助手回复 id |
| model | string | 实际使用的模型名（带图时可能已自动切换为视觉模型） |

---

### 我的会话列表

`GET /api/ai/conversations`

游标分页，按会话 id 倒序。

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cursor | string | 否 | 上一页返回的 nextCursor |
| size | number | 否 | 每页条数，默认 10，最大 50 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      { "id": "uuid-会话id", "title": "帮我看看这身搭配怎么样", "createTime": "2026-08-25T10:00:00", "updateTime": "2026-08-25T10:05:00" }
    ],
    "nextCursor": "1",
    "hasMore": true
  }
}
```

---

### 会话历史消息

`GET /api/ai/conversations/{conversationId}/messages`

仅会话所属用户可访问，消息按时间正序。

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { "id": 101, "role": "user", "content": "帮我看看这身搭配怎么样", "images": ["http://oss.example.com/lime-bucket/outfit.jpg"], "createTime": "2026-08-25T10:00:00" },
    { "id": 102, "role": "assistant", "content": "这身搭配不错……", "images": null, "createTime": "2026-08-25T10:00:15" }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | number | 消息内部 id |
| clientId | string | 客户端消息幂等键（UUID），断线续传对应用 |
| role | string | user / assistant |
| content | string | 消息文本（assistant 生成中时是部分内容，见 status） |
| status | string | streaming=生成中 / done=完成 / failed=失败 / stopped=被打断 |
| images | string[] | 消息携带的图片 URL（可为 null） |
| noteId | number | 消息引用提问的笔记 id（App 端渲染引用卡片用，可为 null） |

---

### 删除会话

`DELETE /api/ai/conversations/{conversationId}`

仅会话所属用户可操作，删除后会话及其消息不可恢复。

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 删除会话中的单条消息

`DELETE /api/ai/conversations/{conversationId}/messages/{messageId}`

仅会话所属用户可操作，删除后不可恢复。删除的消息不再参与后续聊天的上下文。

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 清空会话消息

`DELETE /api/ai/conversations/{conversationId}/messages`

仅会话所属用户可操作，清空该会话的全部消息但保留会话本身。清空后继续在该会话发消息即为全新对话。

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

## 关注接口 `/api/user`

### 关注用户

`POST /api/user/{userId}/follow`

**需要登录**：是

关注成功后：关注者「关注数」+1，被关注者「粉丝数」+1，并向被关注者发一条关注通知。重复关注幂等返回。

**Path 参数**

| 参数   | 类型   | 说明            |
|--------|--------|-----------------|
| userId | number | 被关注的用户 ID |

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 取消关注

`DELETE /api/user/{userId}/follow`

**需要登录**：是

取消后关注数/粉丝数相应 -1，幂等返回。

**Path 参数**

| 参数   | 类型   | 说明                |
|--------|--------|---------------------|
| userId | number | 被取消关注的用户 ID |

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 关注列表

`GET /api/user/{userId}/following`

**需要登录**：是

游标分页，按关注时间倒序，返回用户简要信息。每条带当前登录用户与该用户的关注状态（`isFollowing` / `isFollowedBack`），App 据此渲染关注/回关/互关。

**Path 参数**

| 参数   | 类型   | 说明    |
|--------|--------|---------|
| userId | number | 用户 ID |

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                |
|--------|--------|------|-------------------------------------|
| cursor | number | 否   | 游标，上一页最后一条的 id，首次传空 |
| size   | number | 否   | 每页条数，默认 10，最大 50          |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 8,
        "nickname": "taffy",
        "handle": "user_xxx",
        "avatar": "http://localhost:9000/lime-bucket/avatars/uuid.jpg",
        "bio": "简介",
        "isFollowing": true,
        "isFollowedBack": true
      }
    ],
    "nextCursor": "5",
    "hasMore": true
  }
}
```

> `isFollowing`：当前登录用户是否已关注该用户；`isFollowedBack`：该用户是否已关注当前登录用户；两者都为 true 即互相关注。

---

### 粉丝列表

`GET /api/user/{userId}/followers`

**需要登录**：是

参数与响应结构同「关注列表」。

---

## 站内通知接口 `/api/notifications`

> 点赞笔记、点赞评论/回复、收藏笔记、评论、回复、关注都会产生站内通知；事件经 RocketMQ 异步解耦生成通知，信箱红点取未读数接口，实时变化由 SSE 推送。

### 通知列表

`GET /api/notifications`

**需要登录**：是

游标分页，按时间倒序，返回当前登录用户的通知。可按 `type` 过滤（分三个信箱）。

**Query 参数**

| 参数   | 类型   | 必填 | 说明                                   |
|--------|--------|------|----------------------------------------|
| cursor | number | 否   | 游标，上一页最后一条通知 id，首次传空 |
| size   | number | 否   | 每页条数，默认 10，最大 50             |
| type   | string | 否   | 按通知类型过滤，多个用逗号分隔；不传返回全部 |

`type` 与三个信箱的对应关系：

| 信箱 | type 值 | 含义 |
|------|---------|------|
| 赞和收藏 | `1,2,6` | 点赞笔记 + 收藏 + 点赞评论/回复 |
| 关注 | `5` | 新增关注 |
| 评论与回复 | `3,4` | 评论 + 回复 |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "items": [
      {
        "id": 100,
        "type": 1,
        "noteId": 20,
        "commentId": null,
        "content": null,
        "isRead": false,
        "createTime": "2026-08-25T10:00:00",
        "senderId": 7,
        "senderNickname": "taffy",
        "senderAvatar": "http://localhost:9000/lime-bucket/avatars/uuid.jpg",
        "isFollowing": null,
        "isFollowedBack": null
      }
    ],
    "nextCursor": "99",
    "hasMore": true
  }
}
```

| 字段           | 类型    | 说明                              |
|----------------|---------|-----------------------------------|
| type           | number  | 1=点赞笔记 2=收藏 3=评论 4=回复 5=关注 6=点赞评论/回复 |
| noteId         | number  | 关联笔记 id，可为 null             |
| commentId      | number  | 关联评论/回复 id：type=3（评论）、type=6（点赞评论）为评论 id；type=4（回复）为**回复本身**的 id；可为 null |
| content        | string  | 通知正文：评论(type=3)/回复(type=4)/点赞评论(type=6)为对应评论（回复）正文；点赞笔记、收藏、关注为 null |
| parentCommentId| number  | 回复通知（type=4）被回复的父评论 id，其他类型为 null |
| replyToContent | string  | 回复通知（type=4）被回复的**原评论正文**（"回复了你的评论：xxx" 展示用），可为 null |
| noteCover      | string  | 关联笔记封面图 URL（用于通知卡片），可为 null |
| isRead         | boolean | 是否已读                          |
| senderNickname | string  | 触发者昵称                        |
| senderAvatar   | string  | 触发者头像 URL                     |
| isFollowing    | boolean | 仅 type=5（关注）通知填充：当前用户是否已关注触发者；其他类型为 null |
| isFollowedBack | boolean | 仅 type=5（关注）通知填充：触发者是否已关注当前用户（互关判断用）；其他类型为 null |

---

### 未读通知数（分组）

`GET /api/notifications/unread-count`

**需要登录**：是

返回按信箱分组的未读数，对应三个信箱 Tab 的角标。

| 字段 | 说明 |
|------|------|
| total | 总未读数（主红点） |
| likeFav | 赞和收藏（type=1,2,6） |
| follow | 关注（type=5） |
| comment | 评论与回复（type=3,4） |

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "total": 12,
    "likeFav": 7,
    "follow": 2,
    "comment": 3
  }
}
```

---

### 标记单条已读

`PUT /api/notifications/{id}/read`

**需要登录**：是

**Path 参数**

| 参数 | 类型   | 说明     |
|------|--------|----------|
| id   | number | 通知 ID |

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 全部标记已读

`PUT /api/notifications/read-all`

**需要登录**：是

支持按信箱（类型）批量已读：**不带 `type` = 全部信箱已读；带 `type` = 只清对应类型**，打开哪个信箱调哪个即可清零（不用逐条已读）。

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 否 | 只标记这些类型的通知为已读，多个用逗号分隔；不传则全部 |

对应关系：赞和收藏 `type=1,2,6` / 关注 `type=5` / 评论与回复 `type=3,4`。

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 删除单条通知

`DELETE /api/notifications/{id}`

**需要登录**：是

物理删除，仅能删除当前登录用户自己的通知；删除后未读数同步变化。

**Path 参数**

| 参数 | 类型   | 说明     |
|------|--------|----------|
| id   | number | 通知 ID |

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### 清空全部通知

`DELETE /api/notifications/all`

**需要登录**：是

物理删除当前登录用户的全部通知，未读数归零。

**响应**

```json
{ "code": 200, "message": "操作成功", "data": null }
```

---

### SSE 实时推送未读数

`GET /api/notifications/subscribe?access_token=<accessToken>`

**需要登录**：是（token 通过 `access_token` 查询参数传入，浏览器 EventSource 无法自定义请求头）

建立 SSE 长连接后，每当产生新通知（点赞/收藏/评论/回复/关注）或未读数变化，服务端推送 `unread` 事件，`data` 为分组未读数 JSON（结构与 `unread-count` 接口一致），客户端据此点亮各信箱红点。

**事件示例**

```
event: unread
data: {"total":12,"likeFav":7,"follow":2,"comment":3}
```

---

## 即时通信 IM 接口 `/api/im`

> IM（私信）的消息收发走腾讯云 IM SDK 长连接，本组接口只负责「鉴权凭据下发」与「会话授权」。IM 用户 ID = 业务前缀 `lime_` + 业务用户 id，例如业务 id 7 → IM 用户 `lime_7`。
> 相关 App 集成代码与撤回/删除说明见 Android 工程 `xyz.larkzhh.lime.data.im` 包。

### 获取 UserSig

`GET /api/im/userSig`

**需要登录**：是

返回当前登录用户登录腾讯云 IM 所需的凭据，App 拿到后用 IM SDK `login(userId, userSig)` 登录。

**响应**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "sdkAppId": 1400000000,
    "userId": "lime_7",
    "userSig": "eJxNkE9...",
    "expire": 1788984
  }
}
```

| 字段     | 类型   | 说明 |
|----------|--------|------|
| sdkAppId | number | IM 应用 SDKAppID |
| userId   | string | IM 用户 ID（`lime_` + 业务 id） |
| userSig  | string | 用户签名（服务端用 SecretKey 生成，有效期可配置，默认 7 天） |
| expire   | number | userSig 过期时间戳（秒） |

### 打开私信会话

`POST /api/im/conversation/open`

**需要登录**：是

**请求**

```json
{ "targetUserId": 7 }
```

**逻辑**：仅当双方**互相关注**（互关）才放行，返回单聊会话 ID；未互关返回业务码 403，App 据此提示「需互相关注后才能私信」。

**响应**

```json
{ "code": 200, "message": "操作成功", "data": { "conversationId": "c2c_lime_7" } }
```

| 字段           | 类型   | 说明 |
|----------------|--------|------|
| conversationId | string | IM 单聊会话 ID（格式 `c2c_<IM userID>`），App 用它进入聊天页 |

```
