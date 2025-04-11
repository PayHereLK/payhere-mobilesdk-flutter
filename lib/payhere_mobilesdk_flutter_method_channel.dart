import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'payhere_mobilesdk_flutter_platform_interface.dart';

/// An implementation of [PayhereMobilesdkFlutterPlatform] that uses method channels.
class MethodChannelPayhereMobilesdkFlutter extends PayhereMobilesdkFlutterPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('payhere_mobilesdk_flutter');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
