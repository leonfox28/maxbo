# maxbo

开源、非商业的第三方微博阅读客户端（Flutter，Android + iOS）。方案、合规边界、接口实测结论和当前进度都在 [docs/PLAN.md](docs/PLAN.md)；**开工前先读第 2 节（合规边界）、第 8 节（实测结论）和第 11 节（进度与交接）**。

## 常用命令

```sh
dart format lib test integration_test
flutter analyze                      # 必须 No issues found
flutter test                         # 单元测试 + Widget 测试
flutter test integration_test -d <iOS 模拟器 id>   # 平台通道测试，见下方注意事项
flutter build apk --debug && adb -s <设备> install -r build/app/outputs/flutter-apk/app-debug.apk
flutter build ios --simulator --debug
flutter build apk --release --split-per-abi   # 发版用，签名见 PLAN §9
```

Flutter stable 3.47.x（Dart 3.13），通过 Homebrew cask 安装。

## 硬性约束

- **合规边界优先于功能**（PLAN 第 2 节）：
  - 不做去广告、批量下载、会员内容；
  - 不绕过验证码，不伪装官方 App；
  - 不设服务器、不做埋点；
  - 请求保持克制，只在用户操作时发起。
- **参考项目不能复制代码**：Android_16_Weibo、weiboX 没有 License，只能参考它们的接口形态。
- **测试数据**：只用手写的合成数据。真实响应放在 `captured/`（已 gitignore），不得提交。
- **日志**：日志和诊断输出里不得出现 Cookie 值或微博正文。调试日志用 `devLog()`，它只在调试版输出。
- **保护真实登录态**：真实登录需要短信验证码和人机验证。保存着登录态的设备只能用 `install -r` 更新（调试版包名是 `io.github.leonfox28.maxbo.debug`），不要在上面跑 `integration_test`（会清空 WebView Cookie），也不要卸载或清除数据。
- **先问用户**：完成人机验证、勾选用户协议、以用户身份对外操作（关注、点赞、评论），都必须先得到用户确认。写操作只在用户指定的测试微博上测，测完恢复原状。

## 架构速览

- `lib/core/net/`：网络层
  - `createWeiboDio()`：浏览器 UA、Cookie 和 XSRF 拦截器、限流
  - `WeiboCookieJar`：按站点（weibo.com / weibo.cn）保存 Cookie
  - `weibo_exception.dart`：集中处理微博的各种失败响应
- `lib/core/session/`：会话
  - 登录 Cookie 经原生平台通道读取（`SUB` 是 HttpOnly，Android 端在 `CookieBridge.kt`，iOS 端在 `ios/Runner/AppDelegate.swift`）
  - `SessionController`（`sessionProvider`）
  - 会话存在 Keystore / Keychain
