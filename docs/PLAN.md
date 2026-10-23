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

**v1 明确不做：** 去广告、批量下载/导出、会员付费内容、游客模式、多账号、发原创微博（含传图）、私信、原生搜索。（通知原本也在这里，v0.3.1 之后已加上；发原创微博（含传图）v0.6.4 之后已加上；原生搜索 v0.8.0 之后已加上，见第 11 节。）其中**去广告、批量下载、会员内容这三项永久不做**，其余可在后续版本考虑。

## 2. 合规边界（设计约束，优先级高于功能）

| 原则 | 具体落地 |
|---|---|
| 纯客户端，无服务器 | 没有后端，不聚合、不缓存到云端、不转发任何微博数据；没有统计、埋点和第三方崩溃上报 |
| 登录态只在本机 | 用户在**微博官方登录页**中输入密码，应用接触不到密码；会话用 Android Keystore 里的密钥加密后存在本机 |
| 访问方式与浏览器相同 | 只调用网页端接口（`weibo.com/ajax/*`，必要时 `m.weibo.cn`），使用浏览器 UA；**不破解、不伪装官方 App**（不用其 appkey 或签名） |
| 不碰广告 | 时间线原样展示接口返回的内容；识别出的广告只用来打"广告"标签，**不提供过滤开关** |
| 克制的请求量 | API 并发 ≤ 2，相邻请求最小间隔约 500ms；只在用户操作时请求，**不做后台轮询和预取**（长文、评论都是点开后才加载）；遇到 432/418/429 自动退避并提示 |
| 不绕过风控 | 出现验证码时打开 WebView 让用户本人处理，**绝不自动识别或绕过验证码** |
| 写操作克制 | 只有用户主动点击才会点赞、评论、发微博、删除；按钮在请求期间禁用；不提供任何批量或自动化互动 |
| 品牌隔离 | 名称为 maxbo，使用自己的图标，不用微博的名称或 Logo；README 写明非官方并附免责声明 |
| 代码洁净 | 参考项目 Android_16_Weibo 和 weiboX **没有 License**（即保留所有权利），只学习其接口形态，**不复制代码** |
| 测试数据 | 测试只用手写的合成数据；真实抓包放在 `captured/`，已加入 gitignore，永不提交 |

## 3. 技术选型

### 3.1 框架：Kotlin + Jetpack Compose（2026-10-23 起）

v0.1.0 到 v0.11.0 用的是 Flutter（stable 3.47.x）。当初选它是为了一套代码覆盖 Android 和 iOS。2026-10-22 起不再支持 iOS（§10），这个理由就不成立了；Flutter 的代价却还在：平台的新交互要等 Flutter 自己实现，实现得不好还得自己补（例如预测性返回，§11「预测性返回卡顿」），读 HttpOnly Cookie 要自己写平台通道。于是用户决定改用原生的 Kotlin + Jetpack Compose。v0.12.0 是第一个 Kotlin 版，功能和交互与 v0.11.0 对齐。迁移计划、各阶段的备注和验收记录见 [KOTLIN_MIGRATION.md](KOTLIN_MIGRATION.md)。

鸿蒙：HarmonyOS 5/6 用户通过卓易通安装 APK，原生鸿蒙不做。

### 3.2 依赖

版本统一写在 `gradle/libs.versions.toml`，2026-10-22 时取的是各库的最新稳定版。

| 用途 | 库 | 说明 |
|---|---|---|
| 界面 | Jetpack Compose + Material 3（Compose BOM）、`material3-adaptive` | 配色从 Flutter 版的 `ColorScheme.fromSeed(#3F51B5)` 抄成固定值 |
| 导航 | Navigation 3 | 页面栈是自己持有的列表，转场和预测性返回自己给 |
| 状态和依赖注入 | ViewModel + `StateFlow`，手写的 `AppGraph` | 不用 Hilt / Koin |
| HTTP | OkHttp 5 | 拦截器统一处理请求头、Cookie、XSRF、限流；不用 Retrofit，端点照 `WeiboApi` 手写 |
| JSON | kotlinx-serialization-json 的 `JsonElement` 树 | 模型手写容错解析，不加 `@Serializable` |
| 解析正文 HTML、`$CONFIG` | jsoup | |
| 登录页 / 验证码 / 网页 / 续期 | `android.webkit.WebView` | `CookieManager` 能直接读 HttpOnly 的 `SUB` |
| 登录态存储 | Android Keystore 的 AES-GCM 密钥加密的文件 | 不用已废弃的 `security-crypto` |
| 设置项 | DataStore Preferences | 第一帧前读入 |
| 图片加载与缓存 | Coil 3（OkHttp 网络层、GIF） | Referer、缓存 key、磁盘缓存上限都在 `WeiboImage.kt` |
| 看大图 | Telephoto（`zoomable-image-coil3`、`flick`） | 缩放、长图分块、甩动关闭 |
| 视频 | AndroidX Media3 ExoPlayer | |
| 选图 | 系统照片选择器（`PickVisualMedia`） | |
| 开源许可 | AboutLibraries 的 Gradle 插件 | 只用插件生成 JSON，界面自己画 |
| 测试 | JUnit 4、kotlinx-coroutines-test、Turbine、OkHttp MockWebServer、Robolectric + Compose UI Test；设备测试用 AndroidX Test | |
| 格式与检查 | Spotless + ktfmt（kotlinlang 风格）、Android Lint | |

数据模型手写容错解析，原因是微博的 JSON 字段时有时无、类型也不稳定（数字和字符串混用），所有 ID 一律按字符串保存。

## 4. 架构

### 4.1 目录结构

```
app/src/main/java/io/github/leonfox28/maxbo/
  MaxboApplication / MainActivity / MaxboApp / AppGraph   # 入口、根部 Composable、依赖容器
  core/
    net/                        # OkHttp（请求头/Cookie/XSRF/限流）、Cookie jar、错误映射
    session/                    # 读 WebView Cookie、SessionController、续期、加密存储
    media/                      # 图片加载器（Referer、缓存 key）、磁盘缓存、视频地址选择与刷新
    paging/ settings/ util/     # 分页、设置、JSON 读取与格式化
  data/
    api/WeiboApi.kt             # 所有端点集中在这里（见第 5 节）
    model/                      # Status, WeiboUser, Comment, PicInfo, UserProfile, HotSearch ...
  ui/
    navigation/ theme/ shell/ widget/
    auth/ timeline/ status/ comments/ media/ profile/ search/ notifications/
    webview/ interactions/ compose/ settings/ debug/
app/src/test/                   # 单元测试和 Robolectric 页面测试（support/ 下只放合成数据和假实现）
app/src/androidTest/            # 设备测试（读 WebView 的 HttpOnly Cookie）
```

### 4.2 关键设计

**会话与登录**
1. 在 WebView 中打开官方登录页。入口二选一，在 M1 实测后确定：
   - 桌面入口：`passport.weibo.com/sso/signin?entry=miniblog&url=https://weibo.com/`
   - 移动入口：`passport.weibo.cn/signin/login`，之后再加载一次 `https://weibo.com/` 完成跨域单点登录
2. 页面加载完成后，用 `CookieManager` 读取 `weibo.com` 和 `m.weibo.cn` 两个域的 Cookie，写入 CookieStore。
3. **登录成功以服务端为准，不以 Cookie 为准**。游客也会拿到 `SUB`，所以判断条件是 weibo.com 首页内嵌的 `$CONFIG` 中有当前用户的 uid（见第 8 节）。
4. 判定会话过期的信号：`ok == -100`、403 加"请登录"、401、跳转到 passport 或 login.sina.com.cn、返回 HTML 页面。收到这些信号时先续期：在不显示的 WebView 里打开 weibo.com，让微博自己的页面用 30 天的自动登录凭证换一个新的 `SUB`（见第 8 节「会话续期实测结论」）。确认还是同一个 uid 后换进 CookieStore，读请求再发一次。续期失败才清掉本地会话，回到登录页。
5. 退出登录：同时清空 CookieStore 和 WebView 的 Cookie。

**网络层**（OkHttp 的拦截器和 `CookieJar`）
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
- 用 jsoup 解析 `text` 字段，转换成 `AnnotatedString`：
  - `<a>`：@用户跳到主页，#话题# 打开搜索结果页（v0.8.0 之前是搜索 WebView），短链通过 `url_struct` 还原后外部打开
  - `<img>` 表情：用 `InlineTextContent` 内联显示
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
| 用户微博 | GET `/ajax/statuses/mymblog?uid&page&feature=0` | `page`（`since_id` 为空时到底） |
| 热搜 | GET `/ajax/side/hotSearch` | — |
| 搜索（含话题） | GET `/ajax/statuses/search?q=&count=20&page=`，不带 `atten`（用户确认可以这样用），见第 8 节 | `page`（到空页为止） |
| 网页版搜索结果（应用内 WebView） | `https://m.weibo.cn/search?containerid=231522`（话题）或 `100103`（普通词）`&q=` | — |
| 表情 | GET `/ajax/statuses/config` | — |
| 点赞 / 取消（微博） | POST `/ajax/statuses/setLike`、`/ajax/statuses/cancelLike`（表单 `id=<mid>`） | — |
| 点赞 / 取消（评论） | POST `/ajax/statuses/updateLike`、`/ajax/statuses/destroyLike`（`object_id=<评论id>&object_type=comment`） | — |
| 评论 / 回复 | POST `/ajax/comments/create`（`id&comment&is_repost=0&comment_ori=0&is_comment=0`）、`/ajax/comments/reply`（另加 `cid=<被回复的评论id>`） | — |
| 删除自己的评论 | POST `/ajax/statuses/destroyComment`（`cid=`） | — |
| 发微博 | POST `/ajax/statuses/update`（`content&visible`，带图时另加 `pic_id=[{"type":"pic","pid":…}]`），见第 8 节 | — |
| 上传图片 | POST `https://picupload.weibo.com/interface/pic_upload.php?app=miniblog&s=json&p=1&data=1`，请求体就是图片文件本身 | — |
| 删除自己的微博 | POST `/ajax/statuses/destroy`（`id=`） | — |
| 转发 | POST `/ajax/statuses/normal_repost`（`id&comment&is_comment&comment_ori=0&is_repost=0&visible`，正文为空时不发 `comment`），见第 8 节 | — |
| 通知：收到的评论、@我的评论、@我的微博、赞 | GET `/ajax/message/cmt`、`/ajax/comments/mentions`、`/ajax/statuses/mentions`、`/ajax/message/attitudes`，见第 8 节 | `max_id` = 上一页的 `next_cursor`（0 为末页） |
| 未读通知 | GET `https://rm.api.weibo.com/2/remind/push_count.json`（带网页版的 `source`，用户确认可以用），只看有没有，不显示数字 | — |

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
- **页面测试**（Robolectric + Compose UI Test）：微博卡片（纯文本、九宫格、转发、视频、广告标识）、富文本点击区域、各页面的交互。
- **设备测试**：读 WebView 的 HttpOnly Cookie，只在没有真实登录的模拟器上跑。
- **手动验证**：Android 模拟器（Pixel_9，API 37）。**登录这一步需要你本人在模拟器里用自己的账号完成**，我无法替你登录。
- **账号安全**：开发期间建议用小号测试，并控制刷新频率。

## 8. 待验证项（M1 实测结果，2026-10-13）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 登录入口与 Cookie | ✅ 桌面入口 `passport.weibo.com/sso/signin?entry=miniblog` 在手机 WebView 中显示为手机布局，支持短信、账号密码、扫码登录；发验证码前会弹网易易盾人机验证，需用户本人完成。登录后 passport 先写好 `.weibo.com` 的 `SUB/SUBP/ALF/SCF`，随后跳转的 `login.sina.com.cn/sso/v2/crossdomain` 在 WebView 中返回 403（Android 实测）。因此**不等页面回到 weibo.com，每次主页面加载完成或失败都校验一次**。跨域失败的后果是 `m.weibo.cn` 没有登录态，v1 只用 weibo.com，不受影响 |
| 2 | 会话是否绑定 UA | ✅ 不绑定：用手机 UA 请求同一会话的时间线，同样返回 `ok=1` |
| 3 | `list_id` | ✅ 「最新微博」是 `list_id=11000<uid>`（`/ajax/feed/allGroups` 中 type 20、title「最新微博」的分组）。同为 type 20 的「原创」`11001<uid>`、「视频」`11002<uid>` 会返回不同内容；「全部关注」`10001<uid>`、「特别关注」、「互相关注」以及不传 `list_id` 都返回与最新微博相同的内容，说明这类分组需要改走 `groupstimeline`（v1 不做）。M2 显式传 `11000<uid>` |
| 4 | 当前用户 | ✅ `/ajax/config` **不存在**（登录后返回 404；游客的 `ok:-100` 只是统一拦截）。网页版从首页 HTML 内嵌的 `window.$CONFIG`（含 `user{id,idstr,screen_name,…}` 和 `uid`）读取当前用户，我们也这样做。`/ajax/profile/info` 不带参数返回 400 |
| 5 | 广告字段 | ⏳ 部分确认：时间线里的微博带有 `isAd` 字段，普通微博的 `readtimetype` 为 `mblog`；M1 的 24 条和 M2 的约 120 条中都没有广告，尚未见到信息流里插入的真实广告。目前按 `isAd == true` 或 `readtimetype == 'adMblog'` 或 `mark` 含 `mark_ad` 判定并打标签，见到样本后再核实。**例外（M6 实测）**：微博会给它判定为广告内容的普通微博也打上 `isAd=true`、`readtimetype=adMblog`、`mark=999_reallog_mark_ad:999\|WeiboADNatural`（测试账号关注的用户主页 71 条中有 8 条，都是该用户自己转发的商业内容）。这是对内容的暗中分类，不是信息流里插入的广告，所以 `mark` 含 `WeiboADNatural` 时不打标签 |
| 6 | iOS 读 HttpOnly Cookie | ✅ `WKHTTPCookieStore` 能读到页面响应下发的 HttpOnly Cookie（集成测试，本地服务器）；Android 已用真实登录验证可读到 `SUB` |

