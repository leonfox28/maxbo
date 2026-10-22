# maxbo 迁移到 Kotlin + Jetpack Compose 的计划

写于 2026-10-22，基于 v0.11.0 和去掉 iOS 之后的代码（`2ad832e`）。用户决定把 maxbo 从 Flutter 整体迁移到 Kotlin + Jetpack Compose，只做 Android（PLAN §10）。

## 0. 新上下文从这里开始

1. 先读 `CLAUDE.md`，里面的硬性约束在迁移期间全部有效。再读 PLAN §2（合规边界）、§8（接口实测结论）、§11（各功能的行为细节、后续要点、开发环境注意），最后读本文。
2. 看 §11 的进度表，从第一个没完成的阶段接着做。
3. **Flutter 代码是行为规范**：`lib/` 是每个功能的实现，`test/` 是它的边界情况。迁移就是把它们翻译成 Kotlin，不重新设计。本文和 Dart 代码不一致时，以 Dart 代码的行为为准，并把差异记进 §11 的备注。
4. 每完成一个阶段就提交一次（英文提交信息，格式同 CLAUDE.md），然后更新 §11。阶段中间完成的独立修复也单独提交。
5. 切换（K7）之前，Flutter 版一直保持能构建、能发版。迁移期间如果要修 Flutter 版的 bug，照旧在 `lib/` 里修，Kotlin 版同步改。

## 1. 目标与范围

- **目标**：用 Kotlin + Jetpack Compose 重写 maxbo。功能、界面、交互都和 v0.11.0 对齐（README「功能」一节和 PLAN §11 的各行），然后删除 Flutter 代码，之后只维护 Kotlin 版。
- **不加新功能**：迁移期间不加功能，也不改交互，除非用户要求。发现 Flutter 版的 bug，可以在 Kotlin 版顺手修，但要记进 §11 的备注。
- **不做新旧版本的数据交接**：Kotlin 版装上后就是全新状态，用户重新登录、重新设置都可以接受（用户 2026-10-22 确认）。不迁移 Flutter 版存的登录态、设置和缓存，也不清理它留下的文件。注意同一个包名下 WebView 的 Cookie 会保留，所以升级后打开登录页可能直接就登录上了。这是正常现象，不用特别处理。
- **约束照旧**：
  - PLAN §2 的合规边界；
  - 参考项目（Android_16_Weibo、weiboX）的代码不能复制。`lib/` 是本项目自己的代码（GPL-3.0），可以照着翻译；
  - 测试只用手写的合成数据；
  - 日志里不出现 Cookie 值和微博正文；
  - 不自动重试，请求只在用户操作时发起，并且限流；
  - 界面文案用中文，代码注释用英文；
  - ID 一律用字符串；
  - JSON 手写容错解析，模型不用代码生成。
- **去掉 Flutter 特有的绕路**：
  - `lib/core/page_transitions.dart`：Compose 和导航库自带预测性返回；
  - `router.dart` 里的 `_page()`：是为了绕开 go_router 的问题；
  - `CookieBridge` 平台通道：直接调 `CookieManager` 就行；
  - 用 `MediaQuery.textScaler` 调字体大小的写法：改为在根部调整 `LocalDensity` 的 `fontScale`。

## 2. 关键决策

### 2.1 在哪里写

- 在本仓库的 main 分支上新建 `kotlin/` 目录，里面是独立的 Gradle 工程。`lib/`、`test/`、`integration_test/`、`android/`（Flutter 的宿主工程）原样保留到 K7。
- 这样做的好处：
  - Flutter 版随时能修 bug、发版；
  - Dart 源码和测试就在旁边，随时可以对照；
  - 不用长期分支，也就没有合并的麻烦。
- K7 切换时删掉 Flutter 的文件，把 `kotlin/` 的内容移到仓库根目录（§10）。

### 2.2 包名、签名、版本号

| 安装包 | applicationId | 显示名称 | 签名 |
|---|---|---|---|
| Flutter release（现有用户） | `io.github.leonfox28.maxbo` | maxbo | 发布密钥 |
| Flutter debug（emulator-5554 上有真实登录） | `io.github.leonfox28.maxbo.debug` | maxbo debug | 本机调试密钥 |
| **Kotlin debug，K0–K6** | `io.github.leonfox28.maxbo.dev` | maxbo dev | 本机调试密钥 |
| Kotlin debug，K7 起 | `io.github.leonfox28.maxbo.debug` | maxbo debug | 本机调试密钥 |
| Kotlin release | `io.github.leonfox28.maxbo` | maxbo | 发布密钥 |

