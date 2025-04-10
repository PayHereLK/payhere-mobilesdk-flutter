import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'payhere_mobilesdk_flutter_method_channel.dart';

abstract class PayhereMobilesdkFlutterPlatform extends PlatformInterface {
  /// Constructs a PayhereMobilesdkFlutterPlatform.
  PayhereMobilesdkFlutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static PayhereMobilesdkFlutterPlatform _instance = MethodChannelPayhereMobilesdkFlutter();

  /// The default instance of [PayhereMobilesdkFlutterPlatform] to use.
  ///
  /// Defaults to [MethodChannelPayhereMobilesdkFlutter].
  static PayhereMobilesdkFlutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [PayhereMobilesdkFlutterPlatform] when
  /// they register themselves.
  static set instance(PayhereMobilesdkFlutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