其他发现：服务端会在响应中轮换下发 `XSRF-TOKEN`、`WBPSESS`，Cookie 存储会自动更新并持久化。

**M2 实测结论（2026-10-13，诊断页「时间线结构」）：**

- **分页**：首屏传 `since_id=0`，翻页传上一页的 `max_id`，两页之间没有重叠。响应里有 `since_id`、`max_id`（int）以及对应的 `since_id_str`、`max_id_str`，我们用 `_str` 版本。`since_id` 等于首条的 id，`max_id` 小于末条的 id。`count=20` 实际返回 24–25 条，所以**不能用条数判断是否到底**，以 `max_id` 为 0 或列表为空为准。
- **图片尺寸**：`pic_infos` 的尺寸名不代表实际大小（`original` 其实是 `orj*` 中等尺寸，`largest` 才是 `large/` 原图），因此按实际宽高选择：列表从 `thumbnail`/`bmiddle`/`large`/`mw2000` 中取能覆盖显示尺寸的最小一档，原图只留给查看器。
- **`page_info`**：`object_type` 有 `video`、`topic`、`live` 等。视频封面是 `page_pic`，时长是 `media_info.duration`（秒），宽高在 `media_info.big_pic_info.pic_big`；视频地址（`mp4_720p_mp4`、`mp4_hd_url` 等）带 `Expires`/`ssig` 签名。`page_url` 是 `sinaweibo://` 应用内协议，无法打开。**转发微博的外层会复制一份原微博的 `page_info`**，只在转发框里显示，否则会重复。`object_type:"webpage"`（type 23，如「微博品牌活动」发的活动链接）可以没有 `page_pic`，`page_desc` 为空字符串，`page_title` 就是正文里那个短链接在 `url_struct` 里的 `url_title`（两者 `page_id` 相同），这样的卡片只是重复正文，所以没有图片和描述、标题又和正文里某个链接的标题相同时不显示卡片（2026-10-17，诊断页「广告字段」探针输入 uid 时也会列出 `page_info` 结构）。
- **图片和视频混排**（2026-10-17 补测，诊断页「时间线结构」）：同时带多个视频、或视频加图片的微博**没有 `page_info` 和 `pic_infos`**，媒体全在 `mix_media_info.items[]` 里，每项是 `{type, id, data, scheme, actionlog}`：`type:"video"` 的 `data` 和视频 `page_info` 结构相同（`object_type:"video"`、`page_pic`、`media_info`），`type:"pic"` 的 `data` 和 `pic_infos` 的一项相同。`pic_num` 是总项数，`pic_ids` 只列其中的图片（也可能为空）。`Status.mixedMedia` 按原顺序读出，九宫格里视频格带播放图标和时长；点图片在查看器里只翻这条微博的图片，点视频进 `/video/:id?i=<第几个视频>`，地址过期重新请求 `statuses/show` 后按同一序号取。
- **正文链接**：@ 的 href 是相对路径 `/n/<昵称>`，带 `usercard` 属性；话题指向 `s.weibo.com/weibo?q=`；带 `data-pid` 的链接是图片（「查看图片」）；`t.cn` 短链在 `url_struct` 里有 `long_url`；很多链接内含 `img.icon-link` 类型图标（网页、商品、视频等）。
- **`source`**：可能是纯文本，也可能是 `<a>` 片段，需要去掉标签。

**M3 实测结论（2026-10-13，诊断页「详情结构」）：**

- **长文**：时间线里长微博的 `text` 被截断，末尾带 `<span class="expand">展开</span>`。`statuses/show?isGetLongText=true` 返回的 `text` 是**完整的 HTML**（3 个样本都含全文结尾，且没有展开标记），链接、表情照常可用。`show` 里另有 `longText{content,…}`，`statuses/longtext` 返回 `longTextContent`，这两处都是纯文本（与 `text_raw` 相同），附带 `topic_struct`/`url_struct`，链接要自己重建。所以**只用 `show`，不调 `longtext`**。`show` 的 `id` 传数字 id 或 mblogid 都可以。
- **视频**：`page_info.media_info` 里的 `mp4_720p_mp4`、`mp4_hd_url`、`mp4_sd_url`、`stream_url`、`h265_*`、`inch_*` 都是 `http://f.video.weibocdn.com/…`，带 `Expires`/`ssig`/`KID` 签名，**约 60 分钟后过期**，时间线和 `show` 返回的一样。`playback_list` 每项是 `meta{label,quality_index,quality_desc,type}` 加 `play_info{mime,protocol,width,height,url}`，其中 `type` 3 是拖动缩略图（`image/jpeg`）。明文 http 在 Android 上直接报 `CleartextNotPermitted`，改成 https 后能正常播放（Android 实测）。
- **评论**：`buildComments` 返回 `data[]`、`max_id`（int，0 表示末页）、`total_number`、`rootComment`、`trendsText`。`flow=0` 按热度排序（时间是乱序的），`flow=1` 按时间倒序，每页约 20 条，两页之间没有重叠。每条评论有 `id`/`idstr`、`text`（HTML，含 @、话题、表情 `img`）、`text_raw`、`source`（如「来自上海」）、`like_counts`、`liked`、`total_number`（楼中楼回复数）、`comments`（0–2 条回复预览）、`more_info`。**带图评论**的图片不在 `text` 里，而是在 `url_struct` 的一项（`url_type` 39）中：`pic_infos` 以 pid 为键，项内没有 `pic_id`，尺寸档为 `thumbnail`/`bmiddle`/`large`/`woriginal`，没有宽高。带图评论的 `pic_num` 为 1，`text_raw` 里有这一项的 `short_url`。**直接回复带图评论的回复**（楼中楼列表和 `comments` 预览里都是）也带着被回复评论的这一项，但 `pic_num` 为 0、`text_raw` 里没有它的 `short_url`，带图回复的 `pic_num` 为 1，`url_struct` 里同时有自己的图片和被回复评论的图片，只有自己那张的 `short_url` 在 `text_raw` 里，`text` 里还有一个指向它的「查看图片」链接 `<a data-pid=<pid> href=https://wxN.sinaimg.cn/…>`（根评论的 `text` 里没有）。图片已经显示在下面时，`CommentTile` 隐藏这个链接；回复预览不显示图片，保留链接。所以不能把 `url_struct` 里的图片都算作评论自己的（2026-10-16 实测）。
- **楼中楼**：`fetch_level=1`，`id` 为根评论 id，`max_id` 从 0 开始。回复按时间倒序返回，`text` 自带「回复@xx:」前缀，并带有 `reply_comment`。`total_number` 可能比实际返回的条数多（有的回复被折叠或删除）。
- **转发**：`repostTimeline` 返回 `data[]`（完整的 status，含 `retweeted_status`）、`total_number`、`max_page`、`next_cursor`。`count` 参数不起作用，每页 20 条左右；按时间倒序，两页之间没有重叠。

**M4 实测结论（2026-10-13，诊断页「主页与热搜结构」）：**

- **用户资料**：`profile/info` 返回 `data{user, tabList, blockText}`。`user` 里有 `description`、`location`、`gender`（`m`/`f`）、`followers_count`（另有已格式化的 `followers_count_str`）、`friends_count`、`statuses_count`、`verified_reason`、`following`、`follow_me`、`cover_image_phone` 等。`cover_image_phone` 有时是用 `;` 连起来的多个地址，取第一个。`?screen_name=` 查到的和按 uid 查到的是同一个人。**查不存在的昵称仍返回 `ok=1`**，只是没有 `data`，而是顶层一个空的 `user:{}` 加 `message`，所以要按"有没有 user"判断。
- **用户微博**：`mymblog` 返回 `data{since_id(str), list, status_visible, bottom_tips_visible, bottom_tips_text, topicList, total}`。只传 `page` 就能翻页（第 2 页加不加 `since_id` 结果完全一样），两页之间没有重叠，每页约 20 条。**最后一页 `since_id` 为空字符串**，再往后是空列表。第 1 页开头是置顶微博（`isTop=1`、`mblogtype=2`，可能有多条），所以第 1 页不是严格按时间排序。样本里的 `bottom_tips_visible` 都是 false，提示文案（例如"仅展示半年内的微博"）还没见到实例，暂不显示。**列表里还夹着别人的微博**（2026-10-21，诊断页 📢 探针）：这个用户赞过或评论过的微博，按互动时间插在他自己的微博中间（一个用户前 3 页 74 条里有 15 条），只靠 `title` 标明来由。它们的 `title` 是 `{text, structs:[{name, scheme, cleaned}]}`，`text` 与 `structs[0].name` 相同，形如「他10-17赞过的微博(10)」「他1小时前评论过的微博(11)」，括号里是那天互动过的条数，也可能没有括号；`scheme` 是 `sinaweibo://cardlist?containerid=…_-_mix`（赞过）或 `…_-_comment`（评论过），只有官方 App 能打开，containerid 里带着主页用户的 uid。置顶微博没有 `title`；首页时间线里也有 `title`，但形状不同（`{text, base_color, icon_url}`，见到的只有自己转发上的「公开」），没有 `structs`。所以 `Status.activity` 只取带 `structs` 的 `title`，去掉末尾的条数（那份列表打不开），卡片在作者上方显示这行说明，详情页不显示。
- **热搜**：`side/hotSearch` 返回 `data{realtime, hotgovs, hotgov}`。`realtime` 约 50 条加上插入的条目（两次分别是 51、52 条）。排名看 `realpos`（从 1 开始）；`rank` 从 0 开始，而且插入的条目会与相邻条目重复，不能用。**插入的条目没有 `realpos`，`is_ad=1`**，但 `is_ad` 不等于商业推广：一次是付费的（`small_icon_desc`「商」、`icon_desc`「首发」），另一次是「辟谣」。所以标签取 `small_icon_desc`（付费条目上写着「商」），为空时取 `icon_desc`；`is_ad` 但没有任何标签的显示「荐」。`word_scheme` 是 `#word#`（话题）或与 `word` 相同（普通词），用作搜索词。置顶条目在 `hotgovs`（列表）和 `hotgov`（单个，多了 `url`/`mid`）里都有，`word` 带 `#`，没有排名和热度。条目里的 `monitors` 是广告曝光监测地址，我们不请求。
- **应用内 WebView 打开搜索**：桌面 UA 下的 `s.weibo.com` 是 PC 版布局（侧栏占半屏、正文被截断），在手机上没法用；手机 UA 访问时，它会 302 到 **http://**`m.weibo.cn/search?containerid=…&q=…`，话题用 `231522`、普通词用 `100103`。`m.weibo.cn` 对桌面 UA 同样给出手机布局，所以 **WebView 保持与登录页相同的桌面 UA 和 Cookie，直接打开 `m.weibo.cn` 的搜索页**。`m.weibo.cn` 没有登录态（见第 1 项），以游客身份浏览，搜索结果和话题页都能正常显示。WebView 会把 http 的主框架跳转改成 https，否则 Android 会以 `ERR_CLEARTEXT_NOT_PERMITTED` 拒绝。

**M5 实测结论（2026-10-13，诊断页「互动结构」，目标是用户指定的测试微博）：**

