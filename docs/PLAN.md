# maxbo 技术方案（v0.1，2026-10-12）

## 1. 目标与范围

做一个**开源、非商业、纯客户端**的第三方微博客户端，优先保证"用着方便"，同时按低风险方式设计（见第 2 节）。

**v1（MVP）范围——以阅读为主：**

| 模块 | v1 包含 |
|---|---|
| 登录 | 在 WebView 中打开微博官方登录页，登录状态保存在本机；支持退出登录 |
| 首页 | 关注的人的"最新微博"时间线，下拉刷新、上拉加载 |
| 微博卡片 | 富文本（@、#话题#、链接、表情）、九宫格图片、转发原文、视频封面、互动计数、"广告"标识 |
| 详情 | 全文（长微博）、评论（热度/时间排序、楼中楼）、转发列表 |
| 媒体 | 图片查看器（缩放、左右滑动），视频播放 |
| 用户主页 | 资料卡和该用户的微博列表 |
| 热搜 | 热搜榜；点击某条在应用内 WebView 打开微博搜索页 |
| 互动 | 点赞/取消点赞、发评论（只在用户主动点击时触发） |
| 设置 | 主题（浅色/深色/跟随系统）、图片质量、关于与免责声明 |

**v1 明确不做：** 去广告、批量下载/导出、会员付费内容、游客模式、多账号、发原创微博（含传图）、私信和通知、原生搜索。其中**去广告、批量下载、会员内容这三项永久不做**，其余可在后续版本考虑。

## 2. 合规边界（设计约束，优先级高于功能）

| 原则 | 具体落地 |
|---|---|
| 纯客户端，无服务器 | 没有后端，不聚合、不缓存到云端、不转发任何微博数据；没有统计、埋点和第三方崩溃上报 |
| 登录态只在本机 | 用户在**微博官方登录页**中输入密码，应用接触不到密码；Cookie 存在 Keychain/Keystore（`flutter_secure_storage`） |
| 访问方式与浏览器相同 | 只调用网页端接口（`weibo.com/ajax/*`，必要时 `m.weibo.cn`），使用浏览器 UA；**不破解、不伪装官方 App**（不用其 appkey 或签名） |
| 不碰广告 | 时间线原样展示接口返回的内容；识别出的广告只用来打"广告"标签，**不提供过滤开关** |
| 克制的请求量 | API 并发 ≤ 2，相邻请求最小间隔约 500ms；只在用户操作时请求，**不做后台轮询和预取**（长文、评论都是点开后才加载）；遇到 432/418/429 自动退避并提示 |
| 不绕过风控 | 出现验证码时打开 WebView 让用户本人处理，**绝不自动识别或绕过验证码** |
| 写操作克制 | 只有用户主动点击才会点赞、评论；按钮在请求期间禁用；不提供任何批量或自动化互动 |
| 品牌隔离 | 名称为 maxbo，使用自己的图标，不用微博的名称或 Logo；README 写明非官方并附免责声明 |
| 代码洁净 | 参考项目 Android_16_Weibo 和 weiboX **没有 License**（即保留所有权利），只学习其接口形态，**不复制代码** |
| 测试数据 | 测试只用手写的合成数据；真实抓包放在 `captured/`，已加入 gitignore，永不提交 |

## 3. 技术选型

### 3.1 框架：Flutter（stable 3.47.x）

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| **Flutter** | 一套代码覆盖 Android 和 iOS；同类项目 PiliPala/PiliPlus 已经验证可行（侧载分发、双端）；所需插件（WebView、视频、图片）都有官方或活跃维护的实现 | 包体比纯原生大一些；Cookie 读取要自己写平台通道 | ✅ 采用 |
| Kotlin + SwiftUI 双原生 | 两端各自体验最好 | 工作量翻倍，个人或小团队维护不过来 | ❌ |
| KMP + Compose Multiplatform | UI 共享，用 Kotlin | iOS 侧的 WebView、视频生态不如 Flutter 成熟 | ❌ |
| React Native | 生态大 | 长列表和图片密集的流畅度要额外调优，没有明显优势 | ❌ |

鸿蒙：HarmonyOS 5/6 用户通过卓易通安装 APK。原生鸿蒙（Flutter OHOS 分支）不在 v1 范围内。

### 3.2 依赖（版本为 2026-10 时 pub.dev 的最新版）

