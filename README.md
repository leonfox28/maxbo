# maxbo

一个开源、非商业的第三方微博阅读客户端（Android / iOS），使用 Flutter 开发。

> **状态：早期测试版（v0.1.0，仅 Android）。** 已有登录、关注时间线、微博详情（长文、评论、楼中楼、转发）、图片和视频、用户主页、热搜、点赞和评论。设置页、应用内的关于页和 iOS 安装包还没有做。方案与进度见 [docs/PLAN.md](docs/PLAN.md)。

## 安装（Android）

在 [Releases](https://github.com/leonfox28/maxbo/releases) 页面下载 APK：

- `app-arm64-v8a-release.apk`：绝大多数手机选这个；
- `app-armeabi-v7a-release.apk`：较老的 32 位手机；
- `app-x86_64-release.apk`：模拟器。

鸿蒙 HarmonyOS 5/6 可以通过卓易通安装 APK。

## 从源码构建

需要 Flutter stable 3.47.x。

```sh
flutter build apk --release --split-per-abi
```

没有 `android/key.properties` 时，release 包用调试密钥签名，无法覆盖安装 Releases 页面上的正式包。

## 声明

- 本项目为**非官方**客户端，与新浪微博及北京微梦创科网络技术有限公司没有任何关联，仅供学习交流。
- **不收费、不接受赞助、不提供去广告功能**，也不提供批量下载、会员内容等功能。
- 应用为纯客户端：登录在微博官方网页登录页中完成，应用接触不到你的密码；登录状态只保存在你自己的设备上（Android Keystore / iOS Keychain）。项目没有任何服务器，也不收集任何数据。
- 使用非官方客户端可能导致账号被风控或受到限制，**风险由使用者自行承担**。请遵守《微博服务使用协议》。
- 如权利人认为本项目不妥，请提 issue 联系，我们会及时处理。

## License

[GPL-3.0](LICENSE)