- **请求格式**：先从 weibo.com 前端脚本里查到写接口的请求形态（只看接口形态，没有复制代码），再用真实账号核对。所有写操作都是 `POST`，`Content-Type: application/x-www-form-urlencoded`，带 `X-XSRF-TOKEN` 和 `Origin`（拦截器已经在加），空字段不发。网页版还会给点赞、评论、回复额外附一个 `fp` 参数，它来自风控脚本 `wbBotDetector`，脚本加载失败时网页版就不带。**我们不带 `fp`，也不去伪造它**（PLAN §2「不绕过风控」）；实测不带也能正常点赞和评论。
- **点赞**：`setLike` 返回 `ok=1`，外加一个点赞记录对象（`attitude`、`user` 等）。`cancelLike`、`updateLike`（评论点赞）、`destroyLike` 返回 `{ok:1, result:bool}`。**`ok=1` 但 `result:false` 的请求没有生效**：点赞后约 1 秒内取消，服务端返回 `ok=1,result=false`，微博仍是已赞状态；隔几秒再取消就返回 `result=true`，`statuses/show` 立即显示已取消。所以 `result:false` 一律当失败处理，界面回滚并提示。点赞后 `statuses/show` 立即反映新的 `attitudes_status`、`attitudes_count`，没有缓存延迟。
- **评论 / 回复**：返回 `{ok:1, msg:"…", data:<评论>}`，`data` 与 `buildComments` 里的评论同构（`idstr`、`text`、`user`、`created_at`、`source`、`disable_reply` 等），`Comment.tryFromJson` 可以直接解析。回复多了 `reply_comment`、`reply_original_text`、`is_sub_cmt`；直接回复根评论时 `text` 没有「回复@xx:」前缀。微博的 `comments_count` 把回复也算在内（发一条评论、一条回复后 `comments_count` 为 2）。
- **删除评论**：`statuses/destroyComment` 返回 `{ok:1, data:<被删的评论>}`；重复删除返回 HTTP 400 和 `message`。
- **失败的样子**：`{ok:0, message, msg, error_code, error_type}`（`error_type` 为 `toast`/`alert`/`confirm`/`link`），HTTP 200。`unwrapResponse` 已经把它映射成带 `message` 的 `WeiboApiException`。还没见到写操作触发验证码（`ok:-100`），映射与读接口相同。

**通知实测结论（2026-10-15，诊断页「通知结构」，只读）：**

- **接口来源**：weibo.com 前端脚本（`h5.sinaimg.cn/m/weibo-pro-next/assets/index-*.js`，以游客身份用无头 Chrome 取得，只看接口形态）里「消息」页的各个列表：@我的微博 `GET /ajax/statuses/mentions`、@我的评论 `GET /ajax/comments/mentions`、收到的评论 `GET /ajax/message/cmt`、发出的评论 `GET /ajax/message/myCmt`、赞 `GET /ajax/message/attitudes`。前两个和「收到的评论」可加 `filter_by_author=1`（只看关注的人），@我的微博另有 `filter_by_type=1`（原创）。网页版的「24小时全部」`/ajax/message/msgList?type=all` 对测试账号返回「你访问的地址不存在」，不用。
- **列表格式**：都是 `{ok:1, data:{<列表>, next_cursor(int), previous_cursor, total_number, miss_ids, miss_count, filter_hints, …}}`，列表字段分别是 `statuses`、`comments`、`attitudes`。翻页传 `max_id=<上一页的 next_cursor>`，首屏传 0，`next_cursor` 为 0 表示到底；`next_cursor` 小于本页末条的 id。按时间倒序。**`total_number` 包含被折叠的条目**：收到的评论 `total_number=4`、实际返回 2 条，另外 2 条在 `miss_ids` 里（微博判为低质量后折叠，`pop_tips` 有说明文字），网页版不显示它们，我们也不显示。
- **评论类条目**（收到的评论、@我的评论）：与 `buildComments` 的评论同构，`Comment.tryFromJson` 能直接解析；另带完整的 `status`（被评论的微博，`Status.tryFromJson` 能解析）。回复别人的评论时网页版还读 `reply_comment`，这次的样本都是根评论，没有见到。
- **赞的条目**：`{id(int), idStr, created_at, user(点赞的人), status, attitude:"heart", attitude_type, last_attitude, source_type, …}`，注意是 `idStr` 而不是 `idstr`。`status` 有时完整（74 个字段），有时是精简版（22 个字段，只有 `text`、`user`、`mblogid` 等，没有图片和计数），两种 `Status.tryFromJson` 都能解析。网页版的赞条目也可能是评论（`comment` 里再套 `status`），这次没见到。
- **@我的微博里的转发**（2026-10-15 补测）：外层微博完整，但 `retweeted_status`（45 个字段）**没有 `user`，`text` 是空字符串**，正文只在 `text_raw`（纯文本）里。长微博的 `text_raw` 被截断，末尾没有省略号（`isLongText=true`）。`page_info`、计数、`created_at`、`mblogid` 都在。原微博没有 `url_struct`，它正文里的短链接（如「微博正文」）在**外层**的 `url_struct` 里；`url_type_pic` 指向的图标不存在（NoSuchKey）。原微博里没有作者，但**外层**的 `page_info` 是网页版专为消息列表生成的原微博卡片：`{object_type:"meaasgeObjType"（原文如此）, content1:"@作者名", content2(摘要，与 text_raw 不同), page_pic, uidPageInfo(作者 uid), mblogid(原微博的)}`，网页版的消息列表用它（`FeedCardLink`）代替原微博显示，所以能看到作者名。（视频微博的 `page_info.media_info.author_info` 是视频作者，不一定是发微博的人；网页版请求带的 `client-version`/`server-version` 头不影响返回内容，已核对。）m.weibo.cn 的 `message/mentionsAt` 返回 `ok:-100`，用不了。`Status.tryFromJson` 在 `text` 为空时把 `text_raw` 转成 HTML：短链接按外层 `url_struct` 加上标题，`@昵称`、`#话题#` 变成链接，长微博补上「展开」；作者取自外层卡片（`mblogid` 对得上、`content1` 以 @ 开头时），只有 uid 和昵称，没有头像；卡片缺失时原微博没有作者，但有发布时间，仍然可以点开，详情页用 `statuses/show` 取完整内容（有作者）。已删除的原微博既没有作者也没有时间，仍不能点开。
- **未读数**：网页版用 JSONP 每 30 秒请求一次 `https://rm.api.weibo.com/2/remind/push_count.json`（带网页版自己的 `source=339644097`）。不带 callback 时直接返回 JSON，`.weibo.com` 的 Cookie 有效。计数在顶层而不是 `data` 里：`cmt`/`all_cmt`/`attention_cmt`（收到的评论）、`mention_status`/`mention_cmt`（@我）、`attitude`（赞）、`messages`（合计）等；`trim_null=1` 时为 0 的键会被省略。在自己微博下的评论即使带 @，也只计入 `cmt`，`mention_cmt` 仍是 0。
- **请求列表会清掉未读**：取一次 `message/cmt` 后 `cmt`、`all_cmt`、`attention_cmt` 都从 2 变为 0，取一次 `message/attitudes` 后 `attitude` 从 1 变为 0，没有另外的清除请求（网页版也只是把本地计数清零）。所以在 maxbo 里看过之后，官方 App 和网页版的红点也会消失，与在网页版里看一样。

**发微博实测结论（2026-10-18，用户同意后在测试账号上发了两条「仅自己可见」的测试微博，测完都已删除）：**

- **接口来源**：weibo.com 前端脚本（`index-*.js`，以游客身份用无头 Chrome 取得，只看接口形态）里的发布框。发布是 `POST /ajax/statuses/update`，表单字段 `content`、`pic_id`（JSON 数组，每张图 `{type:"pic", pid}`，按顺序）、`visible`，网页版还会发空的 `share_id`/`media`/`vote`，我们不发（空字段不发）。和点赞、评论一样，网页版会附 `fp`，我们不带（见 M5 实测结论）。正文上限取 `$CONFIG.flags.input_limits`，没有时是 5000（中文算 1、英文算半个、链接算 10），客户端不限制，超长时显示服务端的错误。网页版正文为空时不能发，带图也一样。
- **可见范围**：`visible` 为 0 公开、6 好友圈、1 仅自己可见、10 粉丝（另有 5 群可见）；微博本身的 `visible.type` 用同一套数字，刷新后从时间线读回的测试微博是 `1`。maxbo 提供公开、好友圈、仅自己可见三档，非公开的微博在卡片右上角带标签。网页版发完会把可见范围重置为公开，maxbo 也一样。
- **返回**：`{ok:1, msg:"…", data:<微博>}`，`data` 是完整的微博（含 `user`、`pic_infos`），`Status.tryFromJson` 能直接解析；来源显示为「微博网页版」。刚发出时 `data` 里没有 `region_name`，刷新后才有。时间线（`friendstimeline`）里能看到自己「仅自己可见」的微博。
- **上传图片**：网页版发布框用较新的 `picupload.weibo.com/interface/upload.php`（octet-stream，参数里带网页版的 `appid=339644097`、crc32、md5 等）；脚本里还有经典的 `interface/pic_upload.php?app=miniblog&s=json&p=1&data=1`（`Content-Type: multipart/form-data` 但不带 boundary，请求体就是文件本身），实测可用，所以只用它，不带任何 appid。请求跨域：带 `.weibo.com` 的 Cookie 和 `Origin`，**不带** `X-Requested-With` 和 `X-XSRF-TOKEN`（网页版特意去掉；拦截器用 `kPlainCrossOrigin` 区分）。返回 `{"code":"A00006","data":{"count":1,"pics":{"pic_1":{"pid":…}}}}`；失败时 `code` 不同，`data.count` 是负的错误码（如 -10 文件过大，-9 类型不支持）。网页版不压缩图片（GIF 原样），我们也直接传原文件；水印参数（`$CONFIG.user.watermark`）没有带，所以 maxbo 发的图没有水印。dio 的请求体要用字节流（`Stream`），直接给 `List<int>` 会被当成 JSON 文本发出去。
- **删除**：`POST /ajax/statuses/destroy`（`id=`），返回 `{ok:1, <被删的微博的全部字段>}`（微博字段直接在顶层）。删除后刷新时间线和主页都不再出现。

**转发实测结论（2026-10-21，用户同意后用测试账号转发了一次测试微博，选「仅自己可见」并勾了「同时评论」，测完删除了这条转发和产生的评论）：**

- **接口来源**：weibo.com 前端脚本（`index-*.js`，以游客身份用无头 Chrome 取得，只看接口形态）里的转发框。转发是 `POST /ajax/statuses/normal_repost`，表单字段：`id` 是被转发的那条（转发的转发传中间那条，不是原微博）；`comment` 是转发理由，为空时网页版不发，微博自动填「转发微博」；`is_comment=1` 表示同时评论给被转发的那条；`comment_ori`、`is_repost` 都发 0；`visible` 与发微博同一套数字。网页版另有空的 `pic_id`/`share_id`（我们不发），也会附 `fp`（我们不带，见 M5 实测结论）。网页版还有「快转」：`POST /ajax/statuses/repost`（`id`、`status`、`is_comment=0`、`is_fast=1`），maxbo 没有做。转发理由上限 140 字，A/B 测试里取 `push_active.repost_input_limits`（默认 1000）；客户端不限制。
- **转发的转发**：网页版把输入框预填为 `//@中间人:中间人的原文`（取 `longTextContent_raw || text_raw`，表情是 `[流鼻血]` 这样的代码），光标放在最前面；转发原创微博时预填为空。实测预填的转发链发出后，@、链接和表情都正常显示。
- **返回**：`{ok:1, msg, data}`，网页版从 `data.statuses` 读出新微博；maxbo 两种位置都认，实测解析出了完整的转发（作者、正文、「仅自己可见」标签）。返回里被转发的原微博与时间线里的不一样：视频变成了一张「某某的微博」链接卡片，正文里少了「微博正文」链接，外层也没有 `region_name`。刷新时间线后，同一条转发就显示正常了。
- **计数**：转发后，`statuses/show` 立即显示 `reposts_count` +1；勾了「同时评论」时 `comments_count` 也 +1。`repostTimeline` 立即包含这条「仅自己可见」的转发（自己看得到）。同时评论产生的评论，正文与转发理由相同，可以照常用 `destroyComment` 删除。用 `statuses/destroy` 删除转发后，计数恢复为 0。

**评论带图实测结论（2026-10-22，用户同意后在测试微博上发了一条带文字和图片的评论、一条只有图片的回复，修正显示后又发了一条带图评论核对，测完三条都已删除；图片是本地生成的纯色块）：**

