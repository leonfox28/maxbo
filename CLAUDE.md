# maxbo

开源、非商业的第三方微博阅读客户端（Kotlin + Jetpack Compose，只做 Android）。方案、合规边界、接口实测结论和当前进度都在 [docs/PLAN.md](docs/PLAN.md)；**开工前先读第 2 节（合规边界）、第 8 节（实测结论）和第 11 节（进度与交接）**。

v0.11.0 及以前是 Flutter 版，2026-10-23 起换成 Kotlin 重写的版本（v0.12.0）。迁移的经过和各处设计的理由记在 [docs/KOTLIN_MIGRATION.md](docs/KOTLIN_MIGRATION.md) 的 §11；Flutter 代码只在 git 历史里（`53b24b0` 及以前）。

## 常用命令

```sh
./gradlew spotlessApply                                        # 格式化（ktfmt，kotlinlang 风格）
./gradlew spotlessCheck lint testDebugUnitTest assembleDebug   # 与 CI 相同，必须全部通过；提交前跑全量
adb -s <设备> install -r app/build/outputs/apk/debug/app-debug.apk
ANDROID_SERIAL=<没有真实登录的模拟器> ./gradlew connectedDebugAndroidTest   # 设备测试，见下方注意事项
./gradlew assembleBenchmark   # 测流畅度：release 优化、调试密钥签名，可以覆盖安装到调试版上
./gradlew assembleRelease     # 发版用，签名见 PLAN §9
./gradlew --stop              # 构建完停掉 Gradle/Kotlin daemon（合计约 5 GB）
```

工具链：AGP 9.4.1、Gradle 9.8.2（wrapper）、Kotlin 2.4.21、JDK 17（Temurin）；compileSdk 37、targetSdk 36、minSdk 24。版本统一写在 `gradle/libs.versions.toml`。Robolectric 跑 SDK 34（35 以上要 Java 21）。

## 硬性约束

- **合规边界优先于功能**（PLAN 第 2 节）：
  - 不做去广告、批量下载、会员内容；
  - 不绕过验证码，不伪装官方 App；
  - 不设服务器、不做埋点；
  - 请求保持克制，只在用户操作时发起。
- **参考项目不能复制代码**：Android_16_Weibo、weiboX 没有 License，只能参考它们的接口形态。
- **测试数据**：只用手写的合成数据。真实响应放在 `captured/`（已 gitignore），不得提交。
- **日志**：日志和诊断输出里不得出现 Cookie 值或微博正文。调试日志用 `devLog()`，它只在调试版输出。
- **保护真实登录态**：真实登录需要短信验证码和人机验证。保存着登录态的设备只能用 `install -r` 更新（调试版包名是 `io.github.leonfox28.maxbo.debug`），不要卸载或清除数据，也不要在上面跑 `connectedDebugAndroidTest`（它会清空 WebView Cookie，跑完还会卸载应用）。设备测试用没有真实登录的 AVD `maxbo_test`。
- **先问用户**：完成人机验证、勾选用户协议、以用户身份对外操作（关注、点赞、评论），都必须先得到用户确认。写操作只在用户指定的测试微博上测，测完恢复原状。

## 架构速览

源码在 `app/src/main/java/io/github/leonfox28/maxbo/`，单个 `:app` 模块，按包分层。

- 根包：`MaxboApplication` 在第一帧之前用 `AppGraph.create()` 读入设置和会话；`MainActivity`（edge-to-edge，界面语言固定中文）；`MaxboApp` 是根部的 Composable（主题、字体大小、`Navigator`、`Overlays`、提示条、`UnreadChecks`）。
- `AppGraph`：手写的依赖容器（不用 Hilt/Koin），持有设置、会话、`WeiboApi`、时间线、未读、点赞、草稿、`OpenLists`、图片加载器等全应用共用的对象，经 `LocalAppGraph` 取用。页面状态用 ViewModel + `StateFlow`。
- `core/net/`：网络层
  - `WeiboHttp`：OkHttp，浏览器 UA、Cookie（`WeiboCookieJar`，按站点 weibo.com / weibo.cn 保存）、Referer/XSRF 等请求头、`RequestThrottle` 限流。读请求和写请求各用一个 client，写请求关掉 `retryOnConnectionFailure`；picupload 跨域上传用 `PlainCrossOrigin` 去掉 XHR/XSRF 头。
  - `WeiboException.kt`：集中处理微博的各种失败响应（登录过期、验证码、频率限制、网络、接口错误），界面上用 `loadErrorMessage()` 取文案。
- `core/session/`：会话
  - `WebCookies` 直接读 `CookieManager`（包括 HttpOnly 的 `SUB`）；`SessionController` 的状态是 `SignedOut(expired)` / `SignedIn(account)`。主动退出时连同 WebView 的 Cookie 一起清掉；登录过期时保留它们。
  - `SUB` 在服务端约 7 天失效。接口报过期时，`SessionRenewer` 在不挂到界面上的 WebView 里打开 weibo.com，让微博用 30 天的 `ALC` 换新 `SUB`（PLAN §8「会话续期」）。读请求续期后重发一次，写请求不重发。续期失败才退出登录。
  - 会话用 Android Keystore 里的 AES-GCM 密钥加密，存在 `noBackupFilesDir/session`。
