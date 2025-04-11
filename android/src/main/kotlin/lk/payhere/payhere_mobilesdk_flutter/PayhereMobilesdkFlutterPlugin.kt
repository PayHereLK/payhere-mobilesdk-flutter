package lk.payhere.payhere_mobilesdk_flutter

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry


import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.PHResponse;
import lk.payhere.androidsdk.model.Address;
import lk.payhere.androidsdk.model.Customer;
import lk.payhere.androidsdk.model.InitPreapprovalRequest;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.Item;
import lk.payhere.androidsdk.model.StatusResponse;

/** PayhereMobilesdkFlutterPlugin */
class PayhereMobilesdkFlutterPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel

  companion object {
        @JvmStatic
        private val PAYHERE_REQUEST = 11010
  }

  private var lastResult: Result? = null
  private var attachedActivity: Activity? = null


  class PaymentObjectKey private constructor() {
    companion object {
        const val SANDBOX = "sandbox"
        const val MERCHANT_ID = "merchant_id"
        const val NOTIFY_URL = "notify_url"
        const val ORDER_ID = "order_id"
        const val ITEMS = "items"
        const val AMOUNT = "amount"
        const val CURRENCY = "currency"
        const val FIRST_NAME = "first_name"
        const val LAST_NAME = "last_name"
        const val EMAIL = "email"
        const val PHONE = "phone"
        const val ADDRESS = "address"
        const val CITY = "city"
        const val COUNTRY = "country"
        const val DELIVERY_ADDRESS = "delivery_address"
        const val DELIVERY_CITY = "delivery_city"
        const val DELIVERY_COUNTRY = "delivery_country"
        const val CUSTOM_ONE = "custom_1"
        const val CUSTOM_TWO = "custom_2"
        const val RECURRENCE = "recurrence"
        const val DURATION = "duration"
        const val STARTUP_FEE = "startup_fee"
        const val PREAPPROVE = "preapprove"
        const val AUTHORIZE = "authorize"
        const val PREFIX_ITEM_NUMBER = "item_number_"
        const val PREFIX_ITEM_NAME = "item_name_"
        const val PREFIX_ITEM_AMOUNT = "amount_"
        const val PREFIX_ITEM_QUANTITY = "quantity_"
    }
}


  class ResultKey private constructor() {
      companion object {
          const val SUCCESS = "success"
          const val CALLBACK_TYPE = "flcallback"
          const val DATA = "fldata"
      }
  }

  class ResultCallbackType private constructor() {
    companion object {
        const val COMPLETE = "complete"
        const val DISMISS = "dismiss"
        const val ERROR = "error"
    }
  }

  class PayHereItemProcessingException : Exception {
    public var reason: String

    /**
     * The key's size was not as expected
     * @param key
     * @param size measured size of the key
     */
    constructor(key: String, size: Int) {
        reason = "Empty key encountered. Key string: '$key' Size: $size"
    }

    constructor() {
        reason = "Unknown Error Occurred while extracting Item data"
    }

    /**
     * A key was present, but there was no number at the end of it.
     * @param key
     */
    constructor(key: String) {
        reason = "Could not find a number at the end of key, '$key'. Expected for example, 'some_key_1'."
    }

    /**
     * A key was present, but the character at the end could not be parsed to a number.
     * @param key
     * @param value handles null or scenarios.
     */
    constructor(key: String, value: Any?) {
        val valueContents = value?.toString() ?: "null"
        reason = "Could not parse value '$valueContents' at the end of key '$key' to a number. Expected for example, 'some_key_1'."
    }

    fun setCustomReason(reason: String): PayHereItemProcessingException {
        this.reason = reason
        return this
    }
  }



  class PayHereKeyExtractionException : Exception {
    private var parameter: String
    private var type: String
    private var keyExisted: Boolean

    /**
     * Exception for key that exists, but failed to cast to String
     * @param key Key of value extracted (e.g. 'merchant_id')
     */
    constructor(key: String) {
        this.parameter = key
        this.type = "String"
        this.keyExisted = true
    }

    /**
     * Exception for key that may or may not exist, but failed to cast to String
     * @param key Key of value extracted (e.g. 'merchant_id')
     * @param keyExists Whether the key existed at the point of extraction
     */
    constructor(key: String, keyExists: Boolean) {
        this.parameter = key
        this.type = "String"
        this.keyExisted = keyExists
    }

    /**
     * Exception for key that exists, but failed to cast to type
     * @param key Key of value extracted (e.g. 'merchant_id')
     * @param type The type of the parameter expected but didn't exist (e.g. 'Boolean')
     */
    constructor(key: String, type: String) {
        this.parameter = key
        this.type = type
        this.keyExisted = true
    }

    /**
     * Exception for key that may or may not exist, but failed to cast to type
     * @param key Key of value extracted (e.g. 'merchant_id')
     * @param type The type of the parameter expected but didn't exist (e.g. 'Boolean')
     * @param keyExists Whether the key existed at the point of extraction
     */
    constructor(key: String, type: String, keyExists: Boolean) {
        this.parameter = key
        this.type = type
        this.keyExisted = keyExists
    }

    override fun toString(): String {
        return "PayHereKeyExtractionException(parameter='$parameter', type='$type', keyExisted=$keyExisted)"
    }
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "payhere_mobilesdk_flutter")
    channel.setMethodCallHandler(this)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    attachedActivity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {

    // Guard against malformed method signatures
    if (call.method != "startPayment") {
        result.notImplemented()
        return
    }

    // Actual Implementation
    var errorString: String? = null
    val paymentObject = parseDictionary(call.arguments()!!)

    lastResult = result

    when {
        paymentObject[PaymentObjectKey.PREAPPROVE] as? Boolean == true -> {
          val errorString = createAndLaunchPreapprovalRequest(paymentObject, attachedActivity!!)

        }
        paymentObject[PaymentObjectKey.AUTHORIZE] as? Boolean == true -> {
          val errorString = createAndLaunchAuthorizationRequest(paymentObject, attachedActivity!!)
        }
        else -> {
            // Check if request is a Subscription Payment
            val recurrenceCheck = paymentObject.containsKey(PaymentObjectKey.RECURRENCE) && 
                                  paymentObject[PaymentObjectKey.RECURRENCE] != null
            val durationCheck = paymentObject.containsKey(PaymentObjectKey.DURATION) && 
                                paymentObject[PaymentObjectKey.DURATION] != null

            // If parameters for Subscription Payments are available, start Subscription. Otherwise, One Time.
            errorString = if (recurrenceCheck && durationCheck) {
                createAndLaunchRecurringRequest(paymentObject, attachedActivity!!)
            } else {
                createAndLaunchOnetimeRequest(paymentObject, attachedActivity!!)
            }
        }
    }

    // Error handling
    errorString?.let {
        sendError(it)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    var handled = false
    if (requestCode == PAYHERE_REQUEST && data != null && data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
        val response = data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT) as? PHResponse<StatusResponse>

        if (resultCode == Activity.RESULT_OK) {
            var msg: String
            if (response != null) {
                handled = true
                msg = "Response: ${response}"

                if (response.data == null) {
                    if (response.isSuccess) {
                        sendError("Internal Error. Could not map success response.")
                    } else {
                        handleAsError(response)
                    }
                } else {
                    val status = response.data
                    if (status?.status == StatusResponse.Status.SUCCESS.value() || status?.status == StatusResponse.Status.HOLD.value()) {
                        msg = "Activity result: ${response.data}"
                        val paymentNo = response.data?.paymentNo?.toString()
                        sendCompleted(paymentNo!!)
                    } else {
                        handleAsError(response)
                    }
                }
            } else {
                msg = "Result: no response"
            }

            log(msg)
        } else if (resultCode == Activity.RESULT_CANCELED) {
            handled = true
            if (response != null) {
                when (response.status) {
                    PHResponse.STATUS_ERROR_CANCELED -> sendDismissed()
                    PHResponse.STATUS_ERROR_NETWORK -> sendError("Network Error")
                    PHResponse.STATUS_ERROR_VALIDATION -> sendError("Parameter Validation Error")
                    PHResponse.STATUS_ERROR_DATA -> sendError("Intent Data not Present")
                    PHResponse.STATUS_ERROR_PAYMENT, PHResponse.STATUS_ERROR_UNKNOWN -> handleAsError(response)
                    else -> sendDismissed() // Default case for unhandled response
                }
            } else {
                sendDismissed()
            }
        }
    }
    return handled
  }


  private fun handleAsError(response: PHResponse<StatusResponse>?) {
    if (response == null) {
        sendError("Unknown Error Occurred, response was null")
        return
    }

    val resStr = response.toString()
    var errMsg: String? = null
    try {
        val pattern = Regex("message='(.+?)',")
        val matchResult = pattern.find(resStr)
        if (matchResult != null) {
            errMsg = matchResult.groupValues[1]
        }
    } catch (e: Exception) {
        sendError("Unknown error occurred while extracting error message")
        return
    }

    if (errMsg != null) {
        sendError(errMsg)
        return
    }

    if (response.data == null) {
        sendError("Unknown Error Occurred. PayHere Response was null.")
    } else {
        val message = response.data?.message
        if (message == null) {
            sendError("Unknown Error Occurred.")
        } else {
            sendError(message)
        }
    }
  }


  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    attachedActivity = binding.activity
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // No implementation
  }

  override fun onDetachedFromActivity() {
    attachedActivity = null
  }




  private fun log(msg: String) {
    Log.d("PayHere", msg)
  }

  /**
 * Send an error message back to Flutter interface
 * Flutter: onError
 * @param error Error msg to pass (accepts null)
 */
  private fun sendError(error: String?) {
      if (lastResult == null) {
          throw RuntimeException("callback must not be null")
      }

      val finalError = error ?: "Null error"

      val wrapper = arrayListOf<Any>()
      val map = hashMapOf<String, Any>()

      map[ResultKey.SUCCESS] = false
      map[ResultKey.CALLBACK_TYPE] = ResultCallbackType.ERROR
      map[ResultKey.DATA] = finalError

      wrapper.add(map)
      lastResult?.success(wrapper)
  }


    /**
  * Send a dismissed message back to Flutter interface
  * Flutter: onDismissed
  */
  private fun sendDismissed() {
      lastResult?.let {
          val wrapper = arrayListOf<Any>()
          val map = hashMapOf<String, Any>()

          map[ResultKey.SUCCESS] = false
          map[ResultKey.CALLBACK_TYPE] = ResultCallbackType.DISMISS

          wrapper.add(map)
          it.success(wrapper)
      } ?: throw RuntimeException("callback must not be null")
  }

  /**
  * Send a completed message back to Flutter interface
  * Flutter: onCompleted
  * @param data A string to send back (usually PayHere paymentID)
  */
  private fun sendCompleted(data: String) {
      lastResult?.let {
          val wrapper = arrayListOf<Any>()
          val map = hashMapOf<String, Any>()

          map[ResultKey.SUCCESS] = true
          map[ResultKey.CALLBACK_TYPE] = ResultCallbackType.COMPLETE
          map[ResultKey.DATA] = data

          wrapper.add(map)
          it.success(wrapper)
      } ?: throw RuntimeException("callback must not be null")
  }

  private fun parseDictionary(params: Any): HashMap<String, Any> {
      val castParams = params as HashMap<*, *>
      val paymentObject = hashMapOf<String, Any>()
      
      for (entry in castParams.entries) {
          if (entry.key is String) {
              paymentObject[entry.key as String] = entry.value!!
          }
      }

      return paymentObject
  }


    /**
  * Extracts a String Key from a HashMap.
  * @param map Map to extract keys from
  * @param key Key of value to extract
  * @return String value of extracted key. Null is never returned: exception thrown instead.
  * @throws PayHereKeyExtractionException Key extraction error information
  */
  @Throws(PayHereKeyExtractionException::class)
  private fun extract(map: HashMap<String, Any>, key: String): String {
      if (map.containsKey(key)) {
          val raw = map[key]
          if (raw == null) {
              throw PayHereKeyExtractionException(key, "Object", true)
          } else {
              return try {
                  raw.toString()
              } catch (e: Exception) {
                  throw PayHereKeyExtractionException(key, true)
              }
          }
      } else {
          throw PayHereKeyExtractionException(key, false)
      }
  }

  /**
  * Extracts an int Key from a HashMap.
  * @param map Map to extract keys from
  * @param key Key of value to extract
  * @return int value of extracted key. If an error occurred, an exception will be thrown.
  * @throws PayHereKeyExtractionException Key extraction error information
  */
  @Throws(PayHereKeyExtractionException::class)
  private fun extractInteger(map: HashMap<String, Any>, key: String): Int {
      if (map.containsKey(key)) {
          val raw = map[key]
          if (raw == null) {
              throw PayHereKeyExtractionException(key, "Object", true)
          } else {
              return try {
                  val tmpStr = this.extract(map, key)
                  tmpStr.toInt()
              } catch (e: Exception) {
                  throw PayHereKeyExtractionException(key, true)
              }
          }
      } else {
          throw PayHereKeyExtractionException(key, false)
      }
  }

  /**
  * Extracts a String Key from a HashMap.
  * If the key doesn't exist, returns null.
  * @param map Map to extract keys from
  * @param key Key of value to extract
  * @return String value of extracted key. Null is returned if key or value doesn't exist.
  */
  private fun extractOptional(map: HashMap<String, Any>, key: String): String? {
      return if (map.containsKey(key)) {
          val raw = map[key]
          if (raw == null) {
              null
          } else {
              try {
                  raw.toString()
              } catch (e: Exception) {
                  null
              }
          }
      } else {
          null
      }
  }

  /**
  * Extracts a PayHere Amount from HashMap.
  * @param map Map to extract keys from
  * @param key Key of value to extract
  * @return Double value of extracted key. Null is never returned: exception thrown instead.
  * @throws PayHereKeyExtractionException Key extraction error information
  */
  @Throws(PayHereKeyExtractionException::class)
  private fun extractAmount(map: HashMap<String, Any>, key: String): Double {
      if (map.containsKey(key)) {
          val raw = map[key]
          if (raw == null) {
              throw PayHereKeyExtractionException(key, "Object", true)
          } else {
              return try {
                  val str = raw.toString()
                  str.toDouble()
              } catch (e: Exception) {
                  throw PayHereKeyExtractionException(key, "Double", true)
              }
          }
      } else {
          throw PayHereKeyExtractionException(key, false)
      }
  }

  /**
  * Extracts a PayHere Amount from HashMap.
  * If the key doesn't exist, returns null.
  * @param map Map to extract keys from
  * @param key Key of value to extract
  * @return Double value of extracted key. Null is returned if key or value doesn't exist.
  */
  private fun extractOptionalAmount(map: HashMap<String, Any>, key: String): Double? {
      return if (map.containsKey(key)) {
          val raw = map[key]
          if (raw == null) {
              null
          } else {
              try {
                  val str = raw.toString()
                  str.toDouble()
              } catch (e: Exception) {
                  null
              }
          }
      } else {
          null
      }
  }


    /**
  * Extracts a bool from HashMap.
  * @param map Map to extract keys from
  * @param key Key of value to extract
  * @return Boolean value of extracted key. Null is never returned: exception thrown instead.
  * @throws PayHereKeyExtractionException Key extraction error information
  */
  @Throws(PayHereKeyExtractionException::class)
  private fun extractBoolean(map: HashMap<String, Any>, key: String): Boolean {
      return if (map.containsKey(key)) {
          val raw = map[key]
          if (raw == null) {
              throw PayHereKeyExtractionException(key, true)
          } else {
              try {
                  val str = raw.toString()
                  return str.toBoolean()
              } catch (e: Exception) {
                  throw PayHereKeyExtractionException(key, true)
              }
          }
      } else {
          throw PayHereKeyExtractionException(key, false)
      }
  }

  @Throws(PayHereItemProcessingException::class, PayHereKeyExtractionException::class)
  private fun extractItems(map: HashMap<String, Any>): ArrayList<Item> {
      val itemMap = HashMap<Int, Item>()

      for ((key, value) in map) {
          try {
              if (key.isNullOrEmpty()) continue

              when {
                  key.startsWith(PaymentObjectKey.PREFIX_ITEM_NUMBER) -> {
                      val index = getIndex(key)
                      val item = initOrGetItem(index, itemMap)
                      item.id = extract(map, key)
                  }
                  key.startsWith(PaymentObjectKey.PREFIX_ITEM_NAME) -> {
                      val index = getIndex(key)
                      val item = initOrGetItem(index, itemMap)
                      item.name = extract(map, key)
                  }
                  key.startsWith(PaymentObjectKey.PREFIX_ITEM_QUANTITY) -> {
                      val index = getIndex(key)
                      val item = initOrGetItem(index, itemMap)
                      item.quantity = extractInteger(map, key)
                  }
                  key.startsWith(PaymentObjectKey.PREFIX_ITEM_AMOUNT) -> {
                      val index = getIndex(key)
                      val item = initOrGetItem(index, itemMap)
                      item.amount = extractAmount(map, key)
                  }
              }
          } catch (exc: PayHereKeyExtractionException) {
              throw exc
          } catch (exc: PayHereItemProcessingException) {
              throw exc
          }
      }
      return ArrayList(itemMap.values)
  }

  @Throws(PayHereItemProcessingException::class)
  private fun getIndex(key: String?): Int {
      if (key == null) return -1

      // Keys will have at least one '_' due to logic in this.extractItems.
      // So: components.length >= 1
      val components = key.split("_")
      val last = components[components.size - 1]

      return try {
          last.toInt()
      } catch (exc: NumberFormatException) {
          throw PayHereItemProcessingException(key, last)
      }
  }

  /**
  * Looks for the item with index in the map. If found returns it.
  * Otherwise, creates one AND PUTS IT IN THE MAP then returns it.
  */
  private fun initOrGetItem(index: Int, map: HashMap<Int, Item>): Item {
      var item = map[index]
      if (item == null) {
          item = Item()
          map[index] = item
      }
      return item
  }

  private fun createAndLaunchOnetimeRequest(o: HashMap<String, Any>, attachedActivity: Activity): String? {
      var error: String? = null
      try {
          val items = extractItems(o)

          val req = InitRequest()

          req.merchantId = extract(o, PaymentObjectKey.MERCHANT_ID)
          req.notifyUrl = extract(o, PaymentObjectKey.NOTIFY_URL)
          req.currency = extract(o, PaymentObjectKey.CURRENCY)
          req.amount = extractAmount(o, PaymentObjectKey.AMOUNT)
          req.orderId = extract(o, PaymentObjectKey.ORDER_ID)
          req.itemsDescription = extract(o, PaymentObjectKey.ITEMS)

          val custom1 = extractOptional(o, PaymentObjectKey.CUSTOM_ONE)
          val custom2 = extractOptional(o, PaymentObjectKey.CUSTOM_TWO)

          custom1?.let { req.custom1 = it }
          custom2?.let { req.custom2 = it }

          val customer = req.customer
          customer.firstName = extract(o, PaymentObjectKey.FIRST_NAME)
          customer.lastName = extract(o, PaymentObjectKey.LAST_NAME)
          customer.email = extract(o, PaymentObjectKey.EMAIL)
          customer.phone = extract(o, PaymentObjectKey.PHONE)

          val customerAddress = customer.address
          customerAddress.address = extract(o, PaymentObjectKey.ADDRESS)
          customerAddress.city = extract(o, PaymentObjectKey.CITY)
          customerAddress.country = extract(o, PaymentObjectKey.COUNTRY)

          val customerDeliveryAddress = customer.deliveryAddress
          val deliveryAddress = extractOptional(o, PaymentObjectKey.DELIVERY_ADDRESS)
          val deliveryCity = extractOptional(o, PaymentObjectKey.DELIVERY_CITY)
          val deliveryCountry = extractOptional(o, PaymentObjectKey.DELIVERY_COUNTRY)

          deliveryAddress?.let { customerDeliveryAddress.address = it }
          deliveryCity?.let { customerDeliveryAddress.city = it }
          deliveryCountry?.let { customerDeliveryAddress.country = it }

          req.items.addAll(items)

          val isSandbox = extractBoolean(o, PaymentObjectKey.SANDBOX)
          launchRequest(req, attachedActivity, isSandbox)

      } catch (exc: PayHereKeyExtractionException) {
          error = exc.toString()
      } catch (exc: PayHereItemProcessingException) {
          error = exc.reason
      }
      return error
  }


  private fun createAndLaunchRecurringRequest(o: HashMap<String, Any>, attachedActivity: Activity): String? {
    var error: String? = null

    try {
        val items = this.extractItems(o)
        val req = InitRequest()

        req.merchantId = this.extract(o, PaymentObjectKey.MERCHANT_ID)
        req.notifyUrl = this.extract(o, PaymentObjectKey.NOTIFY_URL)
        req.currency = this.extract(o, PaymentObjectKey.CURRENCY)
        req.amount = this.extractAmount(o, PaymentObjectKey.AMOUNT)
        req.recurrence = this.extract(o, PaymentObjectKey.RECURRENCE)
        req.duration = this.extract(o, PaymentObjectKey.DURATION)
        req.orderId = this.extract(o, PaymentObjectKey.ORDER_ID)
        req.itemsDescription = this.extract(o, PaymentObjectKey.ITEMS)

        val startupFee = this.extractOptionalAmount(o, PaymentObjectKey.STARTUP_FEE)
        if (startupFee != null) {
            req.startupFee = startupFee
        }

        val custom1 = this.extractOptional(o, PaymentObjectKey.CUSTOM_ONE)
        val custom2 = this.extractOptional(o, PaymentObjectKey.CUSTOM_TWO)

        custom1?.let { req.custom1 = it }
        custom2?.let { req.custom2 = it }

        val customer = req.customer
        customer.firstName = this.extract(o, PaymentObjectKey.FIRST_NAME)
        customer.lastName = this.extract(o, PaymentObjectKey.LAST_NAME)
        customer.email = this.extract(o, PaymentObjectKey.EMAIL)
        customer.phone = this.extract(o, PaymentObjectKey.PHONE)

        val customerAddress = customer.address
        customerAddress.address = this.extract(o, PaymentObjectKey.ADDRESS)
        customerAddress.city = this.extract(o, PaymentObjectKey.CITY)
        customerAddress.country = this.extract(o, PaymentObjectKey.COUNTRY)

        val customerDeliveryAddress = customer.deliveryAddress
        val deliveryAddress = this.extractOptional(o, PaymentObjectKey.DELIVERY_ADDRESS)
        val deliveryCity = this.extractOptional(o, PaymentObjectKey.DELIVERY_CITY)
        val deliveryCountry = this.extractOptional(o, PaymentObjectKey.DELIVERY_COUNTRY)

        deliveryAddress?.let { customerDeliveryAddress.address = it }
        deliveryCity?.let { customerDeliveryAddress.city = it }
        deliveryCountry?.let { customerDeliveryAddress.country = it }

        req.items.addAll(items)

        val isSandbox = this.extractBoolean(o, PaymentObjectKey.SANDBOX)
        this.launchRequest(req, attachedActivity, isSandbox)

    } catch (exc: PayHereKeyExtractionException) {
        error = exc.toString()
    } catch (exc: PayHereItemProcessingException) {
        error = exc.reason
    }

    return error
}