- **接口来源**：weibo.com 前端脚本（`index-*.js`，以游客身份用无头 Chrome 取得，只看接口形态）里的评论框。评论和回复仍是 `comments/create`、`comments/reply`，多一个表单字段 `pic_id`，值是图片的 pid，多张时用逗号连接（不是发微博那样的 JSON 数组）。网页版默认只能带一张，`$CONFIG.flags.pc_comment_pics` 为 `"true"` 时才能多选（最多 18 张），maxbo 只做一张。有图时正文可以为空（网页版照发 `comment=`），微博把正文显示为「图片评论」。
- **能否带图**：网页版打开评论框时（转发框除外）请求 `GET /ajax/statuses/checkAllowCommentWithPic?id=<微博 id>`，返回 `{ok:1, result:bool}`；`false` 时图片按钮不可用（作者可以关掉评论配图），请求失败时当作可以。maxbo 等用户第一次点图片按钮时才请求，每次打开弹层最多一次。测试微博返回的是可以，`false` 的情况还没见到。
- **上传**：网页版评论框用较新的 `upload.php`（`file_source=3`）。maxbo 沿用发微博用的经典 `pic_upload.php`（见发微博实测结论），得到的 pid 评论和回复都能用，刷新后图片正常显示。
- **返回**：`data` 仍是一条评论，但**没有 `url_struct`**，图片只体现在 `pic_ids:[pid]` 和 `pic_num:1` 上（另有 `mix_media_ids`）。带文字的评论，`text` 末尾多一个指向图片的 `t.cn` 短链（`<a>` 只有 `target`、`href` 两个属性，没有 `data-pid`）；因为没有 `url_struct`，这个短链显示成「网页链接」。网页版发完也是用自己的 pid 拼出图片（`pidToImgs(pic_ids)`）。maxbo 的 `Comment` 在 `url_struct` 里没有图片时，就从 `pic_ids` 拼出图片（`PicInfo.tryFromPid()`）。pid 第 10 位是 `y` 时，地址为 `https://wx{n}.sinaimg.cn/{thumbnail|bmiddle|large|woriginal}/{pid}.{jpg|gif}`，其中 `n = (crc32(pid) & 3) + 1`；第 22 位是 `g` 时是 GIF。这和 `buildComments` 里两条测试评论的地址一致，wx1–wx4 都能取到同一张图。其他老式 pid 不处理。同时去掉 `text` 末尾的 `t.cn` 链接，每张图去掉一个。
- **列表里的样子**：刷新后，带图评论的 `text` 里没有这个链接，图片在 `url_struct` 里（与 M3 实测结论一致）。只有图片的回复，正文是「图片评论」，后面跟一个「查看图片」链接。

**搜索实测结论（2026-10-20，诊断页「搜索结构」，只读）：**

- **weibo.com 没有现成的搜索结果接口**：前端脚本里「搜索结果」页用的 `GET /ajax/search/all?containerid=…` 已经下线，带登录态请求也返回 404「你访问的地址不存在」；网页版顶部搜索栏直接打开 `s.weibo.com`。
- **m.weibo.cn 只能以游客身份用，而游客只能看第一页**：`/api/container/getIndex?containerid=100103type=1&q=…&page_type=searchall`（话题是 `231522type=1&t=10&q=#话题#`）不带 Cookie 时返回 `ok:-100`；带 WebView 浏览 m.weibo.cn 时拿到的游客 Cookie（`SUB`/`SUBP`，`/api/config` 是 `login:false`）能取到第 1 页（10–17 条，`card_type` 9 是微博，11 是一组卡片，话题的 `cardlistInfo.cardlist_head_cards[].head_data` 是话题头），**第 2 页起返回 `ok:-100`**。应用内 WebView 的搜索页往下滚过第一页也会跳到登录页。微博的格式是 m.weibo.cn 的（`pics[]`、`page_info.urls`、`bid`），与 weibo.com 不同。用 weibo.com 的登录态单点登录 m.weibo.cn（`passport.weibo.com/sso/signin?entry=wapsso&source=wapssowb&url=https://m.weibo.cn/`）也不行：直接请求只得到一个由风控脚本（`wbBotDetector`、易盾）驱动的登录页，不能也不该去模拟；在 WebView 里打开显示的是登录表单，没有自动登录（没有做任何输入就退出了）。
- **`s.weibo.com/weibo?q=`**（桌面 UA，带 weibo.com 的登录态）是已登录的 PC 版 HTML，每页约 20 条，有分页链接，能翻页，但要解析 PC 版 HTML，太脆弱，没有采用。
- **采用 `GET /ajax/statuses/search`**：网页版首页的「搜索我关注的人的微博」用它，带 `atten=1`、`count=10`、`page`（另有 `hasori`/`haspic`/`hasvideo`/`starttime`/`endtime` 等筛选）。**不带 `atten`（或 `atten=0`）时搜的是全站**：10 条结果里没有一条的作者是测试账号关注的人，`total_number` 为 735；带 `atten=1` 时同一个词 0 条。这是网页版前端不会发出的参数组合，但仍是 weibo.com 网页端接口、用登录态、只在用户操作时请求，没有绕过任何验证，判断在第 2 节的边界之内，用户已确认可以这样用。返回 `{ok, statuses[], total_number, absstr, intention, topicHeads, …}`（不在 `data` 里），`statuses` 与时间线同构，`Status.tryFromJson` 直接解析，图片、视频、转发、混排、长文「展开」都有；样本中没有广告（`isAd=false`、`readtimetype=mblog`）。**按发布时间倒序**（不是 m.weibo.cn 默认的「综合」排序）。`page` 从 1 开始，连续 3 页互不重叠（新微博不断进来时偶有 1 条错位，分页控制器会去重）；`count=20` 有效，我们用 20。`total_number` 只是约数（同一个词连续几次在 727–749 之间），话题的讨论数也可能远多于能翻到的条数（245 讨论的话题翻 2 页共 38 条后就是空页），所以只以空页为末页。
- **`topicHeads`**：搜任何词都带。`count` 里 `read`、`mention`、`ori_uv` 是总阅读、讨论、原创人数（另有 `24h_read`、`24h_mention`、`1h_mention` 等短期数）。只有话题的 `object` 里有 `display_name`（话题名，不带 `#`，大小写可能和查询不同）、`summary`（导语，可能为空）、`id`（`1022:231522…`）、`image.url`；普通词的 `object` 只有 `media_counts`、空的 `summary` 和 `image`，所以以有没有 `display_name` 判断是不是话题。另有 `claim_info`（主持人）、`band_info`（在热搜榜上的位置），没有用。

**会话续期实测结论（2026-10-21，用户同意后在模拟器的真实登录态上实验，只看 Cookie 的名字和有效期，没有读值）：**

- **为什么约 7 天就要重新登录**：`.weibo.com` 的 `SUB` 在客户端的有效期是 1 年，但服务端只认约 7 天（10-14 登录，10-21 失效）。登录时还会写下两个 30 天的自动登录凭证，和 `SUB` 是同一时刻写入的：一个是 `.weibo.com` 的 `ALF`，值为 `02_<到期时间戳>`，时间戳与它的有效期一致；另一个是 `.passport.weibo.com` 的 HttpOnly `ALC`。`.sina.com.cn` 上一个 Cookie 都没有（登录的最后一跳是 403，见第 1 项）。原来的 maxbo 有两个问题：接口请求直接打 `/ajax/*`，收到 `-100` 就退出登录；登录页直接打开 passport 的登录表单，而这个表单不会尝试自动登录，所以每次都要短信和人机验证。
- **浏览器怎么续期**（以游客身份看 passport 页面的脚本，只看接口形态）：
  1. weibo.com 把没有有效 `SUB` 的请求 302 到 `passport.weibo.com/visitor/visitor`。
  2. 页面脚本用微博自己的 `wbBotDetector` 取得风控 `rid`，再 POST `/visitor/genvisitor2`。这个请求带着 passport 域的 `ALC`。
  3. 服务端认得凭证时返回 `alt` 票据，页面跳到 `passport.weibo.com/sso/v2/login?entry=sso&source=visitor_restore&type=3&alt=…`，在这里写入新的 `SUB`/`SUBP`。之后经 `login.sina.com.cn/sso/v2/crossdomain`（在 WebView 里同样是 403）回到 weibo.com。
  4. 没有凭证时 `alt` 为空，走游客流程，得到游客 `SUB`，最后停在 `weibo.com/newlogin`。
- **WebView 里有登录后的 weibo.com Cookie（`WBPSESS` 等）时**，weibo.com 先把请求送到老的单点登录网关 `login.sina.com.cn/sso/login.php`。因为没有 .sina.com.cn 的登录态，网关返回 `retcode=6102`，在 `weibo.com/login.php` 和网关之间来回一两次后，同样到 passport 的游客页续期。用假值的 Cookie 名复现不出这条路，说明服务端是按真实的值决定的。
- **续期会顺延 30 天**：续期后 `ALF`、`ALC` 都重新签发，到期时间变成续期时刻加 30 天。带着有效 `SUB` 打开 weibo.com 也会顺延。所以只要 30 天内打开过应用，就能一直保持登录。新旧 `SUB` 可以并存，续期不会让应用手里的那份立即失效。
- **在不显示的 WebView 里能走通**（Android，诊断页「模拟登录过期」）：WebView 不挂在界面上，微博的脚本照常运行，从接口被拒到重发的请求返回共约 5 秒。另外两种情况也测了：
  - WebView 里的 `SUB` 也是无效的：先拿它验证一次，不通就继续等。随后微博清掉它并完成续期，成功。
  - 删掉 `ALC`：约 5 秒得到游客 `SUB`，判定失败并退出登录（测完把 WebView 的 Cookie 库恢复了原样）。

  iOS 没有测过。Kotlin 版（v0.12.0）在 2026-10-23 用真实会话重新核对过（KOTLIN_MIGRATION.md §11「真实会话验收」）。
- **做法**：`WebViewSessionRenewer`（`core/session/SessionRenewer.kt`）用与登录页相同的 UA，在不挂到界面上的 WebView 里打开 weibo.com，每次主框架加载完成或失败都读一次 Cookie。出现没试过的 `SUB` 就交给 `SessionController`，用首页 `$CONFIG` 核对是不是同一个 uid。以下情况算失败：续期途中微博写入的 `SUB` 不是这个账号的（即游客），或者 20 秒内没有结果。结束后载入 `about:blank`，免得 weibo.com 的页面在后台继续轮询。
- **合规**：这和浏览器打开 weibo.com 完全一样。跑的是微博自己的脚本（包括风控脚本），我们不构造也不伪造任何参数。只在用户操作触发的请求被拒时才续期，不轮询。也不做提前刷新：`SUB` 有效时 weibo.com 不经过续期页面，也没找到能提前换新 `SUB` 的接口。

## 9. 分发