- **开发期用 `.dev`**：这样 Flutter 调试版和它的登录态都不受影响，可以随时打开对照行为。代价是 `.dev` 要用户本人登录一次（短信加人机验证，到 K1 结束时问用户）。`.dev` 有了登录态以后，和 CLAUDE.md 说的一样受保护：只能 `install -r`，不卸载、不清数据，不在上面跑设备测试。
- **namespace 和源码包名**：都用 `io.github.leonfox28.maxbo`。
- **签名**：
  - 调试版用本机的 `~/.android/debug.keystore`，和 Flutter 调试版是同一个证书，SHA-256 为 `4050c7a543d65aa8dfa96afbd74396d84ed83da932db104db5fef2146afe3eba`。
  - release 用 PLAN §9 的发布密钥（`android/key.properties`，K7 时随工程一起移动）。
  - 包名和签名都不变，用户直接覆盖安装就能升级。
- **versionCode**：Flutter 版按 ABI 拆分，versionCode = ABI 序号 × 1000 + build 号。v0.11.0 的三个包分别是 armeabi-v7a 1021、arm64-v8a 2021、x86_64 4021。Kotlin 版必须比它们都大才能覆盖安装，所以从 **5022** 开始，每发一版加 1。
- **versionName**：接着往上排。第一个 Kotlin 版的版本号发版时问用户，建议 0.12.0。
- **APK 数量**：Kotlin 版大概率没有原生库（`.so`），这样只出一个通用 APK 就行。K7 时用 `unzip -l` 检查 APK 里有没有 `lib/` 目录。
- **SDK 版本**：minSdk 24，和 Flutter 版一样，不丢用户；compileSdk 和 targetSdk 都是 36，与 Flutter 版相同。本机的 SDK 平台有 35、36、37.0。
- **Manifest 要照搬的**：
  - `INTERNET` 权限；
  - `android:allowBackup="false"`；
  - `android:enableOnBackInvokedCallback="true"`；
  - `windowSoftInputMode="adjustResize"`；
  - 应用图标：`res/mipmap-*`、`mipmap-anydpi-v26`、`drawable/ic_launcher_foreground.xml`；
  - 启动页主题：`values/`、`values-night/`。

  Flutter 专用的 meta-data 和 `PROCESS_TEXT` 的 queries 不要了。

### 2.3 工程结构

Dart 代码约 1.5 万行，一个 `:app` 模块就够了，用包来分层（§4），不拆成多个模块。

## 3. 技术选型

版本号在 K0 时去 Google Maven 和 Maven Central 查最新稳定版，统一写进 `gradle/libs.versions.toml`。本机现有的 AGP 9.1.0、Kotlin 2.4.0、Gradle 9.3.1（Flutter 宿主工程在用）和 JDK 17（Temurin）都确认能用，优先沿用。依赖下载失败时，先看 PLAN「开发环境注意」里关于代理分流的说明。

