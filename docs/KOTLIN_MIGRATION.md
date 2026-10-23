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
- **SDK 版本**：minSdk 24，和 Flutter 版一样，不丢用户；targetSdk 36，与 Flutter 版相同。compileSdk 是 37（K0 时改的，原计划 36）：当前的 AndroidX（Compose 1.12、lifecycle 2.11、Navigation 3 1.2、core 1.19）都要求用 37 编译。compileSdk 只决定能调用哪些 API，运行时的行为由 targetSdk 决定。本机的 SDK 平台有 35、36、37.0。
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

版本号在 K0 时去 Google Maven 和 Maven Central 查最新稳定版，统一写进 `kotlin/gradle/libs.versions.toml`。原计划沿用本机的 AGP 9.1.0、Kotlin 2.4.0、Gradle 9.3.1（Flutter 宿主工程在用），K0 时发现当前的 AndroidX 要求 compileSdk 37，AGP 9.1 不支持，于是改用 AGP 9.4.1，它又要求 Gradle 9.6 以上，所以是 Gradle 9.8.2（wrapper 在 `kotlin/` 里），Kotlin 也顺带用了最新的 2.4.21。JDK 仍是本机的 17（Temurin）。依赖下载失败时，先看 PLAN「开发环境注意」里关于代理分流的说明。

| 用途 | Flutter 版 | Kotlin 版 | 说明 |
|---|---|---|---|
| 界面 | Flutter Material | Jetpack Compose + Material 3（用 Compose BOM 管理版本） | |
| 配色 | `ColorScheme.fromSeed(#3F51B5)` | 把 Flutter 版生成的配色抄成固定值 | K0 时写一个临时 Dart 测试，打印 `buildTheme(Brightness.light/dark).colorScheme` 的每个颜色，抄进 `Color.kt`，这样两版颜色完全一致，不用引入取色库。未读红色 `#F5222D` 照搬 |
| 窗口分档 | `adaptive_layout.dart` | `material3-adaptive` 的 `currentWindowAdaptiveInfo()` | 分界同样是 600 和 840。限宽、宽卡片这些行为照搬 Dart 版 |
| 导航 | go_router + StatefulShellRoute | Navigation 3（`androidx.navigation3`），K0 定的 | 页面栈就是我们自己持有的一个列表（`Navigator.backStack`），四个页签放在栈底的 `Route.Home` 里（`HomeShell` 用 `SaveableStateHolder` 保留各页签的状态），推入、返回、预测性返回的动画都可以自己给。理由见 §11 的 K0 备注 |
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
| 界面语言 | `locale: zh_CN` | 系统组件（返回按钮说明、日期等）也要固定显示中文 | K0 定的：`MainActivity.attachBaseContext` 里 `applyOverrideConfiguration` 只覆盖语言，不引入 AppCompat。文案像 Dart 版一样直接写在代码里，只有中文，不做多语言 |
| 测试 | flutter_test | JUnit 4、kotlinx-coroutines-test、Turbine、OkHttp MockWebServer、Robolectric + Compose UI Test；设备测试用 AndroidX Test | §7 |
| 格式与检查 | `dart format`、`flutter analyze` | Spotless + ktfmt（kotlinlang 风格，K0 定的），Android Lint | `./gradlew spotlessCheck lint testDebugUnitTest` 必须全部通过 |

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

- **常用命令**（在 `kotlin/` 目录下）：
  ```sh
  ./gradlew spotlessApply                                        # 格式化
  ./gradlew spotlessCheck lint testDebugUnitTest assembleDebug   # 与 CI 相同
  adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
  ./gradlew assembleBenchmark   # 测流畅度用，见下面「测流畅度」
  ```

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
- **CI**：K0 起 `ci.yml` 里有一个 Kotlin 的 job，在 `kotlin/` 目录下运行 `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug`（wrapper 在 `kotlin/` 里，所以不是 `-p kotlin`）。Flutter 的 job 保留到 K7。
- **Robolectric 的 SDK**：`app/src/test/resources/robolectric.properties` 设为 34。Robolectric 跑 SDK 35 及以上要 Java 21，本机是 17。个别测试可以用 `@Config(sdk = [...])` 指定别的版本，例如 `MainActivityTest` 同时在 24 和 34 上跑。

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
| K0 工程骨架 | ✅ 完成 | 见 git log | 见下方「K0 备注」 |
| K1 网络、会话、登录 | 代码和测试完成，等真实会话验收（用户登录 `.dev`、设备测试） | 见 git log | 见下方「K1 备注」 |
| K2 首页时间线与微博卡片 | 代码和测试完成，等真实会话验收（时间线、翻页、图片、GIF、深色、release 包滚动） | 见 git log | 见下方「K2 备注」 |
| K3 详情、评论、媒体 | 代码和测试完成，等真实会话验收（PLAN §11「M3 详情与媒体」一行） | 见 git log | 见下方「K3 备注」 |
| K4 用户主页、搜索、通知、网页 | 代码和测试完成，等真实会话验收（PLAN §11「M4 主页与热搜」「通知页」「搜索页签」「原生搜索结果」几行） | 见 git log | 见下方「K4 备注」 |
| K5 互动与发布 | 代码和测试完成，等真实会话验收（PLAN §11「M5 轻互动」「发微博」「转发」「评论带图」几行；每项真实写操作先问用户） | 见 git log | 见下方「K5 备注」 |
| K6 设置、关于、宽屏、诊断 | 代码和测试完成；设置、字体大小、深色、关于、开源许可、右下角胶囊已在模拟器上看过（未登录）；时间线宽卡片和详情页左右并排等登录后用 `wm density` 核对 | 见 git log | 见下方「K6 备注」 |
| K7 对齐、切换、发版 | 未开始 | | |