| 用途 | 包 | 版本 | 选择理由 |
|---|---|---|---|
| 状态管理 / 依赖注入 | `flutter_riverpod` | ^3.4 | 主流、可测；不用代码生成 |
| 路由 | `go_router` | ^18.0 | 官方维护，支持深链接 |
| HTTP | `dio` | ^5.11 | 拦截器方便统一处理请求头、Cookie、XSRF、限流和错误映射 |
| 登录页 / 验证码 / 网页兜底 | `webview_flutter` | ^4.14 | 官方维护，最近一次发版在 2026-10 |
| 读取 WebView Cookie | **自写平台通道** | — | 官方插件只有 `setCookie`，无法读取 HttpOnly 的 `SUB`；`flutter_inappwebview` 正式版停在 2024-10，不依赖它 |
| Cookie 持久化 | `flutter_secure_storage` | ^11.2 | 底层是 Keychain / Keystore |
| 设置项 | `shared_preferences` | ^2.5 | |
| 图片加载、缓存与查看器 | `extended_image` | ^10.1 | 支持自定义请求头（Referer）、自定义缓存 key、手势缩放和翻页；`photo_view` 自 2024-04 起停更 |
| 视频 | `video_player` | ^2.14 | 官方维护，支持 `httpHeaders`；微博视频主要是 mp4，不需要 `media_kit`（后者包大，且 2025-12 后未更新） |
| 解析正文 HTML | `html` | ^0.15 | 能容错解析不带引号的属性值 |
| 打开外链 | `url_launcher` | latest | |
| 代码规范 | `flutter_lints` | latest | |

数据模型手写 `fromJson` 并做容错解析，不用 `json_serializable`。原因是微博的 JSON 字段时有时无、类型也不稳定（数字和字符串混用），所有 ID 一律按字符串保存。

## 4. 架构

### 4.1 目录结构

```
lib/
  main.dart / app.dart          # 入口、主题、路由
  core/
    net/                        # Dio 工厂、拦截器（请求头/Cookie/XSRF/限流/错误映射）
    session/                    # CookieStore、SessionController、平台通道封装
    media/                      # 带 Referer 的图片 provider、缓存 key 规则、视频地址解析与刷新
    util/                       # 时间格式化、计数格式化
  data/
    api/weibo_api.dart          # 所有端点集中在这里（见第 5 节）
    models/                     # Status, User, Comment, PicInfo, VideoInfo, HotSearchItem ...
    repositories/               # Timeline / Status / Comment / Profile / HotSearch
  features/
    auth/ timeline/ status/ comments/ profile/ hot_search/ media_viewer/ webview/ settings/
  widgets/
    status_card/  rich_text/  pic_grid/
android/app/src/main/kotlin/.../CookieBridge.kt   # 平台通道（Android）
ios/Runner/AppDelegate.swift                       # 平台通道（iOS；Runner 用传统文件分组，放在已有文件里免改 pbxproj）
test/  (fixtures/ 下只放合成数据)
```

### 4.2 关键设计

**会话与登录**
1. 在 WebView 中打开官方登录页。入口二选一，在 M1 实测后确定：
   - 桌面入口：`passport.weibo.com/sso/signin?entry=miniblog&url=https://weibo.com/`
   - 移动入口：`passport.weibo.cn/signin/login`，之后再加载一次 `https://weibo.com/` 完成跨域单点登录
2. 页面加载完成后，通过平台通道读取 `weibo.com` 和 `m.weibo.cn` 两个域的 Cookie，写入 CookieStore。
3. **登录成功以服务端为准，不以 Cookie 为准**。游客也会拿到 `SUB`，所以判断条件是 weibo.com 首页内嵌的 `$CONFIG` 中有当前用户的 uid（见第 8 节）。
4. 判定会话过期的信号：`ok == -100`、403 加"请登录"、401、跳转到 passport 或 login.sina.com.cn、返回 HTML 页面。过期后清掉本地会话并回到登录页。
5. 退出登录：同时清空 CookieStore 和 WebView 的 Cookie。

