import 'package:flutter/foundation.dart';

/// Logs [message] in debug builds only; release builds stay silent.
///
/// Never pass cookie values or post content: the log is readable over adb.
void devLog(String message) {
  if (kDebugMode) debugPrint('[maxbo] $message');
}
