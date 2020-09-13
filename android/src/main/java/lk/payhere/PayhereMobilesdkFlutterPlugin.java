package lk.payhere;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

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

public class PayhereMobilesdkFlutterPlugin implements FlutterPlugin, MethodCallHandler, PluginRegistry.ActivityResultListener, ActivityAware {

  private static final int PAYHERE_REQUEST = 11010;
  private Result lastResult;
  private MethodChannel channel;
  // private Context attachedContext;
  private Activity attachedActivity;

  /* MARK: Definitions */

  private static final class PaymentObjectKey{
    public final static String sandbox = "sandbox";
    public final static String merchantId = "merchant_id";
    public final static String merchantSecret = "merchant_secret";
    public final static String notifyUrl = "notify_url";
    public final static String orderId = "order_id";
    public final static String items = "items";
    public final static String amount = "amount";
    public final static String currency = "currency";
    public final static String firstName = "first_name";
    public final static String lastName = "last_name";
    public final static String email = "email";
    public final static String phone = "phone";
    public final static String address = "address";
    public final static String city = "city";
    public final static String country = "country";
    public final static String deliveryAddress = "delivery_address";
    public final static String deliveryCity = "delivery_city";
    public final static String deliveryCountry = "delivery_country";
    public final static String customOne = "custom_1";
    public final static String customTwo = "custom_2";
    public final static String recurrence = "recurrence";
    public final static String duration = "duration";
    public final static String startupFee = "startup_fee";
    public final static String preapprove = "preapprove";

    public PaymentObjectKey(){}
  }

  private static final class ResultKey{
    public final static String success = "success";
    public final static String callbackType = "flcallback";
    public final static String data = "fldata";

    private ResultKey(){}
  }

  private static final class ResultCallbackType{
    public final static String complete = "complete";
    public final static String dismiss = "dismiss";
    public final static String error = "error";

    private ResultCallbackType(){}
  }

  private static final class PayHereKeyExtractionException extends Exception{

    private String parameter;
    private String type;
    private Boolean keyExisted;

    /**
     * Exception for key that exists, but failed to cast to String
     * @param key Key of value extracted (e.g. 'merchant_id')
     */
    public PayHereKeyExtractionException(String key){
      this.parameter = key;
      this.type = "String";
      this.keyExisted = true;
    }

    /**
     * Exception for key that may or may not exist, but failed to cast to String
     * @param key Key of value extracted (e.g. 'merchant_id')
     * @param keyExists Whether the key existed at point of extraction
     */
    public PayHereKeyExtractionException(String key, Boolean keyExists){
      this.parameter = key;
      this.type = "String";
      this.keyExisted = keyExists;
    }

    /**
     * Exception for key that exists, but failed to cast to type
     * @param key Key of value extracted (e.g. 'merchant_id')
     * @param type The type of the parameter expected but didn't exist (e.g. 'Boolean')
     */
    public PayHereKeyExtractionException(String key, String type){
      this.parameter = key;
      this.type = type;
      this.keyExisted = true;
    }
    /**
     * Exception for key that may or may not, but failed to cast to type
     * @param key Key of value extracted (e.g. 'merchant_id')
     * @param type The type of the parameter expected but didn't exist (e.g. 'Boolean')
     * @param keyExists Whether the key existed at point of extraction
     */
    public PayHereKeyExtractionException(String key, String type, Boolean keyExists){
      this.parameter = key;
      this.type = type;
      this.keyExisted = keyExists;
    }

    @Override
    public String toString() {
      return "PayHereKeyExtractionException{" +
              "parameter='" + parameter + '\'' +
              ", type='" + type + '\'' +
              ", keyExisted=" + keyExisted +
              '}';
    }
  }