- `lib/core/media/`：`WeiboImage`，所有微博图片都经它加载（自动带 Referer，缓存 key 去掉签名参数）；`video_source.dart` 负责选视频地址，地址过期时重新请求 `statuses/show`；`image_disk_cache.dart` 按设置的上限清理图片磁盘缓存
- `lib/core/settings/`：`settingsProvider`（主题、字体大小、图片质量、缓存上限），存在 `shared_preferences`，`main()` 在第一帧前读入。图片尺寸要经过 `ImageQuality` 选择；字体大小由 `MaxboApp` 统一套在 `MediaQuery.textScaler` 上（在系统字体大小的基础上乘倍数），页面里不用单独处理
- `lib/core/paging/`：`PagedController<T>`，分页列表（评论、转发等）都继承它，界面用 `widgets/load_state.dart` 里的 `PagedSliverList` 和 `LoadMoreTrigger`
- `lib/data/api/weibo_api.dart`：所有端点都集中在这里。新增端点时写成 `WeiboApi` 的方法，内部调用 `getJson()` / `getPage()`。当前用户从 weibo.com 首页内嵌的 `window.$CONFIG` 解析得到（`/ajax/config` 不存在）。
- `lib/data/models/`：`Status`、`Comment` 等模型，`tryFromJson` 容错解析，字段读取用 `core/util/json_read.dart`
- `lib/widgets/`：微博卡片 `StatusCard`、正文 `StatusText`（`parseStatusText()` 把 HTML 拆成片段）、九宫格 `PicGrid`、头像 `UserAvatar`、底部悬浮胶囊 `FloatingPill`（导航栏和评论栏共用）、`BarsOnScroll`（往下滚隐藏、往回滚出现的判断，首页导航栏和详情页顶栏/底栏共用）、`TabRefreshIndicator`（标签页列表的下拉刷新；再点一次当前标签，不在顶部就回到顶部，已在顶部就刷新，列表要设 `primary: true`）、`adaptive_layout.dart`（平板和折叠屏：`WindowSize` 按窗口宽度分档，expanded 档首页的导航胶囊放在右下角；正文限宽 `readingWidth` 居中，`ListView` 用 `ReadingList`、`CustomScrollView` 用 `ReadingSlivers`，新页面的列表也要套上；首页时间线用 `ReadingList(feed: true)`，宽窗口下卡片加宽、媒体放到正文右侧；详情页在 expanded 档用 `ReadingPanes` 左右并排）
- `lib/features/`：各个页面。登录页是 WebView 加载微博官方登录页，每次主页面加载完成或失败都校验一次会话。详情页在 `status/`，评论和楼中楼在 `comments/`，图片查看器和视频播放在 `media_viewer/`，用户主页在 `profile/`（`/u/:uid`，@ 用 `/n/:name`），搜索页签在 `search/`（`/search`，顶部搜索栏，下面是热搜榜；搜索、热搜条目和正文里的 #话题# 都用 `openSearchResults()` 打开 `/results?q=`，即 `SearchResultsPage`：`statuses/search` 的结果列表，话题带 `Topic` 话题头），通知在 `notifications/`（`unreadNoticesProvider` 决定导航栏的通知图标是否变红，只在登录、打开或回到应用、下拉刷新首页时检查），设置页、字体大小页（`/text-size`，滑动条加示例微博）和关于页在 `settings/`（关于页的声明文案与 README 保持一致）。卡片和评论上的点击统一由 `features/status/status_actions.dart` 处理。
- `lib/features/compose/`：发微博（`/compose`，入口是首页右下角的「写」按钮）。`composeDraftProvider` 保存草稿（关页面不丢，换账号清空），`send()` 在发送时才逐张上传图片（`uploadPicture()`，picupload 跨域请求，用 `kPlainCrossOrigin` 去掉 XHR/XSRF 头），再调 `postStatus()`；选图经 `picturePickerProvider`，测试里覆盖它。
- `lib/features/interactions/`：点赞和评论。`likesProvider` 让同一条微博或评论的点赞状态在所有页面一致（改动只套用在改动时的那份数据上，新加载的数据优先）；`showCommentComposer()` 是写评论/回复的弹层。写接口在 `WeiboApi` 里都走 `postForm()`。删除自己的微博用 `deleteOwnStatus()`（卡片上的 ⋯ 只对当前账号的微博显示，经 `StatusCardActions.onDelete`），删除确认用 `confirmDelete()`。
- `lib/features/webview/`：应用内 WebView（`openWebPage()`），与登录页共用 Cookie 和 UA。搜索结果页右上角的「网页版」走 `m.weibo.cn`（`weiboSearchUrl()`，游客身份，只有第一页），因为 `s.weibo.com` 只有 PC 版布局。验证码也在这里由用户本人完成（`ErrorRetry` 上的「去验证」）。
- **不自动重试**：`ProviderScope(retry: noRetry)` 关掉了 Riverpod 默认的失败重试，保证请求都由用户操作触发；测试里的 scope/container 也要传。
- 诊断页：只在调试版提供，入口是「我」页列表里的「开发者诊断」，用来对真实会话核对接口；「互动结构」探针会真实点赞和评论，跑之前要问用户。

## 约定

- **语言**：代码注释用英文，界面文案用中文。
- **数据**：ID 一律用字符串；JSON 手写容错解析，不用代码生成。
- **依赖注入**：Riverpod 3（不用代码生成）。测试通过覆盖 `weiboDioFactoryProvider`、`sessionStorageProvider`、`cookieBridgeProvider` 注入假实现，假实现见 `test/support/fakes.dart`（`signedInOverrides()` 一次给齐）。测跳转用 `test/support/routes.dart` 的 `stubRouter()`。
- **版本号**：`pubspec.yaml` 的 `version` 和 `lib/core/app_info.dart` 的 `appVersion` 一起改（有测试核对）。
- **提交**：用英文写提交信息，先一句话概括，再空一行写原因。每完成一个里程碑或一个独立修复就提交一次。