### K0 备注

- **导航选了 Navigation 3**：页面栈是普通的 `SnapshotStateList`（`ui/navigation/Navigator.kt`），由我们自己推入和弹出，`Route` 里可以直接带上一页已有的数据（相当于 go_router 的 `extra`），不用序列化。四个页签不是导航目的地，而是栈底 `Route.Home` 里的状态，再点页签、滚动收起这些都留在 `HomeShell` 里做。Navigation 3 默认的转场是 700 ms 的淡入淡出，不像 Android，所以 `ui/navigation/Transitions.kt` 照 Flutter 的 `FadeForwardsPageTransitionsBuilder`（450 ms，新页从右边 25% 处滑入并淡入，旧页左移 25% 并在前 25% 的时间里淡出）和 `PredictiveBackPageTransitionsBuilder`（跟手缩到 0.9，向滑动方向偏移屏宽的 1/20，松手后淡出）重写了三种转场。和 Flutter 相比少了两点：手指上下移动时页面不跟着上下偏移，缩小时没有圆角。
- **格式化选了 ktfmt**（kotlinlang 风格）：和 `dart format` 一样只管格式、没有配置项；ktlint 还要另外配置才能接受 Compose 的大写函数名。ktfmt 会删掉没用到的 import。
- **界面语言**：`MainActivity.attachBaseContext` 里用 `applyOverrideConfiguration` 只覆盖语言，其余配置（深色模式、字体大小等）照常跟随系统变化。Android 8 以前 `Configuration()` 会把字体缩放设成 1，所以要把 `fontScale` 置回 0（未设定）；`MainActivityTest` 在 API 24 和 34 上核对了语言是中文、系统字体大小保留，去掉这一行 API 24 的测试就会失败。
- **字体大小**：`TextSize.applyTo()` 返回一个包装过的 `Density`，在根部经 `LocalDensity` 提供：文字先按系统的方式换算（保留 Android 14 起的非线性缩放），再乘倍数，非文字的尺寸不变。与 Dart 版的 `_ScaledTextScaler` 相同。设置页还没有，K6 时在真机上核对。（K6 发现对话框、底部弹层、菜单不在这里生效，见「K6 备注」。）
- **设置**：`SettingsController`（`StateFlow`）的 `update()` 立即生效，保存在 `AppGraph.appScope` 里排队执行（离开页面不会中断），每次保存的都是最新的设置。`AppGraph.create()` 在 `Application.onCreate` 里读入设置（DataStore，在第一帧之前）。
- **配色**：用临时的 Dart 测试打印了 Flutter 3.47 的 `ColorScheme.fromSeed(#3F51B5)`，抄进 `ui/theme/Color.kt`（Flutter 已去掉的 `background`、`surfaceVariant` 分别取 `surface`、`surfaceContainerHighest`）。字体层级不用改：Flutter 给中文用的 `dense2021` 与 Compose 默认的 M3 字号、行高、字距完全一样（也打印核对过）。导航胶囊与 Flutter 版截图对比，像素级一致。
- **Manifest**：照搬了 Flutter 版的 `configChanges`，旋转、折叠、切换深色模式都不重建 Activity，页签和页面状态保留（模拟器上切深色模式核对过）。**顺手修的 Flutter 版问题**：Android 12 起 `allowBackup="false"` 不再阻止设备间迁移（换手机时的数据传输），WebView 的 Cookie（也就是登录态）会被带到新手机上，违反「登录态只在本机」。Kotlin 版加了 `data_extraction_rules.xml`，云备份和设备间迁移都排除所有数据。Flutter 版没有改（K7 后就不存在了）。
- **无障碍**：Compose 不把已选中的 `Role.Tab` 标为可点击，读屏用户就没法「再点一次当前页签」。K2 已给选中的页签加了自定义无障碍操作「回到顶部或刷新」（有测试）。页签的提示（长按显示名称）是自己触发的 `TooltipBox`，读屏不会多停一个空节点（`HomeShellTest` 核对）。
- **构建类型**：release 开了 R8（minify + shrinkResources），签名还没接（K7）。`benchmark` 构建类型（§6「测流畅度」）K0 就加上了，R8 后约 1.3 MB，在模拟器上启动、打开设置页、返回都正常。APK 里有两个 AndroidX 的小原生库（`libandroidx.graphics.path.so`、`libdatastore_shared_counter.so`，每个约 10 KB，四种 ABI 都有），K7 出通用 APK 不受影响。
- **版本号**：versionCode 5022，versionName 暂时是 `0.12.0-dev`，发版时再问用户。
- **测试**：迁移了 `app_settings_test` 的存储、`TextSize` 和 `listPixels` 部分（`viewerVariant` 依赖 `PicInfo`，放到 K2）；`app_info_test` 不迁移（版本号用 `BuildConfig`）。另外加了 `DataStoreSettingsStorageTest`、`HomeShellTest`（页签切换、打开设置页、返回箭头和系统返回）、`MainActivityTest`。共 19 个，全部通过。测试用的 `AppGraph` 由 `support/TestGraph.kt` 的 `testGraph()` 给出。
- **在 emulator-5554 上核对过**：`.dev` 与 Flutter 的 release、`.debug`、旧的 `app.maxbo` 同时装着；页签切换；长按页签显示名称；从「我」打开设置页；预测性返回（`input motionevent` 注入手势）跟手缩小、滑回边缘松手取消、松手返回「我」页签；深色模式。CI 的 Kotlin job 还没在 GitHub 上跑过（要推送后才知道），本地用同一条命令从 clean 构建通过。