  /* END MARK: Definitions */

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "payhere_mobilesdk_flutter");
    channel.setMethodCallHandler(this);
    // attachedContext = flutterPluginBinding.getApplicationContext();
  }

  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "payhere_mobilesdk_flutter");
    final PayhereMobilesdkFlutterPlugin plugin = new PayhereMobilesdkFlutterPlugin();
    channel.setMethodCallHandler(plugin);

    // This is a fallback method.
    // On newer Flutter versions, this method will not be called.
    // In-order to ensure we listen to the activity result,
    // we also listen to it in, 'onAttachedToActivity'
    registrar.addActivityResultListener(plugin);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {

    // Guard against malformed method signatures

    if (!call.method.equals("startPayment")) {
      result.notImplemented();
      return;
    }

    // Actual Implementation

    String errorString = null;
    HashMap<String, Object> paymentObject = this.parseDictionary(call.arguments());

    this.lastResult = result;

    if (paymentObject.containsKey(PaymentObjectKey.preapprove) && (boolean) paymentObject.get(PaymentObjectKey.preapprove))
      errorString = this.createAndLaunchPreapprovalRequest(paymentObject, attachedActivity);
    else{

      // Check if request is a Subscription Payment

      boolean recurrenceCheck = paymentObject.containsKey(PaymentObjectKey.recurrence);
      recurrenceCheck = recurrenceCheck && (paymentObject.get(PaymentObjectKey.recurrence) != null);

      boolean durationCheck = paymentObject.containsKey(PaymentObjectKey.duration);
      durationCheck = durationCheck && (paymentObject.get(PaymentObjectKey.duration) != null);

      // If parameters for Subscription Payments are available,
      // start Subscription. Otherwise, One Time.

      if (recurrenceCheck && durationCheck)
        errorString = this.createAndLaunchRecurringRequest(paymentObject, attachedActivity);
      else
        errorString = this.createAndLaunchOnetimeRequest(paymentObject, attachedActivity);
    }

    if (errorString != null){
      // Error occurred. Request is not launched.
      // Invoke callback with error details.

      this.sendError(errorString);
    }

  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
    boolean handled = false;
    if (requestCode == PAYHERE_REQUEST && data != null && data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {
      PHResponse<StatusResponse> response = (PHResponse<StatusResponse>) data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);

      if (resultCode == Activity.RESULT_OK) {
        String msg;
        if (response != null) {
          handled = true;
          if (response.isSuccess()) {
            msg = "Activity result:" + response.getData().toString();
            String paymentNo = Long.toString(response.getData().getPaymentNo());
            this.sendCompleted(paymentNo);
          } else {
            msg = "Result:" + response.toString();
            this.sendError(response.getData().getMessage());
          }
        }
        else
          msg = "Result: no response";

        this.log(msg);
      }
      else if (resultCode == Activity.RESULT_CANCELED) {
        handled = true;
        if (response != null) {
          if (response.toString().contains("User canceled the request"))
            this.sendDismissed();
          else
            this.sendError(response.toString());
        }
        else
          this.sendDismissed();
      }
    }
    return handled;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    attachedActivity = binding.getActivity();
    binding.addActivityResultListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    // No implementation
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    attachedActivity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivity() {
    attachedActivity = null;
  }

  private void log(String msg){
    Log.d("PayHere", msg);
  }
  /**
   * Send an error message back to Flutter interface
   * Flutter: onError
   * @param error Error msg to pass (accepts null)
   */
  private void sendError(String error){

    if (lastResult == null){
      throw new RuntimeException("callback must not be null");
    }

    String finalError = error == null ? "Null error" : error;

    ArrayList<Object> wrapper = new ArrayList<Object>();
    HashMap<String, Object> map = new HashMap<String, Object>();

    map.put(ResultKey.success, false);
    map.put(ResultKey.callbackType, ResultCallbackType.error);
    map.put(ResultKey.data, finalError);

    wrapper.add(map);
    lastResult.success(wrapper);
  }

  /**
   * Send a dismissed message back to Flutter interface
   * Flutter: onDismissed
   */
  private void sendDismissed(){

    if (lastResult == null){
      throw new RuntimeException("callback must not be null");
    }

    ArrayList<Object> wrapper = new ArrayList<Object>();
    HashMap<String, Object> map = new HashMap<String, Object>();

    map.put(ResultKey.success, false);
    map.put(ResultKey.callbackType, ResultCallbackType.dismiss);

    wrapper.add(map);
    lastResult.success(wrapper);
  }

  /**
   * Send a dismissed message back to Flutter interface
   * Flutter: onCompleted
   * @param data A string to send back (usually PayHere paymentID)
   */
  private void sendCompleted(String data){

    if (lastResult == null){
      throw new RuntimeException("callback must not be null");
    }

    ArrayList<Object> wrapper = new ArrayList<Object>();
    HashMap<String, Object> map = new HashMap<String, Object>();

    map.put(ResultKey.success, true);
    map.put(ResultKey.callbackType, ResultCallbackType.complete);
    map.put(ResultKey.data, data);

    wrapper.add(map);
    lastResult.success(wrapper);
  }

  private HashMap<String, Object> parseDictionary(@NonNull Object params){
    HashMap<Object, Object> castParams = (HashMap<Object, Object>) params;
    HashMap<String, Object> paymentObject = new HashMap<String, Object>();
    for (Map.Entry<Object, Object> entry : castParams.entrySet()){
      if (entry.getKey() instanceof String){
        paymentObject.put((String)entry.getKey(), entry.getValue());
      }
    }

    return paymentObject;
  }

  /**
   * Extracts a String Key from a HashMap.
   * @param map Map to extract keys from
   * @param key Key of value to extract
   * @return String value of extracted key. Null is never returned: exception thrown instead.
   * @throws PayHereKeyExtractionException Key extraction error information
   */
  private String extract(HashMap<String, Object> map, String key) throws PayHereKeyExtractionException{
    if (map.containsKey(key)){
      Object raw = map.get(key);
      if (raw == null){
        throw new PayHereKeyExtractionException(key, "Object", true);
      }
      else{
        try {
          return raw.toString();
        }
        catch(Exception e){
          throw new PayHereKeyExtractionException(key, true);
        }
      }
    }
    else{
      throw new PayHereKeyExtractionException(key, false);
    }
  }

  /**
   * Extracts a String Key from a HashMap.
   * If the key doesnt exist, returns null.
   * @param map Map to extract keys from
   * @param key Key of value to extract
   * @return String value of extracted key. Null is returned if key or value doesn't exist.
   */
  private String extractOptional(HashMap<String, Object> map, String key){
    if (map.containsKey(key)){
      Object raw = map.get(key);
      if (raw == null){
        return null;
      }
      else{
        try {
          return raw.toString();
        }
        catch(Exception e){
          return null;
        }
      }
    }
    else{
      return null;
    }
  }

  /**
   * Extracts an PayHere Amount from HashMap.
   * @param map Map to extract keys from
   * @param key Key of value to extract
   * @return Double value of extracted key. Null is never returned: exception thrown instead.
   * @throws PayHereKeyExtractionException Key extraction error information
   */
  private Double extractAmount(HashMap<String, Object> map, String key) throws PayHereKeyExtractionException{
    if (map.containsKey(key)){
      Object raw = map.get(key);
      if (raw == null){
        throw new PayHereKeyExtractionException(key, "Object", true);
      }
      else{
        try {
          String str = raw.toString();
          return Double.valueOf(str);
        }
        catch(Exception e){
          throw new PayHereKeyExtractionException(key, "Double", true);
        }
      }
    }
    else{
      throw new PayHereKeyExtractionException(key, false);
    }
  }

  /**
   * Extracts an PayHere Amount from HashMap.
   * If the key doesnt exist, returns null.
   * @param map Map to extract keys from
   * @param key Key of value to extract
   * @return Double value of extracted key. Null is returned if key or value doesn't exist.
   */
  private Double extractOptionalAmount(HashMap<String, Object> map, String key){
    if (map.containsKey(key)){
      Object raw = map.get(key);
      if (raw == null){
        return null;
      }
      else{
        try {
          String str = raw.toString();
          return Double.valueOf(str);
        }
        catch(Exception e){
          return null;
        }
      }
    }
    else{
      return null;
    }
  }

  /**
   * Extracts a bool from HashMap.
   * @param map Map to extract keys from
   * @param key Key of value to extract
   * @return Boolean value of extracted key. Null is never returned: exception thrown instead.
   * @throws PayHereKeyExtractionException Key extraction error information
   */
  private Boolean extractBoolean(HashMap<String, Object> map, String key) throws PayHereKeyExtractionException{
    if (map.containsKey(key)){
      Object raw = map.get(key);
      if (raw == null){
        throw new PayHereKeyExtractionException(key, true);
      }
      else{
        try {
          String str = raw.toString();
          return Boolean.valueOf(str);
        }
        catch(Exception e){
          throw new PayHereKeyExtractionException(key, true);
        }
      }
    }
    else{
      throw new PayHereKeyExtractionException(key, false);
    }
  }

  private String createAndLaunchOnetimeRequest(HashMap<String, Object> o, Activity attachedActivity){
    String error = null;

    try {

      Item item = new Item(
              null,
              this.extract(o, PaymentObjectKey.items),
              1,
              this.extractAmount(o, PaymentObjectKey.amount)
      );

      InitRequest req = new InitRequest();

      req.setMerchantId(          this.extract(o,         PaymentObjectKey.merchantId));
      req.setMerchantSecret(      this.extract(o,         PaymentObjectKey.merchantSecret));
      req.setNotifyUrl(           this.extract(o,         PaymentObjectKey.notifyUrl));
      req.setCurrency(            this.extract(o,         PaymentObjectKey.currency));
      req.setAmount(              this.extractAmount(o,   PaymentObjectKey.amount));
      req.setOrderId(             this.extract(o,         PaymentObjectKey.orderId));
      req.setItemsDescription(    this.extract(o,         PaymentObjectKey.items));

      String custom1 =            this.extractOptional(o, PaymentObjectKey.customOne);
      String custom2 =            this.extractOptional(o, PaymentObjectKey.customTwo);

      if (custom1 != null) {
        req.setCustom1(custom1);
      }

      if (custom2 != null) {
        req.setCustom2(custom2);
      }

      Customer customer = req.getCustomer();
      customer.setFirstName(      this.extract(o,         PaymentObjectKey.firstName));
      customer.setLastName(       this.extract(o,         PaymentObjectKey.lastName));
      customer.setEmail(          this.extract(o,         PaymentObjectKey.email));
      customer.setPhone(          this.extract(o,         PaymentObjectKey.phone));

      Address customerAddress = customer.getAddress();
      customerAddress.setAddress( this.extract(o,         PaymentObjectKey.address));
      customerAddress.setCity(    this.extract(o,         PaymentObjectKey.city));
      customerAddress.setCountry( this.extract(o,         PaymentObjectKey.country));

      Address customerDeliveryAddress = customer.getDeliveryAddress();
      String deliveryAddress =    this.extractOptional(o, PaymentObjectKey.deliveryAddress);
      String deliveryCity =       this.extractOptional(o, PaymentObjectKey.deliveryCity);
      String deliveryCountry =    this.extractOptional(o, PaymentObjectKey.deliveryCountry);

      if (deliveryAddress != null)
        customerDeliveryAddress.setAddress(deliveryAddress);

      if (deliveryCity != null)
        customerDeliveryAddress.setCity(deliveryCity);

      if (deliveryCountry != null)
        customerDeliveryAddress.setCountry(deliveryCountry);

      req.getItems().add(item);

      Boolean isSandbox = this.extractBoolean(o,          PaymentObjectKey.sandbox);
      this.launchRequest(req, attachedActivity, isSandbox);

    }
    catch(PayHereKeyExtractionException exc){
      error = exc.toString();
    }

    return error;
  }

  /**
   * @warning: UNTESTED, development happened during PayHere System Upgrade.
   */
  private String createAndLaunchRecurringRequest(HashMap<String, Object> o, Activity attachedActivity){
    String error = null;

    try {

      InitRequest req = new InitRequest();

      req.setMerchantId(          this.extract(o,         PaymentObjectKey.merchantId));
      req.setMerchantSecret(      this.extract(o,         PaymentObjectKey.merchantSecret));
      req.setNotifyUrl(           this.extract(o,         PaymentObjectKey.notifyUrl));
      req.setCurrency(            this.extract(o,         PaymentObjectKey.currency));
      req.setAmount(              this.extractAmount(o,   PaymentObjectKey.amount));
      req.setRecurrence(          this.extract(o,         PaymentObjectKey.recurrence));
      req.setDuration(            this.extract(o,         PaymentObjectKey.duration));
      req.setOrderId(             this.extract(o,         PaymentObjectKey.orderId));
      req.setItemsDescription(    this.extract(o,         PaymentObjectKey.items));

      Double startupFee =         this.extractOptionalAmount(o, PaymentObjectKey.startupFee);
      if (startupFee != null){
        req.setStartupFee(startupFee);
      }

      String custom1 =            this.extractOptional(o, PaymentObjectKey.customOne);
      String custom2 =            this.extractOptional(o, PaymentObjectKey.customTwo);

      if (custom1 != null) {
        req.setCustom1(custom1);
      }

      if (custom2 != null) {
        req.setCustom2(custom2);
      }

      Customer customer = req.getCustomer();
      customer.setFirstName(      this.extract(o,         PaymentObjectKey.firstName));
      customer.setLastName(       this.extract(o,         PaymentObjectKey.lastName));
      customer.setEmail(          this.extract(o,         PaymentObjectKey.email));
      customer.setPhone(          this.extract(o,         PaymentObjectKey.phone));

      Address customerAddress = customer.getAddress();
      customerAddress.setAddress( this.extract(o,         PaymentObjectKey.address));
      customerAddress.setCity(    this.extract(o,         PaymentObjectKey.city));
      customerAddress.setCountry( this.extract(o,         PaymentObjectKey.country));

      Address customerDeliveryAddress = customer.getDeliveryAddress();
      String deliveryAddress =    this.extractOptional(o, PaymentObjectKey.deliveryAddress);
      String deliveryCity =       this.extractOptional(o, PaymentObjectKey.deliveryCity);
      String deliveryCountry =    this.extractOptional(o, PaymentObjectKey.deliveryCountry);

      if (deliveryAddress != null)
        customerDeliveryAddress.setAddress(deliveryAddress);

      if (deliveryCity != null)
        customerDeliveryAddress.setCity(deliveryCity);

      if (deliveryCountry != null)
        customerDeliveryAddress.setCountry(deliveryCountry);

      Boolean isSandbox = this.extractBoolean(o,          PaymentObjectKey.sandbox);
      this.launchRequest(req, attachedActivity, isSandbox);

    }
    catch(PayHereKeyExtractionException exc){
      error = exc.toString();
    }

    return error;
  }

  /**
   * @warning: UNTESTED, development happened during PayHere System Upgrade.
   */
  private String createAndLaunchPreapprovalRequest(HashMap<String, Object> o, Activity attachedActivity){
    String error = null;

    try {

      InitPreapprovalRequest req = new InitPreapprovalRequest();

      req.setMerchantId(          this.extract(o,         PaymentObjectKey.merchantId));
      req.setMerchantSecret(      this.extract(o,         PaymentObjectKey.merchantSecret));
      req.setNotifyUrl(           this.extract(o,         PaymentObjectKey.notifyUrl));
      req.setCurrency(            this.extract(o,         PaymentObjectKey.currency));
      req.setOrderId(             this.extract(o,         PaymentObjectKey.orderId));
      req.setItemsDescription(    this.extract(o,         PaymentObjectKey.items));

      String custom1 =            this.extractOptional(o, PaymentObjectKey.customOne);
      String custom2 =            this.extractOptional(o, PaymentObjectKey.customTwo);

      if (custom1 != null) {
        req.setCustom1(custom1);
      }

      if (custom2 != null) {
        req.setCustom2(custom2);
      }

      Customer customer = req.getCustomer();
      customer.setFirstName(      this.extract(o,         PaymentObjectKey.firstName));
      customer.setLastName(       this.extract(o,         PaymentObjectKey.lastName));
      customer.setEmail(          this.extract(o,         PaymentObjectKey.email));
      customer.setPhone(          this.extract(o,         PaymentObjectKey.phone));

      Address customerAddress = customer.getAddress();
      customerAddress.setAddress( this.extract(o,         PaymentObjectKey.address));
      customerAddress.setCity(    this.extract(o,         PaymentObjectKey.city));
      customerAddress.setCountry( this.extract(o,         PaymentObjectKey.country));

      Address customerDeliveryAddress = customer.getDeliveryAddress();
      String deliveryAddress =    this.extractOptional(o, PaymentObjectKey.deliveryAddress);
      String deliveryCity =       this.extractOptional(o, PaymentObjectKey.deliveryCity);
      String deliveryCountry =    this.extractOptional(o, PaymentObjectKey.deliveryCountry);

      if (deliveryAddress != null)
        customerDeliveryAddress.setAddress(deliveryAddress);

      if (deliveryCity != null)
        customerDeliveryAddress.setCity(deliveryCity);

      if (deliveryCountry != null)
        customerDeliveryAddress.setCountry(deliveryCountry);

      Boolean isSandbox = this.extractBoolean(o,          PaymentObjectKey.sandbox);
      this.launchRequest(req, attachedActivity, isSandbox);

    }
    catch(PayHereKeyExtractionException exc){
      error = exc.toString();
    }

    return error;
  }

  private void launchRequest(InitRequest req, Activity activity, boolean isSandbox){
    Context context = activity.getApplicationContext();
    Intent intent = new Intent(context, PHMainActivity.class);
    intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);

    if (isSandbox)
      PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);
    else
      PHConfigs.setBaseUrl(PHConfigs.LIVE_URL);

    activity.startActivityForResult(intent, PAYHERE_REQUEST, Bundle.EMPTY);
  }

  private void launchRequest(InitPreapprovalRequest req, Activity activity, boolean isSandbox){
    Context context = activity.getApplicationContext();
    Intent intent = new Intent(context, PHMainActivity.class);
    intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);

    if (isSandbox)
      PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);
    else
      PHConfigs.setBaseUrl(PHConfigs.LIVE_URL);

    activity.startActivityForResult(intent, PAYHERE_REQUEST, Bundle.EMPTY);
    // context.startActivity(intent, Bundle.EMPTY);
    // context.startActivityForResult(intent, PAYHERE_REQUEST, Bundle.EMPTY);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
}
