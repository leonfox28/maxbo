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
- **保护真实登录态**：真实登录需要短信验证码和人机验证。保存着登录态的设备只能用 `install -r` 更新，不要在上面跑 `integration_test`（会清空 WebView Cookie），也不要卸载或清除数据。
- **先问用户**：完成人机验证、勾选用户协议、以用户身份对外操作（关注、点赞、评论），都必须先得到用户确认。

## 架构速览

- `lib/core/net/`：网络层
  - `createWeiboDio()`：浏览器 UA、Cookie 和 XSRF 拦截器、限流
  - `WeiboCookieJar`：按站点（weibo.com / weibo.cn）保存 Cookie
  - `weibo_exception.dart`：集中处理微博的各种失败响应
- `lib/core/session/`：会话
  - 登录 Cookie 经原生平台通道读取（`SUB` 是 HttpOnly，Android 端在 `CookieBridge.kt`，iOS 端在 `ios/Runner/AppDelegate.swift`）
  - `SessionController`（`sessionProvider`）
  - 会话存在 Keystore / Keychain
- `lib/data/api/weibo_api.dart`：所有端点都集中在这里。新增端点时写成 `WeiboApi` 的方法，内部调用 `getJson()` / `getPage()`。当前用户从 weibo.com 首页内嵌的 `window.$CONFIG` 解析得到（`/ajax/config` 不存在）。
- `lib/features/`：各个页面。登录页是 WebView 加载微博官方登录页，每次主页面加载完成或失败都校验一次会话。
- 诊断页：只在调试版提供，入口是「我」页右上角的虫子图标，用来对真实会话核对接口。

## 约定

- **语言**：代码注释用英文，界面文案用中文。
- **数据**：ID 一律用字符串；JSON 手写容错解析，不用代码生成。
- **依赖注入**：Riverpod 3（不用代码生成）。测试通过覆盖 `weiboDioFactoryProvider`、`sessionStorageProvider`、`cookieBridgeProvider` 注入假实现，假实现见 `test/support/fakes.dart`。
- **提交**：用英文写提交信息，先一句话概括，再空一行写原因。每完成一个里程碑或一个独立修复就提交一次。