### K1 备注

- **OkHttp 的结构**：`WeiboHttp`（`core/net/WeiboHttp.kt`）包着两个共用连接池的 `OkHttpClient`。Cookie 走 OkHttp 的 `CookieJar` 接口（`WeiboCookieJar` 实现了它），微博的请求头（Referer、X-Requested-With、XSRF、Origin）在网络拦截器里加。这两样每一跳重定向都会执行，所以 weibo.com 的 Cookie 不会跟着重定向发到 login.sina.com.cn，中间每一跳设置的 Cookie 也都会存下来。Dart 版的 dart:io 跟随重定向时会把请求头原样带到下一跳，中间跳的 `Set-Cookie` 也拿不到；Kotlin 版更接近浏览器。UA、Accept 这些对所有请求都一样的头在应用拦截器里加，免得 OkHttp 换成自己的 UA。
- **限流**：`RequestThrottle` 用协程实现（公平的 `Semaphore` 加 `delay`），在 `WeiboHttp.send()` 里包住每次请求，等待可以随协程取消。Dart 版的三个测试照搬，用 `runTest` 的虚拟时间。
- **`retryOnConnectionFailure`（§5 要求 K1 决定）**：读请求保持 OkHttp 默认的开启，写请求关掉。开启时 OkHttp 只在连接层重试，比如复用的长连接已经被服务器关掉、或者一个 IP 连不上换下一个，浏览器也是这样做的，不算「自动重试」。但请求已经发出后连接断了，OkHttp 也可能重发，对写请求来说就是可能评论两次，所以写请求（`postForm`、以后的上传）用关掉重试的那个 client。
- **JSON 判断**：和 Dio 一样，只有 `Content-Type` 是 JSON 时才解析成 JSON，否则按「无法识别的内容」处理。`ok` 只认数字和布尔值，`message` 为 JSON null 时取 `msg`，与 Dart 版的语义一致（有测试）。
- **登录态存储**：`EncryptedFileSessionStorage` 把会话 JSON 用 Android Keystore 里的 AES-256-GCM 密钥（`KeystoreSessionCipher`）加密后写进 `noBackupFilesDir/session`（`AtomicFile` 写入）。读不出来（文件坏了、密钥没了）就当作没登录并删掉文件。启动时和设置一起在第一帧之前读入。测试用假的 cipher 核对了读写、清除和坏文件。
- **会话**：`SessionController` 的状态是 `StateFlow<SessionState>`。保存都在一个 `Mutex` 里排队，每次保存的都是当时的会话，退出或过期之后的保存不会把会话写回去。续期共用一次（`Deferred`），续期之后才被拒的请求直接重发，时间用 `System.nanoTime()`。Dart 版的 11 个测试全部照搬。
- **网络测试**：`test/support/FakeWeibo.kt` 是一个本地的 HTTPS MockWebServer，证书覆盖 weibo.com、weibo.cn、sina.com.cn 等域名，DNS 全部指向它，所以请求走的是完整的 OkHttp（TLS、重定向、Cookie、拦截器），重定向到 passport 这类情况可以直接测。用到它的测试类要跑在 Robolectric 下：Android 版的 OkHttp 5 从应用的 assets 读公共后缀表（子域名设置 `Domain=.weibo.com` 的 Cookie 时要查），需要 `OkHttp.initialize(context)`。正式运行时，OkHttp 自带的 androidx.startup 初始化器会做这一步（已在合并后的 manifest 里核对）。
- **WebView**：登录页和续期都用 `android.webkit.WebView`，开 JavaScript 和 DOM storage（webview_flutter 在 Android 上默认也开），UA 与 API 请求相同，只放行 http(s)。登录页的内容区加了 `imePadding()`（全面屏下 `adjustResize` 不再缩小窗口）。续期的 WebView 用 Application 的 Context 创建，不挂到界面上，结束后载入 `about:blank` 并 `destroy()`。读 Cookie 直接用 `CookieManager`（`WebViewCookies`），替代了 Flutter 版的平台通道。
- **提示条**：全应用共用一个 `SnackbarHostState`（`LocalSnackbars`），每个页面的 Scaffold 都显示它，相当于 Flutter 的 ScaffoldMessenger。登录成功后登录页先关掉，「登录成功」显示在下面的页面上。
- **「我」页**：未登录时是登录提示加设置入口；登录后是账号、设置、（调试版的）开发者诊断和退出登录（确认对话框）。点账号进主页要等 K4。首页、搜索、通知三个页签暂时是占位页，未登录时显示登录提示，与 Dart 版一致。
- **诊断页**：调试版「我」页的「开发者诊断」进入，K1 只有「模拟登录过期」一个探针（右上角 🔄），做法照 Dart 版：把会话的 `SUB` 换成无效值，再用应用自己的 API 请求一次首页时间线（K2 之前直接请求 `friendstimeline`，只数条数），看续期是否成功、用了多久。日志前缀 `diag:`。
- **设备测试**：`app/src/androidTest/.../WebViewCookiesTest.kt` 对应 Flutter 的 `integration_test/cookie_bridge_test.dart`：读 `CookieManager` 写入的 Cookie、读本地页面响应设置的 HttpOnly Cookie、清空全部 Cookie。为此调试版加了 `network_security_config.xml`，只允许明文访问 `localhost`（与 Flutter 调试版相同）。它会清空被测应用的 WebView Cookie，只能在没有真实登录的模拟器上跑（§7）。
- **测试**：迁移了 `cookie_jar_test`、`request_throttle_test`、`weibo_dio_test`（另加了跨域请求不带 XHR 头、重定向每一跳的 Cookie 两项）、`weibo_exception_test`、`session_controller_test`、`session_renewer_test`、`page_config_test`、`account_test`，`cookie_bridge_test` 拆成 `WebCookiesTest`（解析 Cookie 头）和设备测试，`weibo_api_test` 里属于 K1 的部分（表单格式、`ok` 但带 `error`）用 `postForm` 直接测，另加了 `fetchCurrentAccount` 和会话存储、「我」页的测试。共 81 个，全部通过。

