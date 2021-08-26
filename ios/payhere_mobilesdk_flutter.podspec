#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint payhere_mobilesdk_flutter.podspec' to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'payhere_mobilesdk_flutter'
  s.version          = '1.0.4'
  s.summary          = 'Make PayHere payments with your Flutter App!'
  s.description      = <<-DESC
Make PayHere payments with your Flutter App!
                       DESC
  s.homepage         = 'http://payhere.lk'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'PayHere' => 'support@payhere.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '11.0'

  # Flutter.framework does not contain a i386 slice. Only x86_64 simulators are supported.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'VALID_ARCHS[sdk=iphonesimulator*]' => 'x86_64' }
  s.swift_version = '5.0'

  s.dependency "payHereSDK", '= 2.1.2'
end
