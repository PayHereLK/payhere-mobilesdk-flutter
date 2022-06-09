#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint payhere_mobilesdk_flutter.podspec' to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'payhere_mobilesdk_flutter'
  s.version          = '3.0.1'
  s.summary          = 'Make PayHere payments with your Flutter App!'
  s.description      = <<-DESC
Make Onetime, Subscription and Pre-approval payments with your Flutter Mobile App through PayHere! 
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

  s.dependency "payHereSDK", '= 3.0.4'
end