- **Android**：GitHub Releases 发布一个通用 APK `maxbo-vX.Y.Z.apk`（`./gradlew assembleRelease`；v0.11.0 及以前的 Flutter 版按 ABI 拆成三个）。签名密钥不进 git：本地密钥库是 `~/.config/maxbo/release.jks`（别名 `maxbo`），密码在仓库根目录的 `key.properties`（已 gitignore，Flutter 时期在 `android/key.properties`）；CI 用的副本存在 GitHub 仓库 `release` 环境的 Secrets 里（见下方 CI）。Secrets 写入后读不回来，不能当备份。证书 SHA-256 是 `A1:7A:66:0B:CA:65:D7:BB:89:D6:F3:07:AB:A4:61:06:F3:F8:AF:CB:90:F5:D0:B3:EC:40:EB:28:6D:71:79:E2`。**密钥丢了，已安装的用户就无法覆盖升级**，这两个文件要另外备份。没有 `key.properties` 时 release 包退回调试密钥签名。调试版的包名带 `.debug` 后缀，名称是「maxbo debug」，和 release 版可以同时安装。国行手机通常没有 GMS，暂不受 Google 开发者验证影响；2027 年该政策全球生效后再评估。
- **iOS**：2026-10-22 起不再提供（§10）。v0.2.0 到 v0.11.0 的 Release 附有无签名 IPA，之后的版本没有。
- **CI**（`.github/workflows/`）：
  - `ci.yml`：push 到 main 和每个 PR 时运行 `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug`。不接触签名密钥。
  - `release.yml`：推送 `v*` tag 时触发，分两个 job。`android`：先核对 tag 和 `app/build.gradle.kts` 的 `versionName` 一致，再跑格式检查、lint 和测试，然后用 Secrets 里的密钥打 release APK，核对签名证书指纹必须等于上面的 SHA-256（防止误用调试密钥），改名为 `maxbo-vX.Y.Z.apk`。`release`：等 `android` 完成，下载产物，生成 `SHA256SUMS.txt`，建一个**草稿** Release。只有 `release` 有写仓库的权限。发布说明手写，确认后手动发布。
  - Secrets（放在 `release` 环境里，只允许 `v*` tag 使用）：`ANDROID_KEYSTORE_BASE64`（`base64 -i release.jks`）、`ANDROID_STORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
  - 发版流程：改 `app/build.gradle.kts` 的 `versionName`，**同时把 `versionCode` 加 1**（关于页显示 `BuildConfig.VERSION_NAME`）→ 提交 → `git tag vX.Y.Z && git push origin vX.Y.Z` → 在 GitHub 上编辑草稿并发布。Release 标题就用 tag 名 `vX.Y.Z`（workflow 默认如此，不要改成别的格式）。

## 10. 已定事项

- **License**：GPL-3.0。
- **Git 提交身份**：仅本仓库配置 `Leon <41661196+leonfox28@users.noreply.github.com>`。
- **框架**：Kotlin + Jetpack Compose（2026-10-22 用户决定，10-23 切换，v0.12.0 起；§3.1）。工具链：AGP 9.4.1、Gradle 9.8.2、Kotlin 2.4.21、JDK 17；compileSdk 37、targetSdk 36、minSdk 24。v0.11.0 及以前是 Flutter stable 3.47.7。
- **包名**：`io.github.leonfox28.maxbo`（v0.1.0 起不再改，换成 Kotlin 时也没改）；调试版加后缀 `.debug`。Kotlin 版的 versionCode 从 5022 起（Flutter 版按 ABI 拆分，最大是 4021），每发一版加 1，所以能直接覆盖安装旧版。
- **平板/折叠屏不做列表加详情的双栏**（2026-10-16 用户决定，10-17 调整）：不做左边列表、右边详情的双栏布局。10-17 改为：导航用悬浮胶囊放在右下角（去掉左侧导航栏）；详情页在 expanded 档把正文和评论/转发左右并排；首页时间线不做瀑布流（会打乱时间顺序），改为卡片加宽、媒体放到正文右侧。
- **不再支持 iOS**（2026-10-22 用户决定）：第三方微博客户端几乎不可能上架 App Store（审核条款 5.2.2 要求第三方服务的授权，国区还要 ICP 备案），只能发无签名 IPA 让用户自签，用免费 Apple ID 签的每 7 天要重签，门槛很高；而且 iOS 一直只验证过能编译和启动。删掉了 `ios/` 工程、CI 和 `release.yml` 的 iOS 构建、侧载文档，以及只对 iOS 有用的代码（Keychain 残留登录态的清理、选图的 `requestFullMetadata`、iOS 的页面转场配置和对应测试）。v0.11.0 是最后一个带 IPA 的版本。框架仍是 Flutter，是否改用 Kotlin 原生还在讨论。

## 11. 进度与交接（更新于 2026-10-23，换成 Kotlin 之后）

下表 v0.11.0 及以前的各行写的是 Flutter 版的实现（`lib/` 下的文件名、provider 等），行为在 Kotlin 版里照样保留；Kotlin 版的对应文件见 KOTLIN_MIGRATION.md §4，当前代码的结构见 CLAUDE.md「架构速览」和下方「已有的基础设施」。

| 里程碑 | 状态 | 提交 |
|---|---|---|
| M0 脚手架 | ✅ 完成 | `1d6888b` |
| M1 登录验证 | ✅ 完成：Android 真实登录通过，重启后保持登录；iOS 的 HttpOnly Cookie 读取已由集成测试验证，真实登录待补测 | `02b3d90` `af0e0e2` `88969f8` |
| M2 首页时间线 | ✅ 完成：Android 真实会话下加载、下拉刷新、滚动翻页（连续 3 页）都正常，图片无 403，浅色和深色主题都核对过；iOS 只验证了能编译 | `984f7bb` `585cc22` |
| M3 详情与媒体 | ✅ 完成：Android 真实会话下核对了详情页（长文全文、评论按热度/按时间、评论翻页、楼中楼、转发页签）、图片查看器（翻页、点击或下滑关闭）、视频播放、深色主题；视频地址过期后自动换新地址（时间线加载 1 小时后点视频，先请求一次 `statuses/show` 再播放，无播放错误）；iOS 只验证了能编译 | `41e7e80` `544dc60` |
| M4 主页与热搜 | ✅ 完成：Android 真实会话下核对了从头像、昵称、@ 进主页（@ 先按昵称查资料再加载微博）、主页翻页、置顶标签、「我」进自己的主页（1 条微博，按 `since_id` 为空判断到底，没有多发请求）、热搜榜（置顶、前三名配色、付费条目的「商」标签、热度）、点热搜条目在应用内打开搜索页（普通词和 `#话题#` 两种），以及浅色和深色主题。正文里的话题链接与热搜条目调用同一个函数，没有在真机上点过，只由 Widget 测试覆盖；iOS 只验证了能编译 | `41a27b1` `bfdda6e` |
| M5 轻互动 | ✅ 完成：Android 真实会话下，在用户指定的测试微博上核对了卡片和详情页点赞/取消（主页、详情页之间状态同步，刷新后与服务端一致）、发评论（出现在列表顶部，计数 +1）、评论点赞/取消、回复评论（楼中楼预览更新）、在楼中楼页删除自己的回复、在详情页删除自己的评论；测试结束后这条微博恢复为未赞、0 评论。深色主题核对过；iOS 只验证了能编译 | `7d12372` `53d9951` |
| v0.1.0 | ✅ 2026-10-13 在 GitHub Releases 发布（Pre-release，只有 Android）。包名改为 `io.github.leonfox28.maxbo`，加了 release 签名。release 包在模拟器上只验证了未登录时的启动、登录页和热搜页，登录后的功能没有在 release 包上测过 | 见 `v0.1.0` 标签 |
| M6 打磨与打包 | ✅ 完成：设置页（主题、图片质量、图片缓存上限、清除缓存）、关于页（声明、隐私、项目链接、开源许可）、自己的应用图标（Android 自适应图标，Android 12+ 启动页也用它）、广告标签误判修正、`release.yml` 增加无签名 IPA、iOS 自签文档、README。Android 真实会话下核对了设置页各项、切换深色主题立即生效、关于页和许可页、新图标和启动页；iOS 模拟器上只验证了未登录时能启动 | `05a5e0b` `5e45c45` `b5dd5e4` |
| v0.2.0 | ✅ 2026-10-14 发布（Pre-release，Android APK 加第一个无签名 IPA），由 `release.yml` 自动构建。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号，arm64 APK 能在模拟器上覆盖安装 v0.1.0 | 见 `v0.2.0` 标签 |
| v0.3.0 | ✅ 2026-10-14 发布（Pre-release，APK + 无签名 IPA），以界面调整为主：悬浮胶囊导航栏（滚动时收起/出现）、三个页签去掉标题栏、详情页顶栏和评论栏随滚动收起、设置入口移到「我」页列表、「通知」页签占位。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.3.0），arm64 APK 能在模拟器上覆盖安装并启动（未登录状态） | `919bf2d` `204ee5c` `f281faa`，见 `v0.3.0` 标签 |
| v0.3.1 | ✅ 2026-10-14 发布（Pre-release，APK + 无签名 IPA）：首页和热搜再点一次当前页签，不在顶部就回到顶部，已在顶部就刷新。调试版在真实会话下验证过这两个页签。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.3.1，build 4），arm64 APK 能在模拟器上覆盖安装 0.3.0 并启动（未登录状态） | `f6a72a5`，见 `v0.3.1` 标签 |
| 通知页 | ✅ 完成（v0.4.0 发布）：「通知」页签分为评论、@我的微博、@我的评论、赞四个列表，每个列表第一次打开时才加载，可下拉刷新、翻页，切换后保持已加载的内容；点通知打开对应的微博。打开应用、登录后，以及回到前台或下拉刷新首页（含再点一次首页页签触发的刷新）时（后两种一分钟内最多一次）取一次未读状态；首页往下翻页时不取，有未读时导航栏的通知图标变红（选中时不变，免得红色压在主题色上），页内对应页签的右上角带红点，不显示数字。红点只在用户进入那个页签时清除（检查只会加红点，不会去掉），所有红点都清除后图标才恢复原色。进入带红点的页签时，如果列表是之前加载的就重新加载；微博那边在请求列表时就清掉了这一类未读。Android 真实会话下核对了四个页签、点通知进详情页、浅色和深色主题；用户从大号评论测试账号后，下拉刷新首页使通知图标变红，停在「赞」页签时「评论」右上角出现红点，点进「评论」后重新加载出新评论、红点消失，回到首页图标恢复原色 | `ce603ad`（探针） `3653b18` `a504e02` `a2e6e41` `8f99549` |
| v0.4.0 | ✅ 2026-10-15 发布（Pre-release，APK + 无签名 IPA）：通知页签和未读提醒。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.4.0，build 5），arm64 APK 能在模拟器上覆盖安装 0.3.1 并启动（未登录状态）。发布说明提醒了：在 maxbo 里打开某类通知列表，微博那边也会把这一类标为已读 | `69ba341`，见 `v0.4.0` 标签 |
| v0.4.1 | ✅ 2026-10-15 发布（Pre-release，APK + 无签名 IPA）：「@我的微博」里被转发的原微博不再是空灰框，改为显示作者和正文（取自 `text_raw`，链接、@、话题可点，作者取自转发微博的 `page_info` 卡片）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.4.1，build 6），arm64 APK 能在模拟器上覆盖安装 0.4.0 并启动（未登录状态） | `efde170` `88f4842` `a01044f` `791296b`，见 `v0.4.1` 标签 |
| 平板/折叠屏（第一步） | ✅ 完成（v0.5.0 发布）：按窗口宽度（不是屏幕宽度，分屏和折叠时都对）分档，小于 600 为 compact、600–839 为 medium、840 以上为 expanded。expanded 档首页改用左侧导航栏（未读变红、再点一次当前页签都保留），其他档仍是悬浮胶囊；各页正文限宽 640 居中（列表本身仍占满窗口，两侧空白处也能拖动），通知页的页签栏和详情页、楼中楼页的评论栏跟正文同宽。Android 真实会话下用 `wm size` 把 emulator-5554 临时改成平板横屏（2560×1600@320，1280dp）和折叠屏内屏（1840×2208@420，约 700dp）核对了首页、详情页、通知页，应用运行中切换尺寸后页签和滚动位置都保留，看完已 `wm size reset`。iPad 只有 Widget 测试，没在模拟器上看过 | `b069486` |
| v0.5.0 | ✅ 2026-10-16 发布（Pre-release，APK + 无签名 IPA）：平板/折叠屏适配（minor 版本），以及两处带图评论的修复：直接回复带图评论的回复不再重复显示被回复评论的图片，带图回复正文里的「查看图片」链接在评论列表里隐藏（楼中楼预览不显示图片，保留链接）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.5.0，build 7），arm64 APK 能在模拟器上覆盖安装 0.4.1 并启动（未登录状态） | `e77362f` `5d162f9`，见 `v0.5.0` 标签 |
| v0.5.1 | ✅ 2026-10-17 发布（Pre-release，APK + 无签名 IPA）：修复多视频、视频加图片混排的微博不显示任何媒体（`mix_media_info`，见 §8）。Android 真实会话下核对了转发里的三视频微博（时长与官方一致，点第二个播放的是第二个视频）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.5.1，build 8），arm64 APK 能在模拟器上覆盖安装 0.5.0 并启动（未登录状态） | `591f9e9`，见 `v0.5.1` 标签 |
| 平板/折叠屏（第二步） | ✅ 完成（v0.6.0 发布）：① expanded 档首页的导航改回悬浮胶囊，放在右下角（距右边 16），去掉左侧导航栏；胶囊约 296dp 宽，窗口约 1264dp 以上才完全落在 640 栏外，更窄时会压住正文右下角（往下滚会隐藏）。② 详情页在 expanded 档左右并排：左边正文，右边转发/评论页签和列表，两边各自滚动，评论栏在右栏底部；两栏各最宽 640，居中（`ReadingPanes`）。窗口在 compact/medium 与 expanded 之间切换时，详情页的滚动位置不保留。③ 首页时间线在窗口 ≥ 840dp（与 expanded 档相同）时加宽到最多 1000（`ReadingList(feed: true)`、`feedColumnWidth()`），卡片宽度 ≥ 840 时把图片、视频、链接卡片或被转发的原微博放到正文右侧（正文栏固定 400，媒体拿剩下的宽度：1000 宽时约 544，840 宽时约 384；原来是媒体占 45%、最宽 420，正文 524 太宽、短微博下大片空白，10-17 按用户反馈倒过来），纯文字微博正文靠左、最宽 640，右侧留空（铺满 968 一行超过 50 字太长，居中会让左边缘参差）。计数栏在左栏：正文比媒体高时紧跟正文，媒体更高时放在卡片底部，与媒体下沿平齐（v0.6.1 之后按用户反馈改的，原来计数栏单独占一行，媒体下面会空出一段；用自定义布局 `_WideLayout` 实现，因为 `PicGrid` 用了 `LayoutBuilder`，不能用 `IntrinsicHeight`）；详情页里的卡片不横排。主页、通知等其他列表仍是 640 单栏。Android 真实会话下用 `wm size` 看了平板横屏（2560×1600@320，1280dp）的首页（视频、两图、转发多视频）和详情页，以及折叠屏横放（2208×1840@420，约 841dp）的首页和详情页，看完已 `wm size reset`。1280dp 时首页卡片右侧媒体栏的底部会被胶囊盖住一部分。v0.6.0 的门槛是 880、媒体栏固定 420，用户的折叠屏展开后约 850dp，到了 expanded 档（胶囊在右下角）却没有横排，之后改为现在的 840 和按比例，用 2208×1840@416（849dp）核对过 | `f6aba6a` `c12a320` `aa07390` |
| v0.6.0 | ✅ 2026-10-17 发布（Pre-release，APK + 无签名 IPA）：宽屏布局第二步（minor 版本）：导航胶囊放在右下角、详情页正文和评论左右并排、首页卡片横排。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.6.0，build 9），arm64 APK 能在模拟器上覆盖安装 0.5.1 并启动（未登录状态） | `1300ea7`，见 `v0.6.0` 标签 |
| v0.6.1 | ✅ 2026-10-17 发布（Pre-release，APK + 无签名 IPA）：折叠屏展开后（约 850dp）首页卡片也横排（门槛从 880 降到 840，媒体栏按比例收窄）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.6.1，build 10），arm64 APK 能在模拟器上覆盖安装 0.6.0 并启动（未登录状态） | `b46cca5`，见 `v0.6.1` 标签 |
| v0.6.2 | ✅ 2026-10-17 发布（Pre-release，APK + 无签名 IPA）：宽屏卡片的计数栏放在正文一侧，媒体更高时贴卡片底部，媒体下面不再留空（用户在折叠屏上反馈）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.6.2，build 11），arm64 APK 能在模拟器上覆盖安装 0.6.1 并启动（未登录状态） | `c289a15`，见 `v0.6.2` 标签 |
| v0.6.3 | ✅ 2026-10-17 发布（Pre-release，APK + 无签名 IPA）：宽屏卡片正文一侧固定约 400，媒体拿剩下的宽度（1000 宽时约 544）；纯文字微博正文靠左、最宽 640（用户反馈正文一侧太宽、短微博下大片空白）。调试版在 emulator-5554 上用 `wm size 2700x1800`（约 1028dp）看过首页，看完已 `wm size reset`。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.6.3，build 12），arm64 APK 能在模拟器上覆盖安装 0.6.2 并启动（未登录状态） | `7491a28`，见 `v0.6.3` 标签 |
| 发微博 | ✅ 完成（v0.7.0 发布）：首页页签右下角的「写」按钮（在导航胶囊上方，随胶囊一起收起/出现，只在已登录时显示）打开 `/compose`：正文、最多 9 张图（系统的图片选择器，都不申请权限。Android 打开了插件的 `useAndroidPhotoPicker`：Android 13 及以上、以及有 Google Play 服务的 11–12 用系统 Photo Picker，能限制张数；其他设备退回系统的文档选择器，不能限制张数，多选的部分不加入并提示。插件默认关着它，那样 16 以下的系统走 `ACTION_GET_CONTENT`，各厂商界面不一。iOS 用 PHPicker，不请求完整元数据，所以不弹相册权限）、可见范围（公开/好友圈/仅自己可见）。点「发送」时才逐张上传图片，再发布；失败时原因显示在页面里，已上传的图片记着 pid，再发不会重传。草稿（正文、图片、可见范围）在关掉页面后保留，发出或换账号后清空。发出后新微博放到已打开的首页时间线和自己主页的顶部（没打开的列表不请求）。自己的微博（时间线、主页、详情页）右上角有 ⋯，点后「删除」并二次确认，删除后从已打开的列表移除，在详情页删除会退回上一页；删除菜单（和删评论共用的 `confirmDelete()`）用根导航器弹出，不会被导航胶囊挡住。非公开的微博带「仅自己可见」「好友圈」「粉丝」标签。Android 真实会话下（用户同意后）发了一条纯文字、一条两张图的「仅自己可见」测试微博，刷新后从服务端读回一致，再分别从首页和详情页删除，账号主页恢复为原来的 1 条微博；测试图片已从模拟器相册删掉。iOS 只验证了能编译 | 见 git log |
| v0.7.0 | ✅ 2026-10-18 发布（Pre-release，APK + 无签名 IPA）：发微博（含图片、可见范围）、删除自己的微博（minor 版本），以及链接卡片不再重复正文（`29d5c42`）。这个修复 10-17 已打了 `v0.6.4` tag，CI 也生成了草稿，但一直没发布；用户决定并入 0.7.0，删掉了 0.6.4 的草稿，`v0.6.4` tag 保留。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.7.0，build 14，含相册用途说明），arm64 APK 能在模拟器上覆盖安装 0.6.4（之前装的草稿版）并启动（未登录状态） | `64a66c0` `a52dcf7`，见 `v0.7.0` 标签 |
| 搜索页签 | ✅ 完成（v0.8.0 发布）：「热搜」页签改为「搜索」（放大镜图标，路由 `/hot` 改为 `/search`，代码在 `features/search/`）。顶部是搜索栏（与正文同宽），按键盘上的搜索键后在应用内 WebView 打开 `weiboSearchUrl()`，与点热搜条目相同（`#话题#` 按话题搜），空白不搜；返回后输入的内容还在，有内容时显示清除按钮。下面仍是热搜榜，热搜加载失败时搜索栏照常可用。打开结果前、点搜索栏以外的地方（包括切换页签）、拖动列表时收起键盘。Android 真实会话下核对了搜索 `flutter`（以游客身份显示 m.weibo.cn 的结果）、返回后键盘不再弹出、切换页签时键盘收起，以及浅色和深色主题 | `9542971` |
| v0.8.0 | ✅ 2026-10-20 发布（Pre-release，APK + 无签名 IPA）：「热搜」页签改为「搜索」，顶部是搜索栏（minor 版本）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.8.0，build 15），arm64 APK 能在模拟器上覆盖安装 0.7.0 并启动（未登录状态） | `9542971`，见 `v0.8.0` 标签 |
| 原生搜索结果 | ✅ 完成（v0.9.0 发布）：搜索栏、热搜条目和正文里的 #话题# 不再打开 WebView，而是打开 `/results?q=`（`SearchResultsPage`）：`statuses/search` 的结果按时间倒序，用与首页相同的微博卡片（点赞、进详情、看图、播视频都一样），滚动加载、下拉刷新，翻到空页为止。话题在列表上方显示话题图、名字、「阅读 · 讨论 · 原创人数」和导语；普通词没有话题头。在结果页里点同一个话题不会再打开一遍。右上角的地球图标在应用内 WebView 打开网页版结果（m.weibo.cn，有「综合」排序和用户、视频等分类，但游客只有第一页）。自己的微博在结果页里删除后从列表移除。诊断页的探针按钮放不下，改成可横向滚动的一行。Android 真实会话下核对了：首页转发里的话题进详情、再点话题进话题页（有话题头，第 2 页 18 条，第 3 页为空后显示「没有更多了」）、热搜第一条（话题头带导语，视频卡片和「展开」正常）、输入 `flutter` 搜索（无话题头）、网页版入口、深色主题 | 见 git log |
| v0.9.0 | ✅ 2026-10-20 发布（Pre-release，APK + 无签名 IPA）：搜索结果、热搜条目和正文里的 #话题# 改为应用自己的列表，话题带话题头（minor 版本）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.9.0，build 16），arm64 APK 能在模拟器上覆盖安装 0.8.0 并启动（未登录状态） | `70c5009`，见 `v0.9.0` 标签 |
| 字体大小 | ✅ 完成（v0.9.1 发布）：回应 GitHub issue #1。设置页「外观」下的「字体大小」进入 `/text-size`（`TextSizePage`）：上面是示例微博和示例评论（用真的 `StatusCard`、`CommentTile`，点了不起作用），下面是横向滑动条，标准居中，左右各两档：特小（0.8）、小（0.9）、标准、大（1.15）、特大（1.3），两端的 A 按钮各调一档（到头时变灰），拖动或点按都立即生效并保存。倍数乘在系统字体大小上（`TextSize.applyTo()`，保留系统的非线性缩放），由 `MaxboApp` 的 `builder` 套在 `MediaQuery.textScaler` 上，所以所有页面、弹窗、提示条都跟着变，示例也就是应用本身在这个字号下的样子；正文里的表情图标由 `WidgetSpan` 自动按字号缩放。档位名放在滑动条上方，字号变化时滑动条不会在手指下移动；两端的 A 固定大小，不随字号缩放。Widget 测试在 360 宽的窗口、特大字号下走一遍首页、详情页（含评论）、搜索、通知、我、设置页、弹窗和字体大小页，没有溢出（临时调到 3 倍时会报两处溢出，说明测试能发现问题）。Android 真实会话下核对了：拖到特大、拖到特小、点 A 按钮、到头时 A 变灰、首页（卡片头部一行放不下时省略号截断）、详情页、「我」页、重启后保持、深色主题，核对完已改回标准。issue 评论里提的浏览历史、我的关注还没做 | 见 git log |
| v0.9.1 | ✅ 2026-10-20 发布（Pre-release，APK + 无签名 IPA）：字体大小（滑动条，标准左右各两档，带预览）。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.9.1，build 17），arm64 APK 能在模拟器上覆盖安装 0.9.0 并启动（未登录状态），release 包里打开过字体大小页。发布后在 issue #1 回复了字体大小已添加，并说明评论里提的浏览历史和「我的关注」还没做；issue 保持打开 | `8fd7ab4`，见 `v0.9.1` 标签 |
| 转发 | ✅ 完成（v0.10.0 发布）：卡片上的转发数和详情页底部条的「转发」打开转发弹层（`repostStatus()`，`interactions/repost.dart`）。弹层里有输入框、可见范围（与发微博共用 `VisibilityButton`）、「同时评论」和「转发」按钮，可以不写字。转发的转发预填 `//@作者:原文`，光标在最前；列表里被截断的长转发，先请求一次 `statuses/show` 取全文再预填。草稿按微博 id 保存（`repostDraftsProvider`，换账号清空）。发出后，新转发加到已打开的首页、自己主页和这条微博的转发列表顶部，详情页的转发数本地 +1。Android 真实会话下核对了：时间线里转发的转发的预填；在详情页转发（仅自己可见、同时评论）后，转发数 +1、新转发出现在转发列表顶部、首页顶部出现带「仅自己可见」标签的转发；刷新后服务端为转发 1、评论 1。随后删除了评论和转发，测试微博恢复为 0 转发、0 评论 | `906d7ce` |
| v0.10.0 | ✅ 2026-10-21 发布（Pre-release，APK + 无签名 IPA）：转发（可以不写字、同时评论、选可见范围，转发的转发预填转发链），属于新功能，所以升 minor 版本。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.10.0，build 18），arm64 APK 能在模拟器上覆盖安装 0.9.1 并启动（未登录状态） | `0f49d09`，见 `v0.10.0` 标签 |
| 会话续期 | ✅ 完成（v0.10.1 发布）：之前大约每 7 天要重新登录一次（要短信和人机验证），现在接口报登录过期时，先在不显示的 WebView 里打开 weibo.com，让微博用 30 天的自动登录凭证 `ALC` 换新的 `SUB`，确认是同一个 uid 后继续使用；读请求续期后重发一次，写请求提示再试一次。每次续期都会把 30 天往后顺延。Android 真实会话下用诊断页「模拟登录过期」核对了三种情况：WebView 里有有效 `SUB`（直接采用）、WebView 里的 `SUB` 也无效（经 login.sina.com.cn 和游客页续期成功，约 5 秒）、删掉 `ALC`（约 5 秒判定失败并退出登录，测完恢复了 WebView 的 Cookie）。iOS 没有测过 | 见 git log |
| v0.10.1 | ✅ 2026-10-21 发布（Pre-release，APK + 无签名 IPA）：登录过期后自动续期（不再约每 7 天重新登录）；用户主页里他人赞过、评论过的微博带上「他10-17赞过的微博」这类说明；用户主页和搜索结果的顶栏随滚动收起；主页头部加载时用灰色占位条；主页头部、话题头下面改为细线；卡片计数、评论点赞、详情页底部条去掉按压色块。没有新功能，所以升 patch 版本。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.10.1，build 19），arm64 APK 能在模拟器上覆盖安装 0.10.0 并启动（未登录状态）。续期还没经历过一次真正的 7 天到期（应用里的 `SUB` 是 10-21 续期的，约 10-28 失效，到时看 logcat 的 `session renewal`） | `cf0acf3`，见 `v0.10.1` 标签 |
| 页面转场与预测性返回 | ✅ 完成（v0.10.2 发布）：go_router 18 改用 material_ui 后，只认 material_ui 的 `MaterialApp`，maxbo 用的是 Flutter 自带的那个，于是 `builder:` 路由一直拿到的是 `NoTransitionPage`：打开页面没有转场，Android 的预测性返回和 iOS 的边缘右滑返回都不起作用（M0 起就是这样）。现在页签之上的页面都经 `_page()` 用 Flutter 的 `MaterialPage`，并在 manifest 里加了 `android:enableOnBackInvokedCallback="true"`（Android 16 模拟器上实测，targetSdk 36 并不会默认开启）。Android 16 模拟器上（adb 注入的手势）核对了：设置页侧滑时页面缩小、跟手，松手返回「我」页，慢慢滑回边缘再松手则取消；在页签根页面侧滑时，系统预览退出应用后的界面。新增页面时也要用 `pageBuilder: (_, state) => _page(state, ...)`，不要用 `builder:`。iOS 的右滑返回只由 Widget 测试覆盖，没在模拟器上试过 | 见 git log |
| v0.10.2 | ✅ 2026-10-21 发布（Pre-release，APK + 无签名 IPA）：页面转场、Android 预测性返回、iOS 右滑返回。属于修复，没有新功能，所以升 patch 版本。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.10.2，build 20），arm64 APK 的 manifest 带 `enableOnBackInvokedCallback`，能在模拟器上覆盖安装 0.10.1 并启动（未登录状态），release 包里在设置页侧滑时页面跟手缩小、松手返回 | `687dbe5`，见 `v0.10.2` 标签 |
| 评论带图 | ✅ 完成（v0.11.0 发布）：详情页和楼中楼页的评论/回复弹层左下角加了图片按钮，可以带一张图：从系统相册选，弹层里显示缩略图，可以移除；有图时可以不写字。第一次点图片按钮时先请求 `checkAllowCommentWithPic`，不能带图时按钮变灰并提示。图片在点「发送」时才上传，发送失败后再发不会重传；图片和文字一起存在草稿里，关掉弹层不丢。发出后新评论立即显示图片（用 pid 拼地址）。`picturePickerProvider` 移到了 `compose/picture_picker.dart`。Android 真实会话下核对了：带文字和图片的评论、只有图片的回复（显示「图片评论」）都发送成功，刷新后图片正常；修正后发的第三条评论发出后立即显示图片。随后删除了三条测试评论，测试微博恢复为 0 评论。发出后去掉末尾「网页链接」只由单元测试覆盖（第三条评论是在这项修正之前发的）。iOS 没有测过 | 见 git log |
| 预测性返回卡顿 | ✅ 完成（v0.11.0 发布）：Android 上侧滑返回时页面跟手卡顿。profile 版在 Android 16 模拟器上抓 VM timeline 查到两个原因：① 手指拖动期间只有一半帧率：返回进度每帧一条，刚好在 vsync 之后到达，抢在这一帧开始前处理掉时不会再要下一帧，下一条进度就要多等一帧，于是隔一个 vsync 才出一帧（每帧只要约 6 ms，仍是 30 fps）；② 页面缩小时每帧都按新的缩放比例重新栅格化文字，中文字形多，文字多的页面（关于页、长微博）光这一项每帧 20～35 ms。现在 `lib/core/page_transitions.dart` 包了 Flutter 默认的 `PredictiveBackPageTransitionsBuilder`（经 `ThemeData.pageTransitionsTheme`），手势期间用一个 Ticker 保证每个 vsync 都出帧，并用 `SnapshotWidget` 把被拖走的页面和下面露出的页面各画一次、之后当图片缩放（与 Android 自己缩放窗口的做法一致，手势期间页面上的动图、视频暂停不动）。同一时段对比（模拟器内存紧张，绝对数字只能参考）：文字多的详情页每秒实际呈现的帧从 11～14 升到 28～37，关于页从 9～16 升到 25～40；手势期间文字栅格化降为 0。「是否在手势中」监听 navigator 的 `userGestureInProgressNotifier`，不能在构建时读一次：页面已回到原位时取消手势，路由不会再重建（有测试覆盖）。没在真机上测过 | 见 git log |
| v0.11.0 | ✅ 2026-10-22 发布（Pre-release，APK + 无签名 IPA）：评论和回复带图、Android 侧滑返回更流畅。评论带图是新功能，所以升 minor 版本。下载后核对过校验值、三个 APK 的签名证书、IPA 的版本号（0.11.0，build 21），arm64 APK 能在模拟器上覆盖安装 0.10.2 并启动（未登录状态），关于页显示 0.11.0，release 包里在关于页侧滑时页面跟手缩小、文字清晰，松手返回设置页 | `5a68e34`，见 `v0.11.0` 标签 |
| 停止支持 iOS | ✅ 完成（未发布）：用户决定只做 Android（§10）。删掉了 `ios/` 工程（含读 Cookie 的 `AppDelegate.swift`）、CI 的 iOS 模拟器构建、`release.yml` 的无签名 IPA（Release 里只剩 APK 和 `SHA256SUMS.txt`）、`docs/ios-sideload.md`；README、CLAUDE.md 和关于页改成只写 Android（关于页和 README 的隐私说明都改为「Android Keystore」）；去掉了只对 iOS 有用的代码：首次启动清理 Keychain 里残留的登录态（Android 上卸载即清除，又关了备份，这一步本来就不起作用）、选图的 `requestFullMetadata: false`（Android 插件不使用它）、iOS 的页面转场配置和 iOS 右滑返回的 Widget 测试。`integration_test` 以后要在一台没有真实登录的 Android 模拟器上跑，本机还没有这样的 AVD | 见 git log |
| 迁移到 Kotlin | ✅ 完成（v0.12.0）：用户 10-22 决定把 maxbo 从 Flutter 整体迁移到 Kotlin + Jetpack Compose，功能和交互与 v0.11.0 对齐。分 K0–K7 八个阶段，每个阶段都把对应的 Dart 测试翻译过来（共 345 个测试），并在 emulator-5554 的真实会话上逐项验收（发微博、评论、点赞、转发、删除都在测试微博上做过并恢复原状）。10-23 切换：Kotlin 工程移到仓库根目录，删掉 Flutter 代码；调试版包名换回 `.debug`（覆盖了 Flutter 调试版，WebView 的 Cookie 还在，打开登录页就直接登上了）；CI 只剩 Gradle，`release.yml` 改成出一个通用 APK。不做新旧版本在用户手机上的数据交接：覆盖安装后要重新登录、重新设置。计划、各阶段的备注和验收记录见 [KOTLIN_MIGRATION.md](KOTLIN_MIGRATION.md) | 见 git log |

