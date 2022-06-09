import 'package:flutter/services.dart';

/// Handler for completed payments
typedef PayHereOnCompletedHandler = void Function(String paymentId);

/// Handler for payment and/or SDK errors
typedef PayHereOnErrorHandler = void Function(String error);

/// Handler for SDK dismissals
typedef PayHereOnDismissedHandler = void Function();

/// PayHere Flutter SDK
class PayHere {
  /// Main Bridge between Flutter and Native code.
  static const MethodChannel _channel =
      const MethodChannel('payhere_mobilesdk_flutter');

  /// Starts the PayHere Payment.
  ///
  /// Pass the payment details through the [paymentObject].
  /// Payment completion is notified to the [onCompleted] handler.
  /// Errors are notified to the [onError] handler.
  /// When the user dismisses the bottom sheet it is notified to the [onDismissed] handler.
  static void startPayment(
      Map paymentObject,
      PayHereOnCompletedHandler onCompleted,
      PayHereOnErrorHandler onError,
      PayHereOnDismissedHandler onDismissed) {
    const _resultKeySuccess = 'success';
    const _resultKeyData = 'fldata';
    const _resultKeyCallback = 'flcallback';

    const _resultCallbackTypeError = 'error';
    const _resultCallbackTypeDismiss = 'dismiss';

    _channel.invokeMethod("startPayment", paymentObject).then((value) {
      // print(value);

      // On return, value is a List<dynamic>

      dynamic resultDictionary = value[0];
      Map<dynamic, dynamic> result = resultDictionary as Map<dynamic, dynamic>;
      bool resultSuccess = result[_resultKeySuccess] as bool;

      if (resultSuccess) {
        String resultPaymentId = result[_resultKeyData] as String;
        onCompleted(resultPaymentId);
      } else {
        String resultCallbackType = result[_resultKeyCallback] as String;
        if (resultCallbackType == _resultCallbackTypeError) {
          String error = result[_resultKeyData] as String;
          onError(error);
        } else if (resultCallbackType == _resultCallbackTypeDismiss) {
          onDismissed();
        } else {
          onError('Unknown callback');
        }
      }
    });
  }
}
