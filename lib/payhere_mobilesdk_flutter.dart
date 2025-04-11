
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
  PayHereOnDismissedHandler onDismissed,
) {
  const resultKeySuccess = 'success';
  const resultKeyData = 'fldata';
  const resultKeyCallback = 'flcallback';

  const resultCallbackTypeError = 'error';
  const resultCallbackTypeDismiss = 'dismiss';

  _channel.invokeMethod("startPayment", paymentObject).then((value) {
    if (value is List && value.isNotEmpty) {
      final result = value[0] as Map<dynamic, dynamic>;
      final resultSuccess = result[resultKeySuccess] as bool;

      if (resultSuccess) {
        final resultPaymentId = result[resultKeyData] as String;
        onCompleted(resultPaymentId);
      } else {
        final resultCallbackType = result[resultKeyCallback] as String;
        if (resultCallbackType == resultCallbackTypeError) {
          final error = result[resultKeyData] as String;
          onError(error);
        } else if (resultCallbackType == resultCallbackTypeDismiss) {
          onDismissed();
        } else {
          onError('Unknown callback');
        }
      }
    } else {
      onError("Invalid response from native SDK.");
    }
  }).catchError((e) {
    onError(e.toString());
  });
}

}