| 用途 | Flutter 版 | Kotlin 版 | 说明 |
|---|---|---|---|
| 界面 | Flutter Material | Jetpack Compose + Material 3（用 Compose BOM 管理版本） | |
| 配色 | `ColorScheme.fromSeed(#3F51B5)` | 把 Flutter 版生成的配色抄成固定值 | K0 时写一个临时 Dart 测试，打印 `buildTheme(Brightness.light/dark).colorScheme` 的每个颜色，抄进 `Color.kt`，这样两版颜色完全一致，不用引入取色库。未读红色 `#F5222D` 照搬 |
| 窗口分档 | `adaptive_layout.dart` | `material3-adaptive` 的 `currentWindowAdaptiveInfo()` | 分界同样是 600 和 840。限宽、宽卡片这些行为照搬 Dart 版 |
| 导航 | go_router + StatefulShellRoute | 首选 Navigation 3（`androidx.navigation3`）；不合适就用 Navigation Compose | K0 时定下来并记录理由。结构是：四个页签（各自只有根页面），上面是一个页面栈。预测性返回用框架自带的 |
| 状态和依赖注入 | Riverpod 3（不用代码生成） | ViewModel + StateFlow；依赖容器手写（`AppGraph`），不用 Hilt / Koin | 全局状态（会话、点赞、未读、设置、草稿）放在容器里的单例，测试时换成假实现，相当于现在的 provider override |
| 网络 | Dio + 拦截器 | OkHttp 5 + 拦截器 | 不用 Retrofit：端点不多、形态各异，照 `WeiboApi` 手写方法 |
| JSON | 手写容错解析（`json_read.dart`） | kotlinx-serialization-json 的 `JsonElement` 树，加上手写的读取函数 | 模型不加 `@Serializable`。不用 `org.json`，它在 JVM 单元测试里是空实现 |
| HTML | `html` 包 | jsoup | 用于把正文拆成片段、从首页提取 `$CONFIG` |
| 图片 | extended_image | Coil 3（compose、network-okhttp、gif） | Referer 由 OkHttp 拦截器加；缓存 key 去掉签名参数（用 memoryCacheKey / diskCacheKey）；磁盘缓存上限跟着设置走 |
| 看大图 | extended_image 的手势 | Telephoto（`zoomable-image-coil3`） | 支持缩放，长图分块加载 |
| 视频 | video_player | AndroidX Media3 ExoPlayer | |
| WebView | webview_flutter | `android.webkit.WebView`（放在 `AndroidView` 里）+ `androidx.webkit` | Cookie 直接用 `CookieManager` 读，包括 HttpOnly 的。不显示的续期 WebView 不挂到界面上，用 Application 的 Context 创建 |
| 登录态存储 | flutter_secure_storage | 用 Android Keystore 里的 AES-GCM 密钥加密，存成文件或 DataStore | 不用已经废弃的 `security-crypto` |
| 设置 | shared_preferences | DataStore Preferences | 在第一帧之前读入，避免启动时主题闪一下 |
| 选图 | image_picker（系统照片选择器） | `ActivityResultContracts.PickVisualMedia` / `PickMultipleVisualMedia` | |
| 外部链接 | url_launcher | Intent | |
| 开源许可页 | Flutter 的 `showLicensePage` | AboutLibraries | |
| 界面语言 | `locale: zh_CN` | 系统组件（返回按钮说明、日期等）也要固定显示中文 | 用 `AppCompatDelegate.setApplicationLocales` 或在 Activity 里覆盖 Configuration，K0 时选一种。文案像 Dart 版一样直接写在代码里，只有中文，不做多语言 |
| 测试 | flutter_test | JUnit 4、kotlinx-coroutines-test、Turbine、OkHttp MockWebServer、Robolectric + Compose UI Test；设备测试用 AndroidX Test | §7 |
| 格式与检查 | `dart format`、`flutter analyze` | Spotless + ktlint（或 ktfmt，K0 时定），Android Lint | `./gradlew spotlessCheck lint testDebugUnitTest` 必须全部通过 |

## 4. 代码结构

`kotlin/app/src/main/java/io/github/leonfox28/maxbo/` 下的包，以及对应的 Dart 文件（括号里是负责迁移的阶段）：