**网络层**（Dio 拦截器链）
- **请求头**：浏览器 UA（与登录 WebView 保持一致）、`Referer`、`Origin`、`X-Requested-With: XMLHttpRequest`、`X-XSRF-TOKEN`（取自同域 Cookie）。
- **Cookie**：按域名附加；响应里的 `Set-Cookie`（例如刷新后的 XSRF-TOKEN）回写到 CookieStore。
- **限流**：全局调度器限制并发 ≤ 2、最小间隔约 500ms；遇到 432/418/429 指数退避，并给出界面提示。
- **错误映射**：统一转换为 `SessionExpired`、`CaptchaRequired(url)`、`RateLimited`、`ApiError(msg)`、`NetworkError`，由界面层统一处理。

**媒体**
- 所有 `*.sinaimg.cn` 图片和 `*.weibocdn.com` 视频请求都带 `Referer: https://weibo.com/`；表情资源 `face.t.sinajs.cn` 不需要。
- 头像链接带约 3 小时有效的签名参数，缓存 key 要去掉这些签名参数。
- 配图按质量设置选择尺寸：列表用 `orj360` 或 `bmiddle`，查看器用 `mw2000` 或 `large`；gif 取 `original`；实况照片 v1 只显示静图并加角标。
- 视频地址选择顺序：`mp4_720p_mp4` > `mp4_hd_url` > `mp4_sd_url` > `playback_list` 中最好的非 DASH 条目。地址下发时是 `http://`，两端默认都拦截明文请求，统一改成 `https://`。播放前检查 `Expires` 参数，过期（或 5 分钟内过期）就重新请求 `statuses/show` 换取新地址；CDN 拒绝时也会换一次。

**正文渲染**
- 用 `html` 包解析 `text` 字段，转换成 `InlineSpan`：
  - `<a>`：@用户跳到主页，#话题# 跳到搜索 WebView，短链通过 `url_struct` 还原后外部打开
  - `<img>` 表情：用 `WidgetSpan` 内联显示
  - `<br>`：换行
  - `span.expand`（"展开"）：点击进入详情页，详情页用 `statuses/show` 取全文
- `isLongText` 不可靠（超话等情况也会置位），所以长文只在用户点"展开"或进入详情时才请求。

## 5. v1 使用的接口

| 功能 | 端点 | 分页 |
|---|---|---|
| 当前用户 / 登录校验 | GET `https://weibo.com/` 页面，解析内嵌的 `window.$CONFIG` | — |
| 首页时间线 | GET `/ajax/feed/friendstimeline?refresh=4&count=20&list_id=…` | `since_id` / `max_id` |
| 微博详情（含长文全文） | GET `/ajax/statuses/show?id=&isGetLongText=true` | — |
| ~~长文~~ | ~~GET `/ajax/statuses/longtext?id=`~~ 不用：只给纯文本，见第 8 节 | — |
| 评论 | GET `/ajax/statuses/buildComments?id&uid(作者)&flow(0热/1时间)&fetch_level=0&count=20&is_reload=1&is_show_bulletin=2&is_mix=0` | `max_id`（0 为末页） |
| 楼中楼 | 同上，`id=<根评论id>&fetch_level=1&is_mix=1`，首页 `max_id=0` | `max_id` |
| 转发列表 | GET `/ajax/statuses/repostTimeline?id&page&moduleID=feed` | `page`（到 `max_page` 为止） |
| 用户资料 | GET `/ajax/profile/info?uid=` 或 `?screen_name=` | — |
| 用户微博 | GET `/ajax/statuses/mymblog?uid&page&feature=0` | `page` |
| 热搜 | GET `/ajax/side/hotSearch` | — |
| 表情 | GET `/ajax/statuses/config` | — |
| 点赞 / 取消 | POST `/ajax/statuses/setLike`、`/ajax/statuses/cancelLike`（`id=<mid>`） | — |
| 评论 / 回复 | POST `/ajax/comments/create`、`/ajax/comments/reply` | — |

