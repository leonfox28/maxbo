import Flutter
import UIKit
import WebKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    if let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "CookieBridge") {
      CookieBridge.register(with: registrar.messenger())
    }
  }
}

/// Reads cookies from the shared WKWebView cookie store, including HttpOnly ones
/// (such as Weibo's SUB) that page scripts and webview_flutter cannot read.
enum CookieBridge {
  static func register(with messenger: FlutterBinaryMessenger) {
    let channel = FlutterMethodChannel(name: "io.github.leonfox28.maxbo/cookies", binaryMessenger: messenger)
    channel.setMethodCallHandler { call, result in
      guard call.method == "getCookies" else {
        result(FlutterMethodNotImplemented)
        return
      }
      guard let args = call.arguments as? [String: Any],
            let url = args["url"] as? String,
            let host = URL(string: url)?.host else {
        result(FlutterError(code: "bad_args", message: "url is required", details: nil))
        return
      }
      WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
        let matching = cookies.filter { cookie in
          let domain = cookie.domain.hasPrefix(".") ? String(cookie.domain.dropFirst()) : cookie.domain
          return host == domain || host.hasSuffix("." + domain)
        }
        result(matching.map { ["name": $0.name, "value": $0.value] })
      }
    }
  }
}