| Kotlin 包 | 对应的 Dart 代码 |
|---|---|
| 根包：`MaxboApplication`（创建 `AppGraph`）、`MainActivity` | `main.dart`、`app.dart`（K0） |
| `core.net` | `core/net/`：`weibo_dio.dart`、`cookie_jar.dart`、`request_throttle.dart`、`weibo_exception.dart`（K1） |
| `core.session` | `core/session/`，加上 `android/.../CookieBridge.kt`（K1） |
| `core.settings` | `core/settings/app_settings.dart`：存储放在 K0，设置页在 K6 |
| `core.paging` | `core/paging/paged_controller.dart`（K2） |
| `core.media` | `weibo_image.dart`（K2）、`video_source.dart`（K3）、`image_disk_cache.dart`（K6） |
| `core.util` | `dev_log.dart`（K0）、`json_read.dart` 和 `json_shape.dart`（K1）、`format.dart`（K2） |
| `data.api` | `weibo_api.dart`、`page_config.dart`。`WeiboApi` 的方法随功能分到各阶段迁移：K1 当前用户；K2 时间线；K3 详情、评论、楼中楼、转发；K4 主页、热搜、搜索、通知；K5 所有写操作 |
| `data.model` | `data/models/`：`account`（K1）；`status`、`pic_info`、`weibo_user`、`feed_page`、`paged`（K2）；`comment`（K3）；`user_profile`、`hot_search`、`topic`、`notice`（K4） |
| `ui.theme`、`ui.navigation` | `core/theme.dart`、`core/router.dart`（K0，之后每个阶段往里加页面） |
| `ui.widget` | `widgets/`：`floating_pill`（K0）；`status_card`、`rich_text`、`pic_grid`、`user_avatar`、`tag_label`、`load_state`、`bars_on_scroll`、`tab_reselect`（K2）；`adaptive_layout` 的限宽部分在 K2，宽卡片和左右并排在 K6 |
| `ui.shell` | `features/shell/home_shell.dart`：骨架在 K0，滚动收起和再点页签在 K2，右下角胶囊在 K6 |
| `ui.auth` | `features/auth/`（K1） |
| `ui.timeline` | `features/timeline/`（K2） |
| `ui.status` | `features/status/`：`status_actions` 的跳转部分在 K2，互动部分在 K5；详情页在 K3 |
| `ui.comments` | `features/comments/`（K3） |
| `ui.media` | `features/media_viewer/`（K3） |
| `ui.profile` | `features/profile/`（K4） |
| `ui.search` | `features/search/`（K4） |
| `ui.notifications` | `features/notifications/`（K4） |
| `ui.webview` | `features/webview/`（K4） |
| `ui.interactions` | `features/interactions/`（K5） |
| `ui.compose` | `features/compose/`（K5） |
| `ui.settings` | `features/settings/`（K6） |
| `ui.debug`（只在调试版） | `features/debug/diagnostics_page.dart`（K6，2545 行，只迁还用得上的探针） |

`lib/core/page_transitions.dart` 和 `lib/core/app_info.dart` 不迁移。版本号直接用 `BuildConfig.VERSION_NAME`。

## 5. 必须保留的行为

下面这些是横跨多个文件的规则，最容易在翻译时漏掉。每个功能的其他细节，以 PLAN §11 对应的行和 Dart 代码为准。

**网络与会话**
- 请求头和 Cookie：
  - 用浏览器 UA，和登录用的 WebView 一致；
  - Cookie 按站点（weibo.com、weibo.cn）分开保存；
  - 带 XSRF 头；
  - 请求经过 `RequestThrottle` 限流。
- picupload 是跨域请求，要去掉 XHR 和 XSRF 头（Dart 版的 `kPlainCrossOrigin`）。
- 视频地址返回的是 http，要改成 https。
- 各种失败响应集中在一处映射（`weibo_exception.dart`），包括登录过期、验证码、频率限制等。界面上用 `ErrorRetry` 显示，遇到验证码时带「去验证」，由用户本人在 WebView 里完成。
- **不自动重试**：唯一的例外是读请求在续期成功后重发一次，其余失败都等用户操作。不要在 OkHttp 里加重试拦截器。OkHttp 自带的 `retryOnConnectionFailure` 是连接层的重试，K1 时决定开不开，并记录下来。
- 当前用户从 weibo.com 首页内嵌的 `window.$CONFIG` 解析（`/ajax/config` 这个接口不存在）。
- 登录页每次主页面加载完成或失败，都校验一次会话。
- **退出登录与登录过期的区别**：
  - 用户主动退出时，连同 WebView 的 Cookie 一起清掉，下次登录从头开始；
  - 登录过期时保留 WebView 的 Cookie，打开登录页就能直接登回去。
- **续期**：`SUB` 在服务端约 7 天失效。接口报过期时，`SessionRenewer` 在不显示的 WebView 里打开 weibo.com，让微博用 30 天的 `ALC` 换一个新的 `SUB`，并确认还是同一个 uid。
  - 读请求续期后重发一次；
  - 写请求不重发，提示用户再试；
  - 续期失败才退出登录。
- `devLog` 只在调试版输出，里面不能有 Cookie 值和正文。