## 6. 里程碑

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| **M0 脚手架** | 用 brew 安装 Flutter，`flutter create`（只建 android 和 ios 平台），配置 lint，搭目录骨架，写好平台通道 | 在 Android 模拟器和 iOS 模拟器上都能启动空壳 App |
| **M1 登录验证（决定能否继续）** | WebView 登录、读取 Cookie、持久化、登录校验、过期处理；同时实测第 8 节的待验证项 | 用真实账号登录后，重启 App 仍保持登录，并能拿到 uid |
| **M2 首页时间线** | Status 模型、富文本、九宫格、转发卡片、刷新和分页、带 Referer 的图片加载（网络层已在 M1 完成） | 首页流畅滚动，图片正常加载（无 403） |
| **M3 详情与媒体** | 详情页、长文、评论（含楼中楼）、转发列表、图片查看器、视频播放 | 长文展开、评论分页、视频能播，且地址过期后能自动刷新 |
| **M4 主页与热搜** | 用户主页、热搜榜、应用内 WebView | 从卡片上的 @ 和头像能跳到主页 |
| **M5 轻互动** | 点赞、评论 | 界面状态与服务端一致；失败可回滚并提示 |
| **M6 打磨与打包** | 设置页、关于页、深色模式、出 release APK（按 ABI 拆分）、iOS 无签名 IPA 加自签文档 | 拿到可分发的安装包，README 完整 |

**完成 M2 就能看到"首页刷微博"的效果**，适合作为第一次演示的节点。

## 7. 测试与验证

- **单元测试**：模型解析、富文本解析、视频地址选择、错误映射、限流器。只用手写的合成数据。
- **Widget 测试**：微博卡片（纯文本、九宫格、转发、视频、广告标识）、富文本点击区域。
- **手动验证**：Android 模拟器（Pixel_9，API 37）和 iOS 27 模拟器。**登录这一步需要你本人在模拟器里用自己的账号完成**，我无法替你登录。
- **账号安全**：开发期间建议用小号测试，并控制刷新频率。

## 8. 待验证项（M1 实测结果，2026-10-13）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 登录入口与 Cookie | ✅ 桌面入口 `passport.weibo.com/sso/signin?entry=miniblog` 在手机 WebView 中显示为手机布局，支持短信、账号密码、扫码登录；发验证码前会弹网易易盾人机验证，需用户本人完成。登录后 passport 先写好 `.weibo.com` 的 `SUB/SUBP/ALF/SCF`，随后跳转的 `login.sina.com.cn/sso/v2/crossdomain` 在 WebView 中返回 403（Android 实测）。因此**不等页面回到 weibo.com，每次主页面加载完成或失败都校验一次**。跨域失败的后果是 `m.weibo.cn` 没有登录态，v1 只用 weibo.com，不受影响 |
| 2 | 会话是否绑定 UA | ✅ 不绑定：用手机 UA 请求同一会话的时间线，同样返回 `ok=1` |
| 3 | `list_id` | ✅ 「最新微博」是 `list_id=11000<uid>`（`/ajax/feed/allGroups` 中 type 20、title「最新微博」的分组）。同为 type 20 的「原创」`11001<uid>`、「视频」`11002<uid>` 会返回不同内容；「全部关注」`10001<uid>`、「特别关注」、「互相关注」以及不传 `list_id` 都返回与最新微博相同的内容，说明这类分组需要改走 `groupstimeline`（v1 不做）。M2 显式传 `11000<uid>` |
| 4 | 当前用户 | ✅ `/ajax/config` **不存在**（登录后返回 404；游客的 `ok:-100` 只是统一拦截）。网页版从首页 HTML 内嵌的 `window.$CONFIG`（含 `user{id,idstr,screen_name,…}` 和 `uid`）读取当前用户，我们也这样做。`/ajax/profile/info` 不带参数返回 400 |
| 5 | 广告字段 | ⏳ 部分确认：时间线里的微博带有 `isAd` 字段，普通微博的 `readtimetype` 为 `mblog`；M1 的 24 条和 M2 的约 120 条中都没有广告，尚未见到真实广告样本。目前按 `isAd == true` 或 `readtimetype == 'adMblog'` 或 `mark` 含 `mark_ad` 判定并打标签，见到样本后再核实 |
| 6 | iOS 读 HttpOnly Cookie | ✅ `WKHTTPCookieStore` 能读到页面响应下发的 HttpOnly Cookie（集成测试，本地服务器）；Android 已用真实登录验证可读到 `SUB` |

其他发现：服务端会在响应中轮换下发 `XSRF-TOKEN`、`WBPSESS`，Cookie 存储会自动更新并持久化。

**M2 实测结论（2026-10-13，诊断页「时间线结构」）：**