### K2 备注

- **模型**：`Status`、`PicInfo`、`WeiboUser`、`FeedPage`、`Paged` 都是 data class，照 Dart 逐字段翻译（`text_raw` 转 HTML、`mix_media_info`、广告判定、`title` 里的「他赞过的微博」、`page_info` 卡片去重等）。时间用 `java.time.Instant`，为此开了 core library desugaring（minSdk 24）。`formatPostTime` 多了一个 `zone` 参数，测试固定用 Asia/Shanghai。
- **正文**：`parseStatusText` 用 jsoup（`wholeText()` 不折叠空白，与 Dart 的 `.text` 一致），链接 URL 用 OkHttp 的 `HttpUrl`，非 http(s) 的仍按纯文本。`StatusText` 是一个 `AnnotatedString`：链接和「展开」是 `LinkAnnotation.Clickable`，表情和链接前的小图标是 `InlineTextContent`，边长为字号的 1.2 倍（sp），跟着字体大小缩放。
- **图片**：Coil 3，`ImageLoader` 由 `AppGraph` 持有（测试换成 `FakeImageLoaderEngine`，每张图都是灰块，不联网）。所有图片请求都带 `Referer: https://weibo.com/`（与 Dart 版一样，不只 sinaimg）；内存和磁盘缓存的 key 都去掉签名参数（`imageCacheKey`）。GIF：API 28 起用 `AnimatedImageDecoder`，以下用 `GifDecoder`。磁盘缓存放在 `cacheDir/image_cache`，上限取启动时的设置；Coil 的磁盘缓存自己按 LRU 清理，比 Dart 版只在启动和调低上限时清理更及时。运行中调低上限怎么生效留到 K6。加载失败时点图片重试，与 Dart 版一样会先于外层的点击（头像、宫格）。
- **卡片**：`StatusCard` 照 Dart 的窄卡片（头部、标签、正文、转发框、九宫格/混排/视频封面/链接卡片、计数）。宽卡片（`_WideLayout`）留到 K6；点赞按钮在 K5 之前只显示状态、吞掉点击；⋯ 菜单只在传了 `onDelete` 时出现（K5 才传）。计数用 `indication = null`，按下时自己和卡片都没有水波纹，对应 Dart 的 ink 测试（没有翻译成测试）。
- **列表**：`PagedController`（`core/paging/`）是通用的分页控制器，状态是 `ListLoad`（加载中、失败、`PagedState`），用一个代数（generation）丢弃过期的结果：刷新成功、换账号、重新加载都会让之前发出的翻页结果作废。`TimelineController` 放在 `AppGraph` 里（以后发微博、转发要往里插），跟着会话的 uid 重置，未登录时是空列表、不发请求。时间线的 9 个 Dart 测试全部照搬。
- **加载更多**：`LoadMoreTrigger` 只在滚动位置变化时检查，离末尾不到 800dp（按可见项的平均高度估算）就加载下一页；短得不能滚动的列表等页脚的「加载更多」按钮，与 Dart 版一致。失败后要用户点重试。
- **滚动收起与再点页签**：`BarsOnScroll` 挂在首页 Scaffold 的 `nestedScroll` 上，只算手指拖动（`NestedScrollSource.UserInput`，惯性滑动不算），阈值 24dp，拖到顶（往上滚却没有可消耗的距离）就显示。导航胶囊往下移 1.5 倍自身高度，250 ms。再点当前页签经 `LocalTabReselect`（每个页签一个 `SharedFlow`）通知页面：不在顶部就滚回顶部，在顶部就刷新（显示下拉刷新的指示器）。下拉刷新用 Material 3 的 `PullToRefreshBox`。
- **跳转**：`rememberStatusCardActions()`（`ui/status/StatusActions.kt`）对应 Dart 的 `statusCardActions`，外部链接用 `Intent.ACTION_VIEW`，打不开时提示「无法打开链接」。详情、看图、视频、主页、按昵称的主页、搜索结果、应用内网页这些页面已经有了 `Route`，暂时是「迁移中」的占位页，到对应阶段替换。`Route.Web` 带一个 `onClose`，页面关闭时调用，用于「去验证」后自动重试一次。
- **宽屏**：`readingPadding()` 对应 `ReadingList` 的限宽居中，首页按 `feedColumnWidth`（≥840dp 时 1000），在 K6 宽卡片之前宽窗口下会是加宽的窄卡片。
- **测试**：迁移了 `status_test`、`format_test`、`weibo_image_test`、`status_text_parser_test`、`status_card_test`（宽卡片的几项留到 K6，ink 一项见上）、`load_state_test`、`tab_reselect_test`（在 `TimelinePageTest` 里对真实的首页做）、`timeline_controller_test`，以及 `test/support/statuses.dart` 的全部构造函数（`support/Statuses.kt`，配一个小的 JSON 写法 `support/Json.kt`）。`timeline_page_test` 测的是下拉刷新时检查未读，等 K4 做未读时再迁。`app_settings_test` 剩下的 `viewerVariant` 部分也补上了。共 161 个，全部通过。Robolectric 里按坐标点文字不可靠（文字排版只是近似），测试里点链接用 `performFirstLinkClick`，点转发框用它的 test tag。
- **测试不联网**：`testGraph()` 不传 `FakeWeibo` 时，请求一律失败（离线的 OkHttp 客户端），避免测试碰到真实的微博。