**数据**
- ID 一律用字符串。
- `tryFromJson` 容错：一条数据坏了，不影响整页的其他内容。
- 广告标识的规则（被判成 `WeiboADNatural` 的不标）、`mix_media_info`、链接卡片等，按 PLAN §8 和 `status.dart` 处理。

**界面行为**
- **点赞状态**：同一条微博或评论的点赞状态在所有页面一致。改动只套用在改动时的那份数据上，新加载的数据优先（`likes.dart`）。
- **未读通知**：只在三个时机检查：登录时、打开或回到应用时（至少间隔 1 分钟）、下拉刷新首页时。加载更多时不检查，也不在后台轮询。
- **草稿**：
  - 发微博的草稿关掉页面不丢，换账号时清空；
  - 评论的草稿（文字加图片）关掉弹层不丢。
- **图片上传**：只在点「发送」时上传；发送失败后再发，已经传上去的图不重传。
- **转发**：转发一条转发时，预填 `//@作者:原文`；发出后加到已打开列表的顶部。
- **删除**：卡片上的 ⋯ 只对当前账号自己的微博显示，删除前用 `confirmDelete()` 确认。
- 卡片和评论上的点击，统一由 `status_actions` 处理。
- **页签**：
  - 再点一次当前页签：不在顶部就回到顶部，已在顶部就刷新；
  - 往下滚时隐藏导航胶囊和顶栏，往回滚时出现（`bars_on_scroll.dart`）。
- **字体大小**：在系统字体大小的基础上乘设置的倍数，统一在根部处理。
- **图片质量与缓存**：图片尺寸要按 `ImageQuality` 选择；磁盘缓存在启动时和调低上限时清理。
- **宽窗口**：
  - 按 600 / 840 分档；
  - 正文限宽 640 并居中；
  - expanded 档首页的导航胶囊放在右下角；
  - 宽卡片里文字一侧固定 400，其余给媒体，计数贴在卡片底部；
  - expanded 档详情页的正文和评论左右并排；
  - 要用约 850dp 宽（真实折叠屏展开的宽度）核对，不能只测 841 和 1280。
- 关于页的声明文案和 README 保持一致。
- 诊断页只在调试版提供。「互动结构」探针会真的点赞和评论，跑之前要问用户。

## 6. 开发与验证的规则

- **两个应用的分工**：`.dev`（Kotlin）做开发和真实会话验证；Flutter 的 `.debug` 是对照组和退路，K7 之前不要动它。adb 命令一律带 `-s emulator-5554`。
- **真实写操作**：只在用户指定的测试微博上做，每次先问，测完恢复原状。发微博用「仅自己可见」，测完删掉。规则同 CLAUDE.md。
- **测流畅度**：不看调试版。
  - 构建：加一个 `benchmark` 构建类型（`initWith(release)`，用调试密钥签名，applicationIdSuffix 用 `.dev`，manifest 里声明 `<profileable android:shell="true"/>`），用 `install -r` 覆盖到 `.dev` 上测，登录态不受影响，测完装回调试版。
  - 抓帧耗时：用 `adb shell dumpsys gfxinfo <包名> framestats` 或 Perfetto。PLAN「开发环境注意」里抓 Dart VM timeline 的办法只适用于 Flutter 版。
  - 要测的场景可以写成 `adb shell input` 脚本，多跑几次。
- **操作模拟器**：截图、坐标、深色模式、`input text` 的限制等注意事项都在 PLAN「开发环境注意」。Compose 的界面可以用 `uiautomator dump` 看到元素，比 Flutter 版方便。
- **模拟器资源**：模拟器内存紧张（2.5 GB，交换区用得很多）。要再开一台测试用模拟器之前，先确认主机还有余量。

## 7. 测试