- `core/media/`：`WeiboImage.kt` 是 Coil 的图片加载器，所有微博图片都经它加载（带 Referer、http 改成 https，缓存 key 去掉签名参数）；`ImageDiskCache` 的上限跟着设置走；`VideoSource.kt` 负责选视频地址，地址过期时重新请求 `statuses/show`。
- `core/settings/`：`SettingsController`（主题、字体大小、图片质量、缓存上限），存在 DataStore，第一帧前读入。图片尺寸要经过 `ImageQuality` 选择。字体大小在根部经 `LocalDensity` 统一套上（系统字体大小乘倍数），页面里不用单独处理；但对话框、底部弹层、菜单、提示是单独的窗口，**新加的要用 `AppAlertDialog` 或套上 `KeepTextSize`**。
- `core/paging/`：`PagedController<T>`，分页列表（时间线、评论、转发、通知等）都继承它，子类写 `fetchPage()` 和 `idOf()`；用户自己的改动用 `prepend()` / `remove()` / `replace()`。界面用 `ui/widget/PagedItems.kt` 的 `pagedItems()` 和 `LoadState.kt` 里的 `LoadMoreTrigger`、`ErrorRetry`。
- `core/util/`：`devLog()`、容错读取 JSON 的 `JsonRead.kt`、时间和计数的格式化 `Format.kt`。
- `data/api/WeiboApi.kt`：所有端点都集中在这里。新增端点时写成 `WeiboApi` 的方法，读用 `getJson()` / `getPage()`，写用 `postForm()`。当前用户从 weibo.com 首页内嵌的 `window.$CONFIG` 解析得到（`PageConfig.kt`，`/ajax/config` 不存在）。
- `data/model/`：`Status`、`Comment` 等模型，`tryFromJson` 容错解析 kotlinx-serialization 的 `JsonElement` 树，不用 `@Serializable`。
- `ui/navigation/`：Navigation 3。页面栈是我们自己持有的列表（`Navigator`，经 `LocalNavigator` 取用，`push()` / `pop()`），页面是 `Route`（可以带上一页已有的数据），转场和预测性返回在 `Transitions.kt`。
- `ui/shell/HomeShell.kt`：栈底的 `Route.Home`，四个页签（首页、搜索、通知、我）和底部悬浮胶囊 `FloatingPill`；往下滚隐藏、往回滚出现（`BarsOnScroll`）；再点一次当前页签经 `LocalTabReselect` 通知页面：不在顶部就回到顶部，已在顶部就刷新。
- `ui/widget/`：微博卡片 `status/StatusCard.kt`（宽窗口时媒体放到正文右侧，自定义 `Layout`）、正文 `text/StatusText.kt`（`parseStatusText()` 用 jsoup 把 HTML 拆成片段）、九宫格 `PicGrid`、头像 `UserAvatar`、`HidingAppBar` / `HidingAppBarBody`（随滚动隐藏的顶栏）、`AdaptiveLayout.kt`（`windowSize()` 按 600/840 分档；正文限宽用 `readingPadding()`，新页面的列表也要套上；详情页在 expanded 档用 `ReadingPanes` 左右并排）、`Overlays`（弹层和对话框放在整个应用上方，`show()` 挂起到关闭）、`LocalSnackbars`（全应用共用的提示条）。
- `ui/<功能>/`：各个页面。登录页 `auth/`（WebView 加载微博官方登录页，每次主页面加载完成或失败都校验一次会话）；时间线 `timeline/`；详情页 `status/`；评论和楼中楼 `comments/`；看图（Telephoto）和视频（Media3）`media/`；用户主页和「我」页 `profile/`；搜索和搜索结果 `search/`；通知 `notifications/`（`UnreadNoticesController` 只在登录、打开或回到应用、下拉刷新首页时检查，由根部的 `UnreadChecks` 触发）；应用内 WebView `webview/`；设置、字体大小、关于、开源许可 `settings/`（关于页的声明文案与 README 保持一致）；诊断页 `debug/`（只在调试版，「我」页的「开发者诊断」进入）。卡片和评论上的点击统一由 `status/StatusActions.kt` 的 `rememberStatusCardActions()` 处理。
- `ui/interactions/`：点赞 `LikesController`（同一条微博或评论的点赞状态在所有页面一致；改动只套用在改动时的那份数据上，新加载的数据优先）；`showCommentComposer()` 是写评论/回复的弹层，可以带一张图，点「发送」时才上传；`rememberStatusWrites()` 提供转发和删除自己的微博；`OpenLists` 让发微博、转发、删除改动已打开的列表，不为此发请求。
- `ui/compose/`：发微博（`Route.Compose`，入口是首页右下角的按钮）。`AppGraph.composeDraft` 保存草稿（关页面不丢，换账号清空），发送时才逐张上传图片，再发微博。选图用系统照片选择器（`PicturePicker`）。
- **不自动重试**：OkHttp 里没有重试拦截器，失败都等用户操作。要跟应用同寿命的副作用（例如未读检查）放在根部，不要放在导航条目里。

## 约定

- **语言**：代码注释用英文，界面文案用中文（只有中文，不做多语言）。
- **数据**：ID 一律用字符串；JSON 手写容错解析，不用代码生成。
- **测试**（`app/src/test/`，JUnit 4 + Robolectric + Compose UI Test）：
  - `support/TestGraph.kt` 的 `testGraph()` 用假实现（`support/Fakes.kt`）拼出一个 `AppGraph`，`restored = signedIn` 就是已登录（uid 7）。不传 `web` 时请求一律按离线失败，不会碰到真实的微博；
  - `support/FakeWeibo.kt` 是本地的 HTTPS MockWebServer，DNS 全部指向它，用到它的测试类要跑在 Robolectric 下；
  - 页面测试用 `TestHost`，整个应用的流程用 `AppHarness`，合成数据的构造函数在 `support/Statuses.kt`。
- **版本号**：只在 `app/build.gradle.kts` 里，`versionName` 和 `versionCode` 一起改，`versionCode` 每发一版加 1。
- **提交**：用英文写提交信息，先一句话概括，再空一行写原因。每完成一个里程碑或一个独立修复就提交一次。