- **分页**：首屏传 `since_id=0`，翻页传上一页的 `max_id`，两页之间没有重叠。响应里有 `since_id`、`max_id`（int）以及对应的 `since_id_str`、`max_id_str`，我们用 `_str` 版本。`since_id` 等于首条的 id，`max_id` 小于末条的 id。`count=20` 实际返回 24–25 条，所以**不能用条数判断是否到底**，以 `max_id` 为 0 或列表为空为准。
- **图片尺寸**：`pic_infos` 的尺寸名不代表实际大小（`original` 其实是 `orj*` 中等尺寸，`largest` 才是 `large/` 原图），因此按实际宽高选择：列表从 `thumbnail`/`bmiddle`/`large`/`mw2000` 中取能覆盖显示尺寸的最小一档，原图只留给查看器。
- **`page_info`**：`object_type` 有 `video`、`topic`、`live` 等。视频封面是 `page_pic`，时长是 `media_info.duration`（秒），宽高在 `media_info.big_pic_info.pic_big`；视频地址（`mp4_720p_mp4`、`mp4_hd_url` 等）带 `Expires`/`ssig` 签名。`page_url` 是 `sinaweibo://` 应用内协议，无法打开。**转发微博的外层会复制一份原微博的 `page_info`**，只在转发框里显示，否则会重复。
- **正文链接**：@ 的 href 是相对路径 `/n/<昵称>`，带 `usercard` 属性；话题指向 `s.weibo.com/weibo?q=`；带 `data-pid` 的链接是图片（「查看图片」）；`t.cn` 短链在 `url_struct` 里有 `long_url`；很多链接内含 `img.icon-link` 类型图标（网页、商品、视频等）。
- **`source`**：可能是纯文本，也可能是 `<a>` 片段，需要去掉标签。

**M3 实测结论（2026-10-13，诊断页「详情结构」）：**

- **长文**：时间线里长微博的 `text` 被截断，末尾带 `<span class="expand">展开</span>`。`statuses/show?isGetLongText=true` 返回的 `text` 是**完整的 HTML**（3 个样本都含全文结尾，且没有展开标记），链接、表情照常可用。`show` 里另有 `longText{content,…}`，`statuses/longtext` 返回 `longTextContent`，这两处都是纯文本（与 `text_raw` 相同），附带 `topic_struct`/`url_struct`，链接要自己重建。所以**只用 `show`，不调 `longtext`**。`show` 的 `id` 传数字 id 或 mblogid 都可以。
- **视频**：`page_info.media_info` 里的 `mp4_720p_mp4`、`mp4_hd_url`、`mp4_sd_url`、`stream_url`、`h265_*`、`inch_*` 都是 `http://f.video.weibocdn.com/…`，带 `Expires`/`ssig`/`KID` 签名，**约 60 分钟后过期**，时间线和 `show` 返回的一样。`playback_list` 每项是 `meta{label,quality_index,quality_desc,type}` 加 `play_info{mime,protocol,width,height,url}`，其中 `type` 3 是拖动缩略图（`image/jpeg`）。明文 http 在 Android 上直接报 `CleartextNotPermitted`，改成 https 后能正常播放（Android 实测）。
- **评论**：`buildComments` 返回 `data[]`、`max_id`（int，0 表示末页）、`total_number`、`rootComment`、`trendsText`。`flow=0` 按热度排序（时间是乱序的），`flow=1` 按时间倒序，每页约 20 条，两页之间没有重叠。每条评论有 `id`/`idstr`、`text`（HTML，含 @、话题、表情 `img`）、`text_raw`、`source`（如「来自上海」）、`like_counts`、`liked`、`total_number`（楼中楼回复数）、`comments`（0–2 条回复预览）、`more_info`。**带图评论**的图片不在 `text` 里，而是在 `url_struct` 的一项（`url_type` 39）中：`pic_infos` 以 pid 为键，项内没有 `pic_id`，尺寸档为 `thumbnail`/`bmiddle`/`large`/`woriginal`，没有宽高。
- **楼中楼**：`fetch_level=1`，`id` 为根评论 id，`max_id` 从 0 开始。回复按时间倒序返回，`text` 自带「回复@xx:」前缀，并带有 `reply_comment`。`total_number` 可能比实际返回的条数多（有的回复被折叠或删除）。
- **转发**：`repostTimeline` 返回 `data[]`（完整的 status，含 `retweeted_status`）、`total_number`、`max_page`、`next_cursor`。`count` 参数不起作用，每页 20 条左右；按时间倒序，两页之间没有重叠。