### K3 备注

- **详情页**：`StatusDetailViewModel` 挂在详情页的导航条目上（Navigation 3 的 ViewModel 装饰器），页面开着时，打开过的评论列表（按热度、按时间各一个）和转发列表都留着，再切回去不重新请求（有测试）。转发列表在第一次切到转发页签时才加载。下拉刷新同时刷新微博和当前列表。页签栏是 `LazyColumn` 的 `stickyHeader`，顶栏（`HidingAppBar`）和底部的评论栏随滚动收起。卡片自己的「展开」不跳转。评论栏、转发和赞的按钮在 K5 之前只占位、点了没反应；页签上的计数在 K5 之前不含本地增减；宽窗口的左右并排在 K6。
- **翻页列表**：`pagedItems()`（`ui/widget/PagedItems.kt`）对应 Dart 的 `PagedSliverList`。开发中发现切换列表时，一帧里会用这个列表的内容函数去画另一个列表的数据（Compose 的 `collectAsState` 换了 Flow 后第一帧仍是旧值），导致类型转换崩溃；现在评论和转发各自收集、各自带类型，并按列表 `key`，测试覆盖了来回切换。
- **评论**：`CommentTile` 照 Dart（作者、博主标签、正文隐藏下面已显示图片的「查看图片」链接、96dp 的图片、时间和来源、楼中楼预览）。评论的赞在 K5 之前只显示，按下没有水波纹，也吞掉点击。楼中楼页（`Route.Comments`）显示根评论、「N 条回复」和分页的回复；回复、删除在 K5。
- **看图**：Telephoto 的 `ZoomableAsyncImage`（缩放、双击放大、长图）加 `FlickToDismiss`（上下甩动关闭），放在 `HorizontalPager` 里。查看器是一个全屏的对话框场景（`DialogSceneStrategy`，去掉了遮罩），所以甩动时背景变透明，能看到下面的页面，与 Flutter 版的透明路由一样。点一下关闭。和 Dart 版的差别：长图是否「铺满宽度、从顶部开始」按 `PicInfo` 的宽高判断（Dart 版按加载出的图片判断），没有宽高的评论图片按完整显示；加载中只显示不定进度的圈（Coil 不给下载字节进度）；双击放大用 Telephoto 默认的倍数。
- **视频**：Media3 ExoPlayer，请求带 Referer 和与登录相同的 UA；画面用 `ContentFrame` 的 TextureView，这样预测性返回缩小页面时视频跟着缩（SurfaceView 不会）。选地址、过期判断、播放出错时换一次新地址并从原位置接着播，都照 Dart 版（`core/media/VideoSource.kt`）。应用退到后台时暂停。控件：中间的播放/暂停/重播、底部的时间和进度条，播放中 3 秒后隐藏，点画面切换。
- **测试**：迁移了 `comment_test`、`comments_controller_test`、`comment_tile_test`（ink 一项同 K2 的说明）、`video_source_test`、`weibo_api_test` 里 `fetchStatus`/`fetchComments`/`fetchReplies`/`fetchReposts` 四组，`status_detail_page_test` 里不涉及写操作和宽窗口的两项，另加了「点楼中楼进入楼中楼页」。`status_detail_page_test` 的其余各项（发评论、回复、删除、点赞、转发、计数）和 `comment_replies_page_test` 全部是写操作，到 K5 迁移。共 194 个，全部通过。详情页的测试用 `@Config(qualifiers = "w411dp-h891dp")`，Robolectric 默认的屏幕太小，评论在首屏之外不会被组合。