- **翻译 Flutter 测试**：`test/` 下共 316 个测试，每个 Dart 测试文件都要有对应的 Kotlin 测试，或者在 §11 的备注里写明不迁移的理由（比如 Flutter 特有的转场测试）。各阶段要迁移哪些测试，见 §9。
- **测试辅助**：`test/support/` 里的合成数据构造函数（`statuses.dart` 里的 `statusJson()`、`feedJson()` 等）和假实现（`fakes.dart`）先翻译过来，其他测试都依赖它们。
- **测试分层**：
  - JVM 单元测试：模型、解析、`WeiboApi`（用 MockWebServer 模拟服务端）、Cookie jar、限流、错误映射、会话、续期（用假实现）、分页、点赞、草稿；
  - Robolectric + Compose UI Test：页面层面的测试（对应现在的 Widget 测试）；
  - 设备测试（`androidTest`）：对应 `integration_test/cookie_bridge_test.dart`，验证 `CookieManager` 能读到页面响应写入的 HttpOnly Cookie。
- **设备测试在哪台机器上跑**：必须是一台**没有真实登录**的模拟器。本机现有的 AVD 里，`Pixel_9` 有真实登录，其余的 `Zterm_*` 属于别的项目，都不能用。需要时建一台 `maxbo_test`（本机已有 android-36 和 37.0 的系统镜像），建之前先告诉用户。
- **CI**：K0 起在 `ci.yml` 里加一个 Kotlin 的 job：`./gradlew -p kotlin spotlessCheck lint testDebugUnitTest assembleDebug`。Flutter 的 job 保留到 K7。

## 8. 风险

- **不显示的 WebView**：续期用的 WebView 不挂到窗口上时，页面脚本和计时器能不能正常跑完，要在真实会话上核对（Flutter 版用的是不带界面的 `WebViewController`）。
- **正文里的表情**：表情是嵌在文字里的图片，用 `InlineTextContent` 实现。长列表里的性能要在 release 包上看。
- **图片缓存**：Coil 的缓存 key、Referer、按画质选尺寸，要和 Dart 版行为一致，否则会出现 403 或重复下载。
- **原图长图**：原图画质下特别大的长图，要核对内存占用。
- **工作量**：诊断页一个文件就有 2545 行。只迁还在用的探针，不要逐行翻译。
- **Compose 的已知坑**：滚动位置的保存、页签切换后状态保留（Flutter 版用 IndexedStack 保留各页签的状态），要逐项对照 Dart 版。

## 9. 阶段计划

每个阶段都要在 `.dev` 上用真实会话核对过，测试全部通过，才算完成。

### K0 工程骨架
- 建 `kotlin/` Gradle 工程：version catalog、Compose、Material 3、Spotless、Lint，以及 debug（`.dev`）和 release 两个构建类型。release 签名先不接。
- 主题：照 Flutter 版抄配色，浅色、深色、跟随系统都要有；未读红色。
- 基础设施：`AppGraph`、`devLog`、设置存储（DataStore，在第一帧之前读入），界面固定中文。
- `MainActivity`：edge-to-edge，状态栏图标随主题变化。
- 页面结构：四个页签的骨架（占位页面）和悬浮导航胶囊（`FloatingPill`）。在设置页的占位页上核对预测性返回：页面跟手缩小，松手返回，滑回去能取消。
- CI 加 Kotlin 的 job。
- **验收**：`.dev` 能和 Flutter 的两个应用同时装在 emulator-5554 上；页签能切换；预测性返回正常；CI 通过。
- **迁移的测试**：`app_info_test` 不迁移（换成 BuildConfig）；`app_settings_test` 的存储部分。

### K1 网络、会话、登录
- 网络层：`json_read`、Cookie jar、限流、拦截器（UA、Cookie、XSRF、跨域时去头）、错误映射、`PageConfig`（解析 `$CONFIG`）。
- `WeiboApi` 的基础方法（`getJson`、`getPage`、`postForm`）和 `fetchCurrentAccount`。
- 会话：存储（Keystore）、读 WebView Cookie、`SessionController`（未登录 / 过期 / 已登录三种状态，退出和过期的区别见 §5）、`SessionRenewer`。
- 登录页（WebView 打开微博官方登录页）和登录提示。
- 调试版里加一个「模拟登录过期」的入口（对应诊断页的 🔄），用来核对续期。
- **验收**：
  - 请用户在 `.dev` 上登录一次；
  - 重启后仍然是登录状态；
  - 退出登录后 WebView 的 Cookie 也清掉了；
  - 模拟登录过期后能续期成功；
  - 设备测试在 `maxbo_test` 上通过；
  - 照 PLAN §11 的「M1 登录验证」「会话续期」两行核对。