## 9. 分发

- **Android**：GitHub Releases 发布按 ABI 拆分的 APK。签名密钥只保存在本地（`key.properties` 已 gitignore）。国行手机通常没有 GMS，暂不受 Google 开发者验证影响；2027 年该政策全球生效后再评估。
- **iOS**：发布无签名 IPA，文档说明如何用 SideStore/AltStore 或自己的开发者账号签名。不上 App Store。
- **CI**：等你建好 GitHub 远程仓库后，再加 GitHub Actions 自动构建。

## 10. 已定事项

- **License**：GPL-3.0。
- **Git 提交身份**：仅本仓库配置 `Leon <41661196+leonfox28@users.noreply.github.com>`。
- **Flutter**：stable 3.47.7（Dart 3.13.5）。
- **包名**：暂定 `app.maxbo`，首次发版前可以改。

## 11. 进度与交接（更新于 2026-10-13）

| 里程碑 | 状态 | 提交 |
|---|---|---|
| M0 脚手架 | ✅ 完成 | `1d6888b` |
| M1 登录验证 | ✅ 完成：Android 真实登录通过，重启后保持登录；iOS 的 HttpOnly Cookie 读取已由集成测试验证，真实登录待补测 | `02b3d90` `af0e0e2` `88969f8` |
| M2 首页时间线 | ✅ 完成：Android 真实会话下加载、下拉刷新、滚动翻页（连续 3 页）都正常，图片无 403，浅色和深色主题都核对过；iOS 只验证了能编译 | `984f7bb` `585cc22` |
| M3 详情与媒体 | ✅ 完成：Android 真实会话下核对了详情页（长文全文、评论按热度/按时间、评论翻页、楼中楼、转发页签）、图片查看器（翻页、点击或下滑关闭）、视频播放、深色主题；视频地址过期后自动换新地址（时间线加载 1 小时后点视频，先请求一次 `statuses/show` 再播放，无播放错误）；iOS 只验证了能编译 | `41e7e80` `544dc60` |
| M4 主页与热搜 | ⏭ 下一步 | — |
| M5–M6 | 未开始 | — |

### 已有的基础设施（M4 直接复用）

- **网络**：`weiboApiProvider`（`lib/data/api/weibo_api.dart`）。新端点加成 `WeiboApi` 的方法，内部用 `getJson()` / `getPage()`，统一完成错误映射和登录过期处理。请求头、Cookie、XSRF、限流都由 `createWeiboDio()` 的拦截器负责，调用方不用操心。
- **会话**：`sessionProvider` 为 `SignedIn(account)` 时可拿到 `account.uid`；登录失效时会自动切到 `SignedOut(expired: true)`，界面统一显示 `LoginPrompt`。
- **错误类型**：`SessionExpiredException` / `CaptchaRequiredException` / `RateLimitedException` / `NetworkException` / `WeiboApiException`，都带有可以直接展示的中文 `message`；界面上用 `loadErrorMessage(error)` 取。
- **不自动重试**：`main.dart` 的 `ProviderScope(retry: noRetry)` 关掉了 Riverpod 默认的失败重试（默认最多 10 次），保证每个请求都由用户操作触发。测试里的 `ProviderScope`/`ProviderContainer` 也要传 `noRetry`。
- **模型**（`lib/data/models/`）：`Status`、`WeiboUser`、`PicInfo`、`VideoInfo`、`PageCard`、`UrlEntity`、`FeedPage`、`Comment`、`Paged<T>`，都用 `tryFromJson` 容错解析，读字段用 `lib/core/util/json_read.dart`。时间、计数、时长的格式化在 `lib/core/util/format.dart`。`picsOf(json)` 解析任何带 `pic_ids`/`pic_infos` 的对象。
- **分页列表**：`PagedController<T>`（`lib/core/paging/`）实现首屏、`loadMore()`、去重和过期结果丢弃，子类只需写 `fetchPage(cursor)` 和 `idOf(item)`，游标是字符串（`max_id` 或页码）。界面用 `PagedSliverList`（加载中、错误重试、空列表、页脚）和 `LoadMoreTrigger`（滚到接近底部时加载下一页），都在 `lib/widgets/load_state.dart`。示例见 `features/comments/comments_controller.dart` 和 `features/status/status_detail_providers.dart`（转发，按页码翻页）。
- **图片**：`WeiboImage` / `weiboImageProvider`（`lib/core/media/weibo_image.dart`）自动带 Referer，缓存 key 去掉头像签名；列表尺寸用 `PicInfo.variantFor(像素)` 选，查看器用 `PicInfo.viewerVariant`（`mw2000` 一档，GIF 用原图）。
- **视频**：`lib/core/media/video_source.dart` 的 `resolveVideoUrl()` 负责选地址、判断过期、必要时重新请求 `statuses/show`；播放请求带 `videoHttpHeaders`。
- **路由与入口**：`/status/:id`（详情）、`/comments/:id`（楼中楼）、`/video/:id`、`/viewer`，`extra` 传上一页已有的数据（`Status`、`CommentThread`、`ImageViewerArgs`）。卡片和评论上的各种点击统一走 `statusCardActions(context)`（`lib/features/status/status_actions.dart`）：点卡片和「展开」进详情，图片进查看器，视频进播放页，网页链接用外部浏览器打开；**@、头像、话题目前只弹「将在 M4 实现」**。`UserAvatar`（`lib/widgets/user_avatar.dart`）是通用的圆形头像。
- **详情页**：`StatusDetailPage` 先显示上一页传来的 `Status`，再用 `statusDetailProvider` 取全文和最新计数。评论和转发列表在页面存续期间保持已加载状态，切换页签或排序不会重复请求。
- **调试**：调试版「我」页右上角的虫子图标进入诊断页：▶ 是 M1 的接口检查，▤ 是「时间线结构」探针，📄 是「详情结构」探针（长文、视频地址和有效期、评论/楼中楼/转发的分页）。日志前缀 `[maxbo-diag]` / `[maxbo]`，用 `adb logcat` 读取。`devLog()` 只在调试版输出，不得记录 Cookie 值或微博正文。