private fun createAndLaunchPreapprovalRequest(o: HashMap<String, Any>, attachedActivity: Activity): String? {
    var error: String? = null

    try {
        val items = this.extractItems(o)
        val req = InitPreapprovalRequest()

        req.merchantId = this.extract(o, PaymentObjectKey.MERCHANT_ID)
        req.notifyUrl = this.extract(o, PaymentObjectKey.NOTIFY_URL)
        req.currency = this.extract(o, PaymentObjectKey.CURRENCY)
        req.orderId = this.extract(o, PaymentObjectKey.ORDER_ID)
        req.itemsDescription = this.extract(o, PaymentObjectKey.ITEMS)

        val custom1 = this.extractOptional(o, PaymentObjectKey.CUSTOM_ONE)
        val custom2 = this.extractOptional(o, PaymentObjectKey.CUSTOM_TWO)

        custom1?.let { req.custom1 = it }
        custom2?.let { req.custom2 = it }

        try {
            req.amount = this.extractAmount(o, PaymentObjectKey.AMOUNT)
        } catch (e: Exception) {
            // ignore missing amount param for pre-approval request
        }

        val customer = req.customer
        customer.firstName = this.extract(o, PaymentObjectKey.FIRST_NAME)
        customer.lastName = this.extract(o, PaymentObjectKey.LAST_NAME)
        customer.email = this.extract(o, PaymentObjectKey.EMAIL)
        customer.phone = this.extract(o, PaymentObjectKey.PHONE)

        val customerAddress = customer.address
        customerAddress.address = this.extract(o, PaymentObjectKey.ADDRESS)
        customerAddress.city = this.extract(o, PaymentObjectKey.CITY)
        customerAddress.country = this.extract(o, PaymentObjectKey.COUNTRY)

        val customerDeliveryAddress = customer.deliveryAddress
        val deliveryAddress = this.extractOptional(o, PaymentObjectKey.DELIVERY_ADDRESS)
        val deliveryCity = this.extractOptional(o, PaymentObjectKey.DELIVERY_CITY)
        val deliveryCountry = this.extractOptional(o, PaymentObjectKey.DELIVERY_COUNTRY)

        deliveryAddress?.let { customerDeliveryAddress.address = it }
        deliveryCity?.let { customerDeliveryAddress.city = it }
        deliveryCountry?.let { customerDeliveryAddress.country = it }

        req.items.addAll(items)

        val isSandbox = this.extractBoolean(o, PaymentObjectKey.SANDBOX)
        this.launchRequest(req, attachedActivity, isSandbox)

    } catch (exc: PayHereKeyExtractionException) {
        error = exc.toString()
    } catch (exc: PayHereItemProcessingException) {
        error = exc.reason
    }

    return error
  }



  private fun createAndLaunchAuthorizationRequest(o: HashMap<String, Any>, attachedActivity: Activity): String? {
    var error: String? = null

    try {
        val items = this.extractItems(o)
        val req = InitRequest()

        req.merchantId = this.extract(o, PaymentObjectKey.MERCHANT_ID)
        req.notifyUrl = this.extract(o, PaymentObjectKey.NOTIFY_URL)
        req.currency = this.extract(o, PaymentObjectKey.CURRENCY)
        req.amount = this.extractAmount(o, PaymentObjectKey.AMOUNT)
        req.orderId = this.extract(o, PaymentObjectKey.ORDER_ID)
        req.itemsDescription = this.extract(o, PaymentObjectKey.ITEMS)

        val custom1 = this.extractOptional(o, PaymentObjectKey.CUSTOM_ONE)
        val custom2 = this.extractOptional(o, PaymentObjectKey.CUSTOM_TWO)

        custom1?.let { req.custom1 = it }
        custom2?.let { req.custom2 = it }

        val customer = req.customer
        customer.firstName = this.extract(o, PaymentObjectKey.FIRST_NAME)
        customer.lastName = this.extract(o, PaymentObjectKey.LAST_NAME)
        customer.email = this.extract(o, PaymentObjectKey.EMAIL)
        customer.phone = this.extract(o, PaymentObjectKey.PHONE)

        val customerAddress = customer.address
        customerAddress.address = this.extract(o, PaymentObjectKey.ADDRESS)
        customerAddress.city = this.extract(o, PaymentObjectKey.CITY)
        customerAddress.country = this.extract(o, PaymentObjectKey.COUNTRY)

        val customerDeliveryAddress = customer.deliveryAddress
        val deliveryAddress = this.extractOptional(o, PaymentObjectKey.DELIVERY_ADDRESS)
        val deliveryCity = this.extractOptional(o, PaymentObjectKey.DELIVERY_CITY)
        val deliveryCountry = this.extractOptional(o, PaymentObjectKey.DELIVERY_COUNTRY)

        deliveryAddress?.let { customerDeliveryAddress.address = it }
        deliveryCity?.let { customerDeliveryAddress.city = it }
        deliveryCountry?.let { customerDeliveryAddress.country = it }

        req.items.addAll(items)
        req.setHoldOnCardEnabled(true)

        val isSandbox = this.extractBoolean(o, PaymentObjectKey.SANDBOX)
        this.launchRequest(req, attachedActivity, isSandbox)

    } catch (exc: PayHereKeyExtractionException) {
        error = exc.toString()
    } catch (exc: PayHereItemProcessingException) {
        error = exc.reason
    }

    return error
}

  private fun launchRequest(req: InitRequest, activity: Activity, isSandbox: Boolean) {
      val context = activity.applicationContext
      val intent = Intent(context, PHMainActivity::class.java)
      intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req)

      if (isSandbox) {
          PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL)
      } else {
          PHConfigs.setBaseUrl(PHConfigs.LIVE_URL)
      }

      activity.startActivityForResult(intent, PAYHERE_REQUEST, Bundle.EMPTY)
  }

  private fun launchRequest(req: InitPreapprovalRequest, activity: Activity, isSandbox: Boolean) {
      val context = activity.applicationContext
      val intent = Intent(context, PHMainActivity::class.java)
      intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req)

      if (isSandbox) {
          PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL)
      } else {
          PHConfigs.setBaseUrl(PHConfigs.LIVE_URL)
      }

      activity.startActivityForResult(intent, PAYHERE_REQUEST, Bundle.EMPTY)
  }


}