### 已有的基础设施

代码结构见 CLAUDE.md「架构速览」，这里记的是用法和容易弄错的规则。路径相对于 `app/src/main/java/io/github/leonfox28/maxbo/`。

- **网络**：`AppGraph.api`（`data/api/WeiboApi.kt`）。新端点加成 `WeiboApi` 的方法，读用 `getJson()` / `getPage()`，写用 `postForm()`（表单提交，`ok=1` 但带 `error` 的也当失败），统一完成错误映射和登录过期处理。请求头、Cookie、XSRF、限流都由 `WeiboHttp` 负责，调用方不用操心。诊断类的只读请求用 `api.withoutRenewal()`，异常的回应不会让用户退出登录。
- **会话**：`AppGraph.session.state` 为 `SessionState.SignedIn(account)` 时可拿到 `account.uid`。接口报登录过期时，`WeiboApi` 先调 `SessionController.renewOrExpire()` 续期（见 §8「会话续期实测结论」）：
  - 同时被拒的请求共用一次续期；在续期之前发出、续期之后才被拒的请求直接重发。
  - 续期成功后 `getJson()`/`getPage()` 重发一次；`postForm()` 和上传不重发，提示「登录状态已更新，请再试一次」。
  - 续期不会改变会话状态，页面不会因此重新加载。
  - 续期失败才切到 `SignedOut(expired = true)`，界面统一显示 `LoginPrompt`。

  测试里用 `FakeSessionRenewer`，`testGraph()` 默认已经带上。