### K4 备注

- **用户主页**：`ProfileViewModel` 挂在主页的导航条目上：按 uid 打开时资料和微博同时加载；按昵称（@）打开时先查资料，拿到 uid 再加载微博。头部在资料到达前显示占位条，资料加载失败时微博照常显示，头部给出原因和「重试」。顶栏随滚动收起（`HidingAppBarBody`）。主页里点这个用户自己的头像不再跳转。「我」页的账号一行现在进入自己的主页。
- **搜索页签**：顶部的搜索栏是自己画的胶囊（`BasicTextField`，样式照 Material 3 的 SearchBar），输入的内容存在页签的状态里，从结果页返回或切换页签后还在。热搜榜在第一次打开页签时加载（ViewModel 挂在首页条目上，切换页签不重新请求），之后只在下拉刷新或再点一次页签（在顶部时）时刷新。收起键盘的时机：搜索、点热搜条目、拖动列表；离开页签时输入框不在界面上，键盘也随之收起。Flutter 版点输入框以外的地方也会收起，Kotlin 版没有单独处理这一项。
- **搜索结果页**：`SearchResultsController` 在加载第一页时记下话题，有话题时列表上方显示话题头。右上角的地球图标打开网页版结果（`weiboSearchUrl()`，应用内 WebView）。正文里点的话题如果就是正在搜的这个，不再打开新页面。
- **通知页**：四个列表各一个分页控制器，放在挂在首页条目上的 `NotificationsViewModel` 里，第一次显示时才加载，之后切换页签、切换首页页签都保留。页签栏下面是 `HorizontalPager`，和 Flutter 的 `TabBarView` 一样可以左右滑。打开一个带红点的页签时清掉红点，如果列表之前加载过就重新加载（保留旧内容直到新内容到达，与 Dart 版一致）。再点一次首页的「通知」页签只作用于当前显示的列表。
- **未读提醒**：`UnreadNoticesController` 放在 `AppGraph` 里，只在三个时机检查：登录或打开应用时、从后台回到应用时（`ON_STOP` 之后的 `ON_START`，一分钟内最多一次）、下拉刷新首页时（一分钟内最多一次）；加载更多不检查，不轮询。有未读时导航胶囊的通知图标变红（选中时不变）。换账号时清空。
- **应用内网页**：`WebPage` 用 `android.webkit.WebView`，UA 和 Cookie 与登录页相同。系统返回和顶栏的返回箭头都先在网页里后退，网页能后退时顶栏另有「关闭」；菜单里有「刷新」「在浏览器中打开」。主框架的 http 改成 https 再加载，非 http(s) 的链接（`sinaweibo://` 等）不打开。「去验证」打开它，页面关闭后自动重试一次（`Route.Web.onClose`）。
- **测试**：迁移了 `user_profile_test`、`hot_search_test`、`topic_test`、`notice_test`（合在 `K4ModelsTest` 里）、`weibo_api_test` 的主页、搜索、热搜、未读、通知列表各组、`profile_page_test`、`search_page_test`、`search_results_page_test`、`notifications_page_test`，以及 K2 留下的 `timeline_page_test`（下拉刷新时检查未读，一分钟一次）。「顶栏随滚动收起」的两项没有单独翻译：收起逻辑在 `BarsOnScroll` 里，首页的测试已经覆盖了滚动。共 248 个，全部通过。`FakeWeibo` 的证书加上了 `*.api.weibo.com`（通配符只匹配一级子域名，`rm.api.weibo.com` 原来对不上）。

### K5 备注

