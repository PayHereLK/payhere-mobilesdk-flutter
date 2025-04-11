import 'package:flutter_test/flutter_test.dart';
import 'package:payhere_mobilesdk_flutter/payhere_mobilesdk_flutter.dart';
import 'package:payhere_mobilesdk_flutter/payhere_mobilesdk_flutter_platform_interface.dart';
import 'package:payhere_mobilesdk_flutter/payhere_mobilesdk_flutter_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockPayhereMobilesdkFlutterPlatform
    with MockPlatformInterfaceMixin
    implements PayhereMobilesdkFlutterPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final PayhereMobilesdkFlutterPlatform initialPlatform = PayhereMobilesdkFlutterPlatform.instance;

  test('$MethodChannelPayhereMobilesdkFlutter is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelPayhereMobilesdkFlutter>());
  });

  test('getPlatformVersion', () async {
    PayhereMobilesdkFlutter payhereMobilesdkFlutterPlugin = PayhereMobilesdkFlutter();
    MockPayhereMobilesdkFlutterPlatform fakePlatform = MockPayhereMobilesdkFlutterPlatform();
    PayhereMobilesdkFlutterPlatform.instance = fakePlatform;

    expect(await payhereMobilesdkFlutterPlugin.getPlatformVersion(), '42');
  });
}