- **错误类型**：`SessionExpiredException` / `CaptchaRequiredException` / `RateLimitedException` / `NetworkException` / `WeiboApiException`（`core/net/WeiboException.kt`），都带有可以直接展示的中文 `message`；界面上用 `loadErrorMessage(error)` 取。
- **不自动重试**：OkHttp 里没有重试拦截器；读请求保留 OkHttp 连接层的 `retryOnConnectionFailure`（复用的连接被关掉时换一条，浏览器也这样），写请求的 client 关掉它，免得评论发两次。
- **模型**（`data/model/`）：`Status`、`WeiboUser`、`PicInfo`、`VideoInfo`、`PageCard`、`UrlEntity`、`FeedPage`、`Comment`、`Paged<T>`、`UserProfile`、`HotSearch`、`Topic`、`Notice` 等，都用 `tryFromJson`/`fromJson` 容错解析，读字段用 `core/util/JsonRead.kt`。时间、计数、时长的格式化在 `core/util/Format.kt`。`picsOf(json)` 解析任何带 `pic_ids`/`pic_infos` 的对象。
- **分页列表**：`PagedController<T>`（`core/paging/`）实现首屏、`loadMore()`、去重和过期结果丢弃（刷新、换账号后，之前发出的翻页结果作废），子类只需写 `fetchPage(cursor)` 和 `idOf(item)`，游标是字符串（`max_id` 或页码）。用户自己的改动用 `prepend()`、`remove()`、`replace()` 直接改列表（同时调整 `total`）。界面用 `pagedItems()`（`ui/widget/PagedItems.kt`：加载中、错误重试、空列表、页脚）和 `LoadMoreTrigger`（离末尾不到 800dp 时加载下一页）。示例见 `ui/comments/CommentControllers.kt`。
- **图片**：`weiboImageLoader()`（`core/media/WeiboImage.kt`）是全应用共用的 Coil `ImageLoader`（`AppGraph.imageLoader`），带 Referer，http 改成 https，内存和磁盘缓存的 key 都去掉签名参数；列表尺寸用 `PicInfo.variantFor(像素)` 选，像素由 `ImageQuality.listPixels()` 算，查看器用 `ImageQuality.viewerVariant(pic)`。
- **视频**：`core/media/VideoSource.kt` 的 `resolveVideoUrl()` 负责选地址、判断过期、必要时重新请求 `statuses/show`；播放请求带 `VideoHttpHeaders`。播放出错时换一次新地址，从原位置接着播。
- **路由与入口**：`Route`（`ui/navigation/Route.kt`）：`StatusDetail`、`Comments`（楼中楼）、`Video`、`Viewer`、`Profile`、`ProfileByName`（@ 用）、`SearchResults`、`Web`（应用内 WebView，`onClose` 用于「去验证」后重试）、`Compose`、`Settings`、`TextSize`、`About`、`Licenses`、`Diagnostics`，可以带上上一页已有的数据（`initial`）。卡片和评论上的各种点击统一走 `rememberStatusCardActions()`（`ui/status/StatusActions.kt`）：点卡片和「展开」进详情，图片进查看器，视频进播放页，头像和昵称进主页，@ 按昵称进主页，话题打开搜索结果页（`openSearchResults()`），网页链接用外部浏览器打开（`openExternally()`）。只想改其中一项时用 `copy()`（例如主页里点这个用户自己的头像、话题页里点这个话题都不再跳转）。
- **应用内 WebView**：`Route.Web(url)`（`ui/webview/WebPage.kt`）。它与登录页共用 WebView 的 Cookie 存储，UA 也相同，只放行 http(s)（http 会改成 https），可以后退、刷新或用外部浏览器打开。网页版搜索结果的地址用 `weiboSearchUrl(query)`。
- **验证码**：`ErrorRetry` 和列表页脚遇到 `CaptchaRequiredException` 时显示「去验证」，用户在应用内 WebView 里自己完成验证，返回后自动重试一次。
- **弹层和对话框**：Compose 没有挂在导航栈上的弹层，用 `LocalOverlays.current.show { … }`（`ui/widget/Overlays.kt`）把弹层或对话框放到整个应用上方，挂起到关闭并返回结果。对话框用 `AppAlertDialog`；自己写的窗口内容要套 `KeepTextSize`，否则字体大小设置不生效。提示条经 `LocalSnackbars`，出错用 `showError()`（验证码时带「去验证」）。
- **详情页**：`StatusDetailViewModel` 挂在详情页的导航条目上，先显示上一页传来的 `Status`，再请求全文和最新计数。评论（按热度、按时间各一个）和转发列表在页面存续期间保持已加载状态，切换页签或排序不会重复请求。
- **互动**（`ui/interactions/`）：
  - `LikesController`（`Likes.kt`，`AppGraph.likes`）按 `(kind, id)` 保存用户在应用里改过的点赞，所有列表和页面共用。每条改动记着改动时页面数据的样子，**只有页面数据还是那个样子时才套用改动**；之后加载的数据已经包含这次改动，以新数据为准。界面用 `rememberLike()`：点了立即显示，请求期间不能再点，失败时回滚并提示。点赞在 `appScope` 上进行，离开页面也会完成或撤回。
  - `showCommentComposer()`（`CommentComposer.kt`）是写评论/回复的底部弹层：发送中不能关、失败时在弹层里显示原因、关掉弹层不丢草稿（草稿按回复对象存在页面的 ViewModel 里）。可以带一张图，选图前先问能否带图，发送时才上传，上传成功的 pid 留在草稿里，重发不再上传。`CommentBar` 是详情页和楼中楼页底部的输入条，`confirmDelete()` 是删除前的确认。
  - `rememberStatusWrites()`（`StatusWrites.kt`）提供转发和删除自己的微博。转发发出后把新转发加到已打开的首页、自己主页和转发列表顶部（经 `OpenLists`，不为此发请求）。转发草稿在 `AppGraph.repostDrafts`。
  - 详情页：底部条写评论、转发和点赞，点评论回复它，长按自己的评论删除。楼中楼页：底部条回复根评论，点任一条回复它，长按自己的回复删除，改动同时套用到详情页里那条评论的预览上。评论数在详情页本地加减，刷新后以服务端为准。
