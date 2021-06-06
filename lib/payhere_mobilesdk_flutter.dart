
import 'dart:async';

import 'package:flutter/services.dart';

typedef PayHereOnCompletedHandler = void Function(String? paymentId);
typedef PayHereOnErrorHandler = void Function(String? error);
typedef PayHereOnDismissedHandler = void Function();

class PayHere {
  static const MethodChannel _channel =
      const MethodChannel('payhere_mobilesdk_flutter');

  static void startPayment(
    Map paymentObject, 
    PayHereOnCompletedHandler onCompleted, 
    PayHereOnErrorHandler onError,
    PayHereOnDismissedHandler onDismissed
  ){

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

      if (resultSuccess){
        String? resultPaymentId = result[_resultKeyData] as String?;
        onCompleted(resultPaymentId);
      }
      else{
        String? resultCallbackType = result[_resultKeyCallback] as String?;
        if (resultCallbackType == _resultCallbackTypeError){
          String? error = result[_resultKeyData] as String?;
          onError(error);
        }
        else if (resultCallbackType == _resultCallbackTypeDismiss){
          onDismissed();
        }
        else{
          onError('Unknown callback');
        }
      }
    });

  }
}