- **迁移的测试**：`cookie_jar_test`、`request_throttle_test`、`weibo_dio_test`、`weibo_exception_test`、`session_controller_test`、`session_renewer_test`、`cookie_bridge_test`（对应设备测试）、`page_config_test`、`account_test`、`weibo_api_test` 的相关部分。

### K2 首页时间线与微博卡片
- 模型：`Status`、`PicInfo`、`WeiboUser`、`FeedPage`、`Paged`。
- 工具和图片：`format`；`WeiboImage`（Coil，处理 Referer、缓存 key、按画质选尺寸）。
- 正文：解析成片段，支持 @、#话题#、链接和嵌入的表情；长微博显示「全文」。
- 卡片：`StatusCard`，包括转发框、链接卡片、视频封面、混合媒体、广告标识、互动计数、⋯ 菜单的占位；`PicGrid`，支持九宫格、长图、GIF 标识；`UserAvatar`、`TagLabel`。
- 列表和首页：`load_state`（分页列表、加载更多、`ErrorRetry`）、`PagedController`、时间线的 controller 和页面（下拉刷新时检查未读）。
- 页签交互：`BarsOnScroll`（滚动时收起），再点页签回到顶部或刷新。
- 限宽：`ReadingList` 的限宽居中部分。
- `status_actions` 的跳转部分。
- **验收**：时间线能加载；能下拉刷新；能连续翻 3 页以上；图片没有 403；GIF 能动；深色主题正常；用 release 包看滚动流畅度。照 PLAN §11 的「M2 首页时间线」一行核对。
- **迁移的测试**：`status_test`、`format_test`、`weibo_image_test`、`status_text_parser_test`、`status_card_test`、`load_state_test`、`tab_reselect_test`、`timeline_controller_test`、`timeline_page_test`，以及 `test/support/` 的构造函数和假实现。

### K3 详情、评论、媒体
- 详情页：长文全文；转发、评论、赞的页签；评论能按热度或时间排序，能翻页。
- 评论：楼中楼页，评论条目（显示图片评论），评论的 controller，转发列表。
- 图片查看器（Telephoto）：左右翻页，点击或下滑关闭，支持长图和 GIF。
- 视频播放（Media3）：按规则选播放地址，http 改成 https，地址过期时重新请求 `statuses/show`。
- **验收**：照 PLAN §11 的「M3 详情与媒体」一行逐项核对。
- **迁移的测试**：`status_detail_page_test`、`comment_tile_test`、`comments_controller_test`、`comment_replies_page_test`、`comment_test`、`video_source_test`。

### K4 用户主页、搜索、通知、网页
- 用户主页：按 uid 进（`/u/:uid`），按昵称进（`/n/:name`，用于 @）；头部加载时显示占位条；置顶标签；「他10-17赞过的微博」这类说明。「我」页。
- 搜索页：顶部搜索栏，下面是热搜榜（置顶、前三名配色、「商」标签）。
- 搜索结果页：话题头；右上角的「网页版」走 `m.weibo.cn`（`weiboSearchUrl()`）。
- 通知页：各个页签，未读红点规则，`push_count` 接口。
- 应用内 WebView：网页能后退时先后退网页；验证码从「去验证」进入。
- **验收**：照 PLAN §11 的「M4 主页与热搜」「通知页」「搜索页签」「原生搜索结果」几行逐项核对。
- **迁移的测试**：`profile_page_test`、`search_page_test`、`search_results_page_test`、`notifications_page_test`、`user_profile_test`、`hot_search_test`、`topic_test`、`notice_test`、`weibo_api_test` 的相关部分。

### K5 互动与发布
- 点赞：全局一致的规则见 §5。
- 评论：评论和回复的弹层，可以带一张图（第一次点图片按钮时先请求 `checkAllowCommentWithPic`，点「发送」时才上传，草稿保留）；删除自己的评论。
- 转发：选可见范围、同时评论、转发链预填、发出后插到列表顶部。
- 删除自己的微博：卡片上的 ⋯，删除前确认。
- 发微博：文字加最多 9 张图（系统照片选择器）、可见范围、草稿。发送时先逐张上传图片（picupload），再发微博。
- `status_actions` 的完整版。
- **验收**：照 PLAN §11 的「M5 轻互动」「发微博」「转发」「评论带图」几行核对。每项真实写操作都要先问用户；测完恢复测试微博；发微博用「仅自己可见」，测完删掉。
- **迁移的测试**：`likes_test`、`comment_composer_test`、`repost_test`、`compose_draft_test`、`compose_page_test`、`status_actions_test`、`weibo_api_test` 的写接口部分。