- **设置**（`core/settings/`）：`AppGraph.settings` 是 `SettingsController`，状态是 `AppSettings`（主题、`TextSize`、`ImageQuality`、图片缓存上限），存在 DataStore 里，`AppGraph.create()` 在第一帧前读入，避免主题闪一下。改设置用 `settings.update { it.copy(...) }`，会立即生效并在 `appScope` 上保存。测试用 `InMemorySettingsStorage`。
- **图片磁盘缓存**（`core/media/ImageDiskCache.kt`）：Coil 的磁盘缓存放在 `cacheDir/image_cache`，平时就按 LRU 清理。调整上限时 `ImageDiskCache` 在同一目录上按新上限重建，并立即清到新上限之内。设置页经 `PictureCache` 读大小、清除和改上限，测试用 `FakePictureCache`。
- **设置页和关于页**：`Route.Settings`（入口是「我」页列表里的「设置」，未登录时也有）、`Route.TextSize`（字体大小）、`Route.About`、`Route.Licenses`（AboutLibraries 插件构建时生成 `res/raw/aboutlibraries.json`）。关于页的声明和隐私文案要与 README 保持一致。界面语言固定中文（`MainActivity.attachBaseContext` 只覆盖语言）。
- **调试**：调试版「我」页列表里的「开发者诊断」进入诊断页（`ui/debug/DiagnosticsPage.kt`）：「核对接口（只读）」用应用自己的接口依次读账号、时间线、一条微博和它的评论、主页、热搜、搜索、未读提醒，只记数量（不读通知列表，那会把通知标成已读）；「打开页面」按 `/n/昵称`、`/u/uid`、`/status/id`、`/results?q=` 打开页面（写操作测试时用来找测试微博；adb 输不了中文，昵称可以写成 URL 编码）；「模拟登录过期」把会话的 `SUB` 换成无效值，再加载一次时间线，看续期是否成功、用了多久，logcat 里有 `session renewal` 的每一跳（只有 host 和 path）。日志前缀 `diag:`，tag 是 `maxbo`。Flutter 版为回答 §8 各个问题写的结构探针没有迁过来，要用时从 git 历史（`53b24b0` 及以前的 `lib/features/debug/diagnostics_page.dart`）照着写。

### 后续要点（M6 之后）

- **发版**：按 §9 的发版流程。v0.2.0 是 `release.yml` 第一次真正运行，暴露了一个问题：runner 上最新的 build-tools 37 的 `apksigner` 输出格式变了（`V2 Signer: certificate SHA-256 digest:`，旧版是 `Signer #1 certificate SHA-256 digest:`），签名核对已改为两种都认。tag 推上去但还没生成 Release 时可以修好后移动 tag 重跑；已发布的 tag 不要再动。
- **会话续期的已知局限**：
  - 登录页仍然直接打开 passport 的登录表单，续期失败后（比如 30 天没打开过应用）要完整登录。
  - 写操作遇到过期时，续期后不重发。
  - 真实的第 7 天自然过期还没遇到。emulator-5554 上 Kotlin 调试版的 `SUB` 是 10-23 切换时从 WebView 读到的（Flutter 调试版留下的，很可能是 10-21 续期得到的那个），预计 10-28 到 10-30 之间失效，到时用 logcat 的 `session renewal` 核对。
- **信息流广告**：还没见到信息流里插入的真实广告，判定规则（§8 第 5 项）仍是推测；见到后用诊断页的「时间线结构」核对。
- **互动的已知局限**：转发数和时间线卡片上的评论数不会因为用户评论而变化（只有详情页本地加减）；从详情页的楼中楼预览里不能直接删回复（要进楼中楼页）；没有表情面板和 @ 补全；评论字数没有在客户端限制，超长时显示服务端的错误信息；评论只能带一张图（网页版在 `pc_comment_pics` 开启时可以带多张），图片不压缩，上传中途不能取消。
- **搜索的已知局限**：结果只按时间倒序，没有网页版的「综合」排序，也没有用户、视频等分类（这些只在右上角的网页版里有，游客只能看第一页）；没有搜索历史、联想词和筛选（接口支持原创、图片、视频和时间段筛选，见 §8）；宽窗口下是 640 单栏，不像首页那样横排卡片；新发的微博不会加进已打开的结果页，在详情页删除的自己的微博也要刷新后才从结果页消失（在结果页卡片上删除的会立即移除）。
- **发微博的已知局限**：没有表情面板、@ 补全、话题、定位、视频、投票、定时发布、编辑；不显示字数（超长时显示服务端的错误）；图片不压缩、不带水印；上传中途不能取消。
- **转发的已知局限**：没有「快转」，不能带图（网页版可以）；时间线卡片上的转发数不会因为转发而变化（只有详情页本地 +1）；勾了「同时评论」时，详情页的评论数和评论列表要刷新后才更新；刚转发出的那条里，原微博按接口返回的样子显示（视频变成链接卡片），刷新后正常；不显示字数，超长时显示服务端的错误。
- **还没做的小项**：`mymblog` 的 `bottom_tips_text` 没有显示（还没见到实例）；验证码的「去验证」还没遇到真实验证码，不知道验证完成后要不要把 WebView 的 Cookie 同步回 API。
- **未在真机上验证的部分**：双击放大、长图、GIF 和实况照片；「原图」画质下特别大的长图的内存占用（模拟器上「原图」看一张截图长图时进程约 208 MB）。
- **流畅度**：Kotlin 版只在模拟器上看过。模拟器的 GPU 是软件仿真（`ro.hardware.egl=emulation`），benchmark 包的时间线 p50 约 38 ms，连纯文字的开源许可列表也是 44 ms，测不出应用自己的问题。用户 10-23 决定发 v0.12.0 后在自己的手机上用着看，有问题再出补丁版。Baseline Profile 没有做。
- **通知页**：没见过的格式还有回复我的评论（`reply_comment`，显示为通知下方的引用）、赞的是评论（`comment`）、@我的微博；按网页版的写法处理，见到后用诊断页「通知结构」核对。精简版的微博（赞列表里 22 个字段的那种）目前只在一条已删除的微博上见过。私信没有做。
- **测试写操作**：测试目标由用户指定。2026-10-13 指定的是测试账号关注的用户大号主页上的第一条微博（2025-08-29 发布），昵称问用户，诊断页里用 `@昵称` 定位。每次真实点赞或评论前仍要先问用户；测试完把它恢复成未赞、无测试评论。2026-10-21 时那条微博已不在主页列表里，只出现在用户 10-15 为测试通知发的一条转发的转发链里。转发测试经用户同意，改用主页当前的第一条，即这条 10-15 的测试转发。10-22 起主页第一条换成了一条真实的转发，所以测试微博要按发布时间「10-15 17:24」找，不要再按「第一条」找。
- **平板/折叠屏**：WebView（登录页、应用内网页）、图片查看器、视频播放仍占满窗口。
- **应用图标**：图标是代码画的（两段圆弧组成的小写 m，底色是主题色 `#3F51B5`）。Android 的前景是矢量图 `app/src/main/res/drawable/ic_launcher_foreground.xml`；`mipmap-*` 的 PNG 是同一图形的渲染结果，改图形时要一起重新生成。

### 开发环境注意

- **emulator-5554（AVD `Pixel_9`）上的应用**：
  - `io.github.leonfox28.maxbo.debug`：Kotlin 调试版，保存着测试账号的真实登录态（10-23 起；之前是 Flutter 调试版，覆盖安装后靠 WebView 留下的 Cookie 直接登上）。真实会话测试都用它；
  - `io.github.leonfox28.maxbo`：Flutter 的 release 版（0.11.0），没有登录。可以用它试 Releases 上的新版能否覆盖安装。
- 真实登录要过短信验证码和网易易盾人机验证，代价很高。保存着登录态的设备**只能用 `adb install -r` 覆盖安装**；不要在上面运行 `connectedDebugAndroidTest`（会清空 WebView Cookie，跑完还会卸载应用），也不要卸载或清除数据。
- 设备测试（`app/src/androidTest/`，读 WebView 的 HttpOnly Cookie）在 AVD `maxbo_test` 上跑（没有真实登录，10-22 建的）：`ANDROID_SERIAL=<它的序列号> ./gradlew connectedDebugAndroidTest`。本机的其他 AVD（`Zterm_*`）属于别的项目，不要用。
- **测流畅度**：调试版慢得多，不能用来看流畅度。`./gradlew assembleBenchmark` 出的包开了 R8、用调试密钥签名、包名也是 `.debug`，可以 `install -r` 覆盖到保存着登录态的调试版上，登录态不受影响；测完再 `install -r` 装回调试版。它不可调试，`run-as` 用不了。抓帧耗时用 `adb shell dumpsys gfxinfo <包名> framestats` 或 Perfetto。模拟器的 GPU 是软件仿真，帧耗时的绝对值没有意义，要在真机上看。
- **核对续期**：诊断页的「模拟登录过期」。要测 WebView 里的 `SUB` 也无效的情况，得改 WebView 的 Cookie 库（`app_webview/Default/Cookies`），做法见 KOTLIN_MIGRATION.md §11「真实会话验收」的 K1 一项；改之前先问用户、先备份，不打印 Cookie 值。
- **宽窗口**：`adb -s emulator-5554 shell wm density 200` 得到约 864dp 宽的窗口（expanded 档，和真实折叠屏展开的宽度接近），`wm size` 也可以；看完用 `wm density reset` / `wm size reset` 恢复。这不碰应用数据，登录态不受影响。
- 本机可能同时连着别的模拟器（2026-10-13 先后见过 `emulator-5586`、`emulator-5558`，都不属于本项目），adb 命令要用 `-s emulator-5554` 指定保存着登录态的 Pixel_9；不确定时用 `adb -s <设备> emu avd name` 确认是 `Pixel_9`。zsh 不会按空格拆开 `$A` 这样的变量，要省事就写成 shell 函数。
- **模拟器的毛病**：adb 偶尔会短暂掉线（`install` 无报错失败、`device offline`），`adb reconnect offline` 或稍等再试即可。主机内存满时 `adb shell` 和截图会挂起，这时直接告诉用户，不要反复重试；冷启动（`emulator -avd Pixel_9 -no-snapshot-load`）能恢复，登录态还在。`adb exec-out screencap` 挂起时，可以用模拟器控制台的 `screenrecord screenshot <目录>` 截图（先 `auth`，口令在 `~/.emulator_console_auth_token`）。Gradle 和 Kotlin 编译的 daemon 构建完会常驻、合计约 5 GB，内存紧时用 `./gradlew --stop` 停掉。
- `adb install -r` 无声失败时，接在后面的点击会落在旧应用的界面上。点击要用 `&&` 接在安装后面，点写操作按钮附近之前先截图核对。
- 部分 Google 域名（如 `dl.google.com`）可能被本机代理规则判为直连而连不上，表现为 TLS 握手中断；这时调整代理分流规则即可，**不要往仓库里写镜像地址**。
- 操作模拟器：Compose 的文字能在 `adb exec-out uiautomator dump /dev/tty` 里看到（带坐标），点文字按钮优先用它找坐标。截图是 1080×2424，按缩小后的图量出的坐标要乘回原始比例；点小目标（如正文里的 @）前先用 `sips -c <高> <宽> --cropOffset <y> <x>` 裁出原图对应区域核对。
- 核对深色主题可用 `adb -s emulator-5554 shell cmd uimode night yes`，看完记得改回 `no`。
- `adb shell input text` 只能输入 ASCII（空格写成 `%s`），测试评论用英文。
- 调试版崩溃时，异常在 `adb logcat -s AndroidRuntime` 里；应用自己的日志 tag 是 `maxbo`（`devLog()`）。
- 测写操作时注意点击位置：卡片里的转发框打开的是**原微博**（别人的），不是测试微博。