### M4 要点

- **入口**：在 `statusCardActions` 里把 `onUser`（头像）、`MentionLink`（@昵称，只有昵称没有 uid）、`TopicLink` 换成真实页面。
- **用户主页**：`/ajax/profile/info?uid=` 或 `?screen_name=`（@ 链接只能用后者），用户微博 `/ajax/statuses/mymblog?uid&page&feature=0`。这两个端点的结构**还没有实测**，先在诊断页加探针确认字段和翻页规则。微博列表可以直接用 `PagedController`（页码游标）配合 `StatusCard`。
- **热搜**：`/ajax/side/hotSearch`；点某条或点话题，在应用内 WebView 打开 `s.weibo.com/weibo?q=`。应用内 WebView 要沿用登录页的 UA 和 Cookie。
- **还没做的小项**：验证码（`CaptchaRequiredException`）目前只显示文字，还没有打开 WebView 让用户处理的入口，应用内 WebView 做好后可一并接上；图片磁盘缓存没有大小上限，留给 M6 的设置页处理；评论的点赞数只显示（M5）。
- **M3 未在真机上验证的部分**：查看器的双击放大（adb 连点两次太慢，触发不了双击）；长图在查看器里按屏幕宽度铺满（没遇到真实长图样本）；GIF 和实况照片（时间线里还没出现过样本）。
- **流畅度**：M2、M3 都只在模拟器的调试版上看过，滚动没有明显卡顿；还没在 profile 版或真机上测帧率。

### 开发环境注意

- 真实登录要过短信验证码和网易易盾人机验证，代价很高。保存着登录态的设备**只能用 `adb install -r` 覆盖安装**；不要在上面运行 `integration_test`（会清空 WebView Cookie），也不要卸载或清除数据。
- 本机可能同时连着别的模拟器（2026-10-13 有一台 `emulator-5586` 不属于本项目），adb 命令要用 `-s emulator-5554` 指定保存着登录态的 Pixel_9。
- 部分 Google 域名（如 `dl.google.com`）可能被本机代理规则判为直连而连不上，表现为 TLS 握手中断；这时调整代理分流规则即可，**不要往仓库里写镜像地址**。
- `adb shell uiautomator dump` 看不到 Flutter 的界面元素，操作模拟器只能靠截图（`adb exec-out screencap -p`）加坐标点击。截图是 1080×2424，按缩小后的图量出的坐标要乘回原始比例。
- 核对深色主题可用 `adb -s emulator-5554 shell cmd uimode night yes`，看完记得改回 `no`。
