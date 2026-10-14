# 在 iPhone 上安装 maxbo

maxbo 不上架 App Store。Releases 页面提供的 `maxbo-unsigned.ipa` **没有签名**，不能直接安装，需要用你自己的 Apple ID 签名后装到手机上。下面几种方式任选其一。

> 安装前可以核对文件：`shasum -a 256 maxbo-unsigned.ipa`，结果应与同一 Release 里 `SHA256SUMS.txt` 中的对应行一致。

## 先了解这些

- **免费 Apple ID 签名的应用 7 天后过期**，过期后打不开，重新签名安装即可（数据和登录状态还在）。免费账号同时最多装 3 个自签应用。付费开发者账号（每年 99 美元）签名的有效期是 1 年。
- **第一次打开前要信任证书**：「设置 → 通用 → VPN 与设备管理」，在「开发者 App」下找到你的 Apple ID，点「信任」。
- **iOS 16 及以上要打开开发者模式**：「设置 → 隐私与安全性 → 开发者模式」，打开后按提示重启。
- **覆盖安装不会丢失登录状态**，前提是 Bundle ID 不变。有些签名工具会改 Bundle ID（例如在后面加一段后缀），改了以后系统会把它当成另一个应用，需要重新登录。
- 签名工具要用你的 Apple ID 登录苹果的服务器。建议用 Apple ID 的「App 专用密码」，或者专门注册一个用于签名的 Apple ID。**不要使用网上的「企业证书签名服务」**：这类证书随时会被苹果吊销，而且安装包可能被改过。

## 方式一：AltStore 或 SideStore（免费 Apple ID）

这两个工具会在手机上装一个管理器，之后可以在手机上直接安装 IPA，并自动续签。

1. 按官方说明安装：[AltStore](https://altstore.io/)（需要电脑上一直运行 AltServer 来续签）或 [SideStore](https://sidestore.io/)（首次配置后可以在手机上自己续签）。
2. 把 `maxbo-unsigned.ipa` 存到手机的「文件」App 里。
3. 在 AltStore/SideStore 的「My Apps」页点「+」，选择这个 IPA，等待签名安装完成。
4. 按上面「先了解这些」信任证书、打开开发者模式。

## 方式二：Sideloadly（电脑端，免费 Apple ID）

[Sideloadly](https://sideloadly.io/) 支持 Windows 和 macOS，用数据线把手机连到电脑：

1. 打开 Sideloadly，把 IPA 拖进窗口，填写 Apple ID，点「Start」。
2. 安装完成后信任证书、打开开发者模式。
3. 7 天后重复一次即可续签。

## 方式三：用 Xcode 从源码安装

适合有 Mac 的用户，免费 Apple ID 或付费开发者账号都可以。

1. 安装 Xcode 和 Flutter（stable 3.47.x），克隆本仓库，执行 `flutter pub get`。
2. 用 Xcode 打开 `ios/Runner.xcworkspace`，选中 Runner target，在「Signing & Capabilities」里选择你的 Team。如果提示 Bundle ID 已被占用，把 `io.github.leonfox28.maxbo` 改成你自己的（例如 `com.<你的名字>.maxbo`）。
3. 手机连上电脑，在命令行执行：

   ```sh
   flutter run --release -d <设备 id>
   ```

   或在 Xcode 里选择你的手机后点运行。

付费开发者账号也可以用自己的证书和描述文件重签 `maxbo-unsigned.ipa`（例如 `fastlane sigh resign`），这里不展开。

## 常见问题

- **打开就闪退**：多半是签名已过期，重新签名安装。
- **提示「无法验证 App」或「未受信任的开发者」**：去「VPN 与设备管理」信任证书，并确认开发者模式已打开。
- **重新安装后需要重新登录**：Bundle ID 变了，或者先删除了旧的 App。删除 App 会清除它的数据；maxbo 在全新安装后的首次启动时，也会主动清掉钥匙串里残留的旧登录状态。