- **写接口**：`WeiboApi` 加了点赞、评论、回复、删评论、转发、发微博、删微博、`allowsCommentPicture` 和 `uploadPicture`，都照 Dart 的表单字段（有测试逐项核对）。上传走 `WeiboHttp.send(upload = true)`：写请求的客户端（不自动重连）加上两分钟的读写超时；`PlainCrossOrigin` 标记去掉 XHR 和 XSRF 头，Cookie 和 Origin 照常带。
- **弹层**：Compose 没有 Flutter 那样挂在导航栈上的弹层，新加了 `Overlays`（`ui/widget/Overlays.kt`）：`show()` 把弹层或对话框放到整个应用上方，挂起到关闭，返回关闭时的结果，和 `showModalBottomSheet` 的用法一样。`MaxboApp` 和测试的 `TestHost` 都提供它。两处与 Flutter 不同：发送中的弹层不能被拖走、点外面或按返回关掉（Flutter 可以关，请求在后台继续；Kotlin 关掉会取消请求，写请求半途取消不知道发没发出去）；弹层里的「去验证」先关掉弹层再打开网页（弹层是窗口，会盖在新页面上面），草稿保留，验证完重新打开即可。
- **点赞**：`LikesController` 放在 `AppGraph` 里，规则照 Dart（改动只套用在改动时的那份数据上，新加载的数据优先；进行中不重复发；被拒绝时撤回并提示）。点赞在 `appScope` 上进行，离开页面也会完成或撤回。换账号（uid 变化）时清空；Dart 是任何会话变化都清空，续期也算，Kotlin 只看账号。
- **打开的列表**：Riverpod 的 `ref.exists()` 换成了 `OpenLists`（`AppGraph.openLists`）：详情页的评论和转发列表、用户主页的微博列表在 ViewModel 存在期间登记，ViewModel 清除时注销；首页时间线直接用 `AppGraph.timeline`（还没加载过的列表不会被改动）。发微博、转发、删除自己的微博、在楼中楼里回复或删除，都经它改动已打开的列表，不为此发请求（PLAN §2）。同一条微博开着两个详情页时两个都会更新。
- **评论弹层**：`showCommentComposer()` 照 Dart：第一次点图片按钮时才请求 `checkAllowCommentWithPic`，点「发送」才上传，上传成功的 pid 留在草稿里，重发不再上传。草稿按回复对象存在页面的 ViewModel 里。选图用系统照片选择器（`PickVisualMedia`/`PickMultipleVisualMedia`，Android 13 以下没有时退回打开文档的界面，可能多选，所以保留「多选的 N 张没有加上」的提示），图片是 content URI，经 `ContentPictureReader` 读出；读不出来时显示「图片读取失败」（Dart 的读文件失败没有处理）。测试通过 `MaxboApp(picturePicker = …)` 和 `testGraph(pictures = …)` 换掉选图和读图。
- **转发、删除**：`StatusWrites`（`rememberStatusWrites()`）提供转发和删除自己的微博，卡片的转发数和 ⋯ 菜单都接到它；转发草稿 `RepostDrafts` 放在 `AppGraph`，换账号清空。详情页删除自己的微博后，只在这个详情页仍在最上面时才关闭它。
- **详情页和楼中楼**：评论栏、页签上的计数（含本页的增减，新加载的数据到达后以它为准，判断方式同 Dart，比较的是 `Status` 对象本身）、底部栏的转发和赞、点评论回复、长按自己的评论删除，全部照 Dart。楼中楼页的评论栏和点回复都能回复，长按自己的回复删除，改动同时套用到下面详情页里那条评论的预览上。
- **发微博**：`Route.Compose`，入口是首页页签右下角的「发微博」按钮（只在登录时、首页页签上显示，随导航胶囊收起）。草稿在 `AppGraph.composeDraft`，关页面不丢，换账号清空。Flutter 版从下往上弹出（`fullscreenDialog`），Kotlin 版和其他页面一样的进入动画。
- **测试**：迁移了 `likes_test`（另加了「退出后清空」）、`comment_composer_test`、`repost_test`、`compose_draft_test`（另加了「换账号清空」）、`compose_page_test`、`comment_replies_page_test`、`weibo_api_test` 的写接口和 `parseUploadedPid`、`status_detail_page_test` 的写操作各项（放在 `StatusDetailWritesTest`），另加了首页「发微博」按钮的测试。`status_actions_test` 只测跳转，K2 已经覆盖；`status_detail_page_test` 的「按钮没有水波纹」同 K2 的说明，没有翻译。关弹层用遮罩的无障碍点击（`support/Sheets.kt` 的 `closeSheet()`），因为点遮罩中心会点到弹层上。共 305 个，全部通过。

### K6 备注