### K6 设置、关于、宽屏、诊断
- 设置页：主题、字体大小、图片质量、缓存上限、清除缓存（显示已用大小）。
- 字体大小页：滑动条加示例微博。
- 关于页：文案和 README 一致；项目主页、问题反馈、开源许可（AboutLibraries）。
- 图片缓存的清理规则。
- 宽屏：按窗口分档；宽卡片（Dart 版是自定义 RenderObject `_WideLayout`，Compose 里用自定义 `Layout` 实现）；expanded 档详情页左右并排；右下角的导航胶囊。用 `wm size` / `wm density` 核对，看完要 reset。
- 诊断页（只在调试版）：只迁还用得上的探针。
- **验收**：照 PLAN §11 的「M6 打磨与打包」「平板/折叠屏（第一步）」「平板/折叠屏（第二步）」「字体大小」几行逐项核对。
- **迁移的测试**：`settings_page_test`、`text_size_page_test`、`app_settings_test`、`image_disk_cache_test`、`adaptive_layout_test`、`app_test`（应用层面的流程，比如登录跳转、页签、字体大小、预测性返回）。

### K7 对齐、切换、发版
见 §10。

## 10. 切换（K7）

1. **逐项对齐**：在真实会话上把 README「功能」和 PLAN §11 的每一行走一遍。核对 §7 的测试清单，确认每个 Dart 测试都有对应的 Kotlin 测试，或者写明了理由。
2. **性能**：在 release 包上看时间线滚动、预测性返回、图片查看器。Baseline Profile 可以选做。
3. **改包名**：把调试版的 `.dev` 改成 `.debug`。Kotlin 调试版第一次覆盖安装到 Flutter 调试版上之后，要重新登录，可以接受。登录要请用户本人来。`.dev` 应用要不要卸载，问用户。
4. **删除 Flutter**：
   - 删掉 `lib/`、`test/`、`integration_test/`、`pubspec.yaml`、`pubspec.lock`、`analysis_options.yaml`、`.metadata`，以及 Flutter 的宿主工程 `android/`；
   - 删之前先把图标等资源搬进 Kotlin 工程，`android/key.properties` 也要移走，它不在 git 里，要手动移；
   - 把 `kotlin/` 的内容移到仓库根目录；
   - `.gitignore` 里 Flutter 的规则换成 Gradle 的。
5. **CI**：
   - `ci.yml` 只保留 Gradle 的 job；
   - `release.yml` 重写：先核对 tag 和 `versionName` 一致，再用 Secrets 里的密钥打 release APK，核对证书指纹等于 PLAN §9 里的值，最后生成 `SHA256SUMS.txt` 并建草稿 Release。
6. **文档**：
   - CLAUDE.md：改常用命令、架构速览、约定（比如依赖注入改成 AppGraph，版本号只在 `build.gradle.kts` 里）；
   - README：技术栈、从源码构建；
   - PLAN：§3 记下改用 Kotlin 的决定，更新 §4 目录结构、§9 分发、§11 进度；
   - 本文：标为已完成。
7. **发版**：版本号问用户，按 PLAN §9 的流程走。发布说明里写明三点：已经用 Kotlin 重写；装上后要重新登录、重新设置；可以直接覆盖安装旧版。

## 11. 进度

| 阶段 | 状态 | 提交 | 备注 |
|---|---|---|---|
| K0 工程骨架 | 未开始 | | |
| K1 网络、会话、登录 | 未开始 | | |
| K2 首页时间线与微博卡片 | 未开始 | | |
| K3 详情、评论、媒体 | 未开始 | | |
| K4 用户主页、搜索、通知、网页 | 未开始 | | |
| K5 互动与发布 | 未开始 | | |
| K6 设置、关于、宽屏、诊断 | 未开始 | | |
| K7 对齐、切换、发版 | 未开始 | | |