- **设置页**：照 Dart（主题、字体大小、图片质量、缓存上限、清除缓存并显示已用大小、关于）。选项对话框没有按钮，点选即关，与 Flutter 的 `SimpleDialog` 一样（用 `BasicAlertDialog` 自己排）。
- **图片缓存**：Coil 的磁盘缓存在创建时定下上限，所以加了 `ImageDiskCache`（实现 Coil 的 `DiskCache` 接口、转发给 Coil 的实现）：调整上限时先关掉旧的，在同一目录上按新上限重建，再开一个空的编辑立即放弃，让 Coil 按 LRU 马上清到新上限之内（Coil 在编辑结束、超出上限时清理）。设置页经 `PictureCache` 接口读大小、清除（磁盘和内存）和改上限，测试换成假的。Coil 平时就按 LRU 清理，比 Dart 版只在启动和调低上限时清理更及时。
- **字体大小页**：滑动条两端各有一个固定大小的「A」按钮，到头时变暗；滑动条读屏时读档位名（`stateDescription`）。示例微博和评论接不到按下（`pointerInput` 在 Initial 阶段吃掉按下），列表仍可滚动。
- **对话框里的字体大小（修正 K0）**：K0 以为在根部提供 `LocalDensity` 就覆盖所有页面和对话框，实测不是：对话框、底部弹层、菜单、提示都是单独的窗口，窗口的根会按系统重新提供 `LocalDensity`，里面的文字回到系统大小（Flutter 的对话框在同一棵树里，没有这个问题）。现在根部另外提供 `LocalTextSize`，这些窗口的内容用 `KeepTextSize` 重新套上（`AppAlertDialog`、`OverlaySheet`、两个下拉菜单、导航胶囊的长按提示、图片查看器的对话框场景、开源许可的对话框），`applyTo()` 改成重复套用不会叠加。有测试，模拟器上「特大」时的主题对话框也看过。以后新加对话框、菜单要记得套。
- **关于页**：文案照 Dart（与 README 一致），项目主页和问题反馈用外部浏览器打开（`openExternally()`，链接和网页页面的「在浏览器中打开」也改用它）。
- **开源许可**：用 AboutLibraries 的 Gradle 插件（15.3.0）在构建时生成 `res/raw/aboutlibraries.json`，页面自己读这个文件列出库名、版本和许可证，点开看许可证全文。**偏离计划**：没有用 AboutLibraries 的界面库，因为 14.x、15.x 的界面库都是 Java 21 字节码，单元测试的 JDK 17 加载不了（应用本身能编译，D8 会转换）；自己写的列表也让许可证对话框跟着字体大小走。插件第一次生成用了约 9 分钟（解析依赖的 POM），之后走缓存。
- **宽屏**：`windowSize()` 按窗口宽度（`LocalWindowInfo`）分档。时间线卡片用一个自定义 `Layout`（`CardLayout`）同时处理窄和宽：窄时上下排列，宽（卡片可用宽度加内边距不小于 840dp）时媒体或转发框在右边，正文限 400dp（无媒体时 640dp），计数在正文下面或卡片底部，与 Dart 的 `_WideLayout` 一致；不用每张卡片一个 `BoxWithConstraints`。详情页在 expanded 档用 `ReadingPanes` 左右并排，评论栏在右栏下面。首页导航胶囊在 expanded 档移到右下角，切换时胶囊重新出现。「我」页漏了阅读宽度限制（K4），一起补上。
- **修的 K2 问题**：九宫格每格的宽度按 dp 算出来再各自取整到像素，三格加间距可能比整行多 1 像素，第三格就换到下一行，变成两列。宽卡片的测试先发现了这个问题；手机上分数密度时同样会出现。改成每行用 `weight` 平分，不再有取整误差。
- **诊断页**：只迁了还用得上的：「核对接口（只读）」用应用自己的接口依次读账号、时间线、一条微博和它的评论、主页、热搜、搜索、未读提醒，只记数量（不读通知列表，那会把通知标成已读；用 `WeiboApi.withoutRenewal()`，异常的回应不会让用户退出登录）；「打开页面」按 `/n/昵称`、`/u/uid`、`/status/id`、`/results?q=` 打开页面（写操作测试时用来找测试微博）；会话续期。Dart 版为回答 PLAN §8 各个问题写的探针、真实点赞评论的「互动结构」探针没有迁。
- **测试**：迁移了 `settings_page_test`、`text_size_page_test`、`image_disk_cache_test`（改为对 Coil 磁盘缓存的调整上限）、`adaptive_layout_test`、`status_card_test` 的宽卡片各项（`StatusCardWideTest`，用 Robolectric 的 NATIVE 图形模式：旧模式量文字时每个字符算 1 像素；尺寸用不裁剪的位置和大小，与 Flutter 的 `getRect` 一致）、`status_detail_page_test` 的宽窗口一项，以及 `app_test` 里尚未迁移的：在首页删除自己的微博、从「我」进设置、关于、开源许可，胶囊在手机上居中、宽窗口在右下角且卡片加宽。没有翻译的：预测性返回的逐帧测试（Flutter 自己的转场实现，Compose 由 Navigation 3 负责）；运行中改变窗口大小（Robolectric 不能在测试中途换尺寸，宽和窄分开用 `@Config` 测）；「窄手机上字体大小处处生效」由 `KeepTextSizeTest` 和字体大小页的测试代替。另修了 `NotificationsPageTest` 里一个负载高时才出现的竞态（打开应用时的未读检查还没回来，测试自己的检查被跳过，或者那次检查回来时把已打开的「评论」页签算作已读）：`UnreadNoticesController.check()` 现在返回这次是否真的问了微博，测试一直等到自己的检查做完。共 339 个，全部通过。
