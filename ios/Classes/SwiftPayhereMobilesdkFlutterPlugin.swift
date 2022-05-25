import Flutter
import UIKit
import payHereSDK

public class SwiftPayhereMobilesdkFlutterPlugin: NSObject, FlutterPlugin {
  
  private var lastResult: FlutterResult!
  
  private enum PaymentObjectKey{
    static let sandbox = "sandbox"
    static let merchantId = "merchant_id"
    static let notifyUrl = "notify_url"
    static let orderId = "order_id"
    static let items = "items"
    static let amount = "amount"
    static let currency = "currency"
    static let firstName = "first_name"
    static let lastName = "last_name"
    static let email = "email"
    static let phone = "phone"
    static let address = "address"
    static let city = "city"
    static let country = "country"
    static let deliveryAddress = "delivery_address"
    static let deliveryCity = "delivery_city"
    static let deliveryCountry = "delivery_country"
    static let customOne = "custom_1"
    static let customTwo = "custom_2"
    static let recurrence = "recurrence"
    static let duration = "duration"
    static let startupFee = "startup_fee"
    static let preapprove = "preapprove"
    static let authorize = "authorize"
    static let prefixItemNumber = "item_number_"
    static let prefixItemName = "item_name_"
    static let prefixItemAmount = "amount_"
    static let prefixItemQuantity = "quantity_"
  }
  private enum ResultKey{
    static let success = "success"
    static let callbackType = "flcallback"
    static let data = "fldata"
  }
  private enum ResultCallbackType{
    static let complete = "complete"
    static let dismiss = "dismiss"
    static let error = "error"
  }
  private enum ItemProcessingError: Error, CustomStringConvertible{
    case cannotFindNumberAtEnd(_ key: String)
    case cannotParseToNumber(_ key: String, _ v: String)
    
    var description: String{
      switch(self){
      case .cannotFindNumberAtEnd(let key):
        return "Could not find a number at the end of key, '\(key)'. Expected for example, 'some_key_1'."
        
      case .cannotParseToNumber(let key, let value):
        return "Could not parse value '\(value)' at the end of key '\(key)' to a number. Expected for example, 'some_key_1'."
      }
    }
  }
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "payhere_mobilesdk_flutter", binaryMessenger: registrar.messenger())
    let instance = SwiftPayhereMobilesdkFlutterPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }
  
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    guard call.method == "startPayment" else{
      fatalError("Unknown method '\(call.method)' invoked from Plugin's Dart API")
    }
    
    guard let castParams = call.arguments as? NSDictionary else{
      fatalError("Unable to cast \(String(describing: call.arguments!)) to type of NSDictionary")
    }
    
    let paymentObject = parseToDictionary(castParams)
    
    self.lastResult = { [weak self] params in
      guard self != nil else { return }
      result(params)
    }
    
    var initRequest: PHInitialRequest
    var errorString: String? = nil
    var isSandbox: Bool = true
    
    guard let request = createGeneralRequest(paymentObject, &errorString) else{
      self.sendError(errorString ?? "Cannot create payment object")
      return
    }
    initRequest = request
    
    guard let sandbox = paymentObject[PaymentObjectKey.sandbox] as? Bool else{
      self.sendError("Cannot find parameter, 'sandbox' in payment object")
      return
    }
    isSandbox = sandbox
    
    DispatchQueue.main.async { [weak self] in
      guard let s = self else { return }
      
      let vc = UIApplication.shared.keyWindow!.rootViewController!
      PHPrecentController.precent(from: vc, withInitRequest: initRequest, delegate: s)
    }
    
  }
  
  private func log(_ msg: String){
    print("PayHere iOS: " + msg)
  }
  
  /**
   Sends to error handler in Flutter
   Flutter: onError
   */
  private func sendError(_ error: String? = nil){
    guard let callback = lastResult else {
      log("Lost reference to callback")
      return
    }
    
    let finalError = error ?? "Null Error"
    
    let resultDict = NSDictionary(dictionary: [
      ResultKey.success: false,
      ResultKey.callbackType: ResultCallbackType.error,
      ResultKey.data: finalError
    ])
    callback([resultDict])
  }
  
  /**
   Flutter: onDismissed
   */
  private func sendDismissed(){
    guard let callback = lastResult else {
      log("Lost reference to callback")
      return
    }
    
    let resultDict = NSDictionary(dictionary: [
      ResultKey.success: false,
      ResultKey.callbackType: ResultCallbackType.dismiss,
    ])
    callback([resultDict])
    
  }
  
  /**
   Flutter: onCompleted
   */
  private func sendCompleted(data: String){
    guard let callback = lastResult else {
      log("Lost reference to callback")
      return
    }
    let paymentNo = data
    let resultDict = NSDictionary(dictionary: [
      ResultKey.success: true,
      ResultKey.callbackType: ResultCallbackType.complete,
      ResultKey.data: paymentNo
    ])
    callback([resultDict])
  }
  
  /**
   Determines the request type and calls the appropriate method
   */
  private func createGeneralRequest(_ obj: [String: Any], _ errorString: inout String?) -> PHInitialRequest?{
    
    typealias k = PaymentObjectKey
    
    if let prepapproveStatus = obj[k.preapprove] as? Bool{
      if (prepapproveStatus){
        return self.createPreApprovalRequest(obj, &errorString)
      }
    }
    
    if let authorizeStatus = obj[k.authorize] as? Bool{
      if (authorizeStatus){
        return self.createAuthorizeRequest(obj, &errorString)
      }
    }
    
    if let recurrence = obj[k.recurrence] as? String, let duration = obj[k.duration] as? String{
      if (!recurrence.isEmpty && !duration.isEmpty){
        return self.createRecurringRequest(obj, &errorString)
      }
    }
    
    return self.createCheckoutRequest(obj, &errorString)
    
  }
  
  // MARK: Parse methods
  
  /**
   Dictionary must have its keys in string. Otherwise their corresponding values are not mapped.
   Nil values are also discarded WITH their keys.
   */
  private func parseToDictionary(_ dict: NSDictionary) -> [String: Any]{
    var retDict: [String: Any] = [:]
    var unknownKeyCount = 0
    
    let keys = dict.allKeys.map { (any) -> String in
      if let stringKey = any as? String{
        return stringKey
      }
      else{
        let unknownTypeKey = String(format: "unkt_key_%d", unknownKeyCount)
        unknownKeyCount += 1
        return unknownTypeKey
      }
    }
    
    for k in keys{
      if let value = dict[k]{
        retDict[k] = value
      }
      else{
        retDict[k] = "Unknown Value"
      }
    }
    
    return retDict
  }
  
  private func parseCurrency(_ currency: Any?) -> PHCurrency{
    guard let str = currency as? String else{ return .LKR }
    return PHCurrency(rawValue: str) ?? .LKR
  }
  
  private func parseAmount(_ amount: Any?) -> Double?{
    if let str = amount as? String{
      return Double(str)
    }
    else if let dbl = amount as? Double{
      return dbl
    }
    else{
      return nil
    }
  }
  
  private func parse(_ val: Any?) -> String?{
    guard let valStr = val as? String else{ return nil }
    return valStr
  }
  
  private func parseInteger(_ val: Any?) -> Int?{
    if let str = parse(val){
      return Int(str)
    }
    else if let number = val as? Int{
      return number
    }
    return nil
  }
  
  private func parseRecurrence(_ val: Any?, _ error: inout String?) -> PHRecurrenceTime?{
    guard let str = parse(val) else {
      error = "Could not parse recurrence"
      return nil
    }
    let components = str.components(separatedBy: " ")
    guard (components.count == 2) else{
      error = "Invalid recurrence"
      return nil
    }
    guard let number = Int(components[0]) else{
      error = "Recurrence number is invalid"
      return nil
    }
    switch(components[1].lowercased()){
    case "week":    return  .Week(period: number)
    case "month":   return  .Month(period: number)
    case "year":    return  .Year(period: number)
    default:
      error = "Recurrence word is invalid"
      return nil
    }
  }
  
  private func parseDuration(_ val: Any?, _ error: inout String?) -> PHDuration?{
    guard let str = parse(val) else {
      error = "Could not parse duration"
      return nil
    }
    let components = str.components(separatedBy: " ")
    guard (components.count == 2 || components.count == 1) else{
      error = "Invalid duration"
      return nil
    }
    
    if components[0].lowercased() == "forever"{
      return .Forver
    }
    
    guard let number = Int(components[0]) else{
      error = "Duration number is invalid"
      return nil
    }
    
    switch(components[1].lowercased()){
    case "week":    return  .Week(duration: number)
    case "month":   return  .Month(duration: number)
    case "year":    return  .Year(duration: number)
    default:
      error = "Duration word is invalid"
      return nil
    }
  }
  
  private func parseItems(_ o: [String: Any], _ err: inout String?) -> [payHereSDK.Item]{
    var itemMap: [Int: payHereSDK.Item] = [:]
    
    for (k, v) in o{
      do{
        if k.starts(with: PaymentObjectKey.prefixItemNumber){
          let index = try getIndex(k)
          let item = initOrGetItem(index: index, &itemMap)
          item.id = parse(v)
        }
        else if k.starts(with: PaymentObjectKey.prefixItemName){
          let index = try getIndex(k)
          let item = initOrGetItem(index: index, &itemMap)
          item.name = parse(v)
        }
        else if k.starts(with: PaymentObjectKey.prefixItemQuantity){
          let index = try getIndex(k)
          let item = initOrGetItem(index: index, &itemMap)
          item.quantity = parseInteger(v)
        }
        else if k.starts(with: PaymentObjectKey.prefixItemAmount){
          let index = try getIndex(k)
          let item = initOrGetItem(index: index, &itemMap)
          item.amount = parseAmount(v)
        }
      }
      catch let error as ItemProcessingError {
        err = error.description
        break;
      }
      catch{
        err = "Unknown error occurred while parsing items"
      }
    }
    
    return Array(itemMap.values)
  }
  
  private func getIndex(_ key: String) throws -> Int{
    let comps = key.components(separatedBy: "_")
    
    guard let valueAtEnd = comps.last else {
      throw ItemProcessingError.cannotFindNumberAtEnd(key)
    }
    
    guard let parsed = Int(valueAtEnd) else{
      throw ItemProcessingError.cannotParseToNumber(key, valueAtEnd)
    }
    
    return parsed
  }
  
  private func initOrGetItem(index: Int, _ map: inout [Int: payHereSDK.Item]) -> payHereSDK.Item{
    if let item = map[index]{
      return item
    }
    else{
      let newItem = payHereSDK.Item()
      map[index] = newItem
      return newItem
    }
  }
  
  // MARK: END
  
  private func createCheckoutRequest(_ o: [String: Any], _ errorString: inout String?) -> PHInitialRequest?{
    
    typealias k = PaymentObjectKey
    
    // let item = Item(id: nil, name: parse(o[k.items]), quantity: 1, amount: parseAmount(o[k.amount]))
    let itemsArr = parseItems(o, &errorString)
    guard errorString == nil else { return nil }
    
    let request = PHInitialRequest(
      merchantID:         parse(o[k.merchantId]),
      notifyURL:          parse(o[k.notifyUrl]),
      firstName:          parse(o[k.firstName]),
      lastName:           parse(o[k.lastName]),
      email:              parse(o[k.email]),
      phone:              parse(o[k.phone]),
      address:            parse(o[k.address]),
      city:               parse(o[k.city]),
      country:            parse(o[k.country]),
      orderID:            parse(o[k.orderId]),
      itemsDescription:   parse(o[k.items]),
      itemsMap:           itemsArr,
      currency:           parseCurrency(o[k.currency]),
      amount:             parseAmount(o[k.amount]),
      deliveryAddress:    parse(o[k.deliveryAddress]),
      deliveryCity:       parse(o[k.deliveryCity]),
      deliveryCountry:    parse(o[k.deliveryCountry]),
      custom1:            parse(o[k.customOne]),
      custom2:            parse(o[k.customTwo]))
    
    return request
  }
  
  private func createRecurringRequest(_ o: [String: Any], _ errorString: inout String?) -> PHInitialRequest?{
    
    typealias k = PaymentObjectKey
    guard let recurrence = parseRecurrence(o[k.recurrence], &errorString) else{
      return nil
    }
    
    guard let duration = parseDuration(o[k.duration], &errorString) else{
      return nil
    }
    
    // let item = Item(id: nil, name: parse(o[k.items]), quantity: 1, amount: parseAmount(o[k.amount]))
    let itemsArr = parseItems(o, &errorString)
    guard errorString == nil else { return nil }
    
    let request = PHInitialRequest(
      merchantID:         parse(o[k.merchantId]),
      notifyURL:          parse(o[k.notifyUrl]),
      firstName:          parse(o[k.firstName]),
      lastName:           parse(o[k.lastName]),
      email:              parse(o[k.email]),
      phone:              parse(o[k.phone]),
      address:            parse(o[k.address]),
      city:               parse(o[k.city]),
      country:            parse(o[k.country]),
      orderID:            parse(o[k.orderId]),
      itemsDescription:   parse(o[k.items]),
      itemsMap:           itemsArr,
      currency:           parseCurrency(o[k.currency]),
      amount:             parseAmount(o[k.amount]),
      deliveryAddress:    parse(o[k.deliveryAddress]),
      deliveryCity:       parse(o[k.deliveryCity]),
      deliveryCountry:    parse(o[k.deliveryCountry]),
      custom1:            parse(o[k.customOne]),
      custom2:            parse(o[k.customTwo]),
      startupFee:         parseAmount(o[k.startupFee]),
      recurrence:         recurrence,
      duration:           duration
    )
    
    return request
  }
  
  private func createPreApprovalRequest(_ o: [String: Any], _ errorString: inout String?) -> PHInitialRequest?{
    
    typealias k = PaymentObjectKey
    
    // let item = Item(id: nil, name: parse(o[k.items]), quantity: 1, amount: nil)
    let itemsArr = parseItems(o, &errorString)
    guard errorString == nil else { return nil }
    
    let request = PHInitialRequest(
      merchantID:         parse(o[k.merchantId]),
      notifyURL:          parse(o[k.notifyUrl]),
      firstName:          parse(o[k.firstName]),
      lastName:           parse(o[k.lastName]),
      email:              parse(o[k.email]),
      phone:              parse(o[k.phone]),
      address:            parse(o[k.address]),
      city:               parse(o[k.city]),
      country:            parse(o[k.country]),
      orderID:            parse(o[k.orderId]),
      itemsDescription:   parse(o[k.items]),
      itemsMap:           itemsArr,
      currency:           parseCurrency(o[k.currency]),
      custom1:            parse(o[k.customOne]),
      custom2:            parse(o[k.customTwo])
    )
    
    return request
  }
  
  private func createAuthorizeRequest(_ o: [String: Any], _ errorString: inout String?) -> PHInitialRequest?{
    
    typealias k = PaymentObjectKey
    
    // let item = Item(id: nil, name: parse(o[k.items]), quantity: 1, amount: nil)
    let itemsArr = parseItems(o, &errorString)
    guard errorString == nil else { return nil }
    
    let request = PHInitialRequest(
      merchantID:         parse(o[k.merchantId]),
      notifyURL:          parse(o[k.notifyUrl]),
      firstName:          parse(o[k.firstName]),
      lastName:           parse(o[k.lastName]),
      email:              parse(o[k.email]),
      phone:              parse(o[k.phone]),
      address:            parse(o[k.address]),
      city:               parse(o[k.city]),
      country:            parse(o[k.country]),
      orderID:            parse(o[k.orderId]),
      itemsDescription:   parse(o[k.items]),
      itemsMap:           itemsArr,
      currency:           parseCurrency(o[k.currency]),
      amount:             parseAmount(o[k.amount]),
      deliveryAddress:    parse(o[k.deliveryAddress]),
      deliveryCity:       parse(o[k.deliveryCity]),
      deliveryCountry:    parse(o[k.deliveryCountry]),
      custom1:            parse(o[k.customOne]),
      custom2:            parse(o[k.customTwo]),
      isHoldOnCardEnabled: true
    )
    
    return request
  }
}

extension SwiftPayhereMobilesdkFlutterPlugin : PHViewControllerDelegate{
  public func onErrorReceived(error: Error) {
    let nsError = error as NSError
    
    if (nsError.domain == ""){
      let description = (nsError.userInfo[NSLocalizedDescriptionKey] as? String) ?? ""
      if (nsError.code == 401){
        switch(description){
        case "Oparation Canceld":
          sendDismissed()
        case "Invalid", "Unable to connect to the internet":
          sendError(description)
        default:
          sendError("Validation Error: " + description)
        }
      }
      else if (nsError.code == 501){
        if (description.isEmpty){
          sendError("Server Response Error")
        }
        else{
          sendError("Server Response Error: " + description)
        }
      }
      else{
        if (description.isEmpty){
          sendError("Server Error")
        }
        else{
          sendError("Server Error: " + description)
        }
      }
    }
  }
  
  public func onResponseReceived(response: PHResponse<Any>?) {
    
    if let statusResponse = response?.getData() as? StatusResponse{
      if let status = statusResponse.status,
         status == StatusResponse.Status.SUCCESS.rawValue ||
          status == StatusResponse.Status.AUTHORIZED.rawValue{
        
        var paymentNo = "0"
        if ((statusResponse.paymentNo ?? 0.0)?.truncatingRemainder(dividingBy: 1.0) == 0){
          paymentNo = String(format: "%.0f", statusResponse.paymentNo ?? 0.0)
        }
        
        sendCompleted(data: paymentNo)
      }
      else{
        // Payment Failed
        handleAsError(response)
      }
    }
    else{
      if (response?.isSuccess() ?? false){
        sendError("Internal Error: Could not map success response")
      }
      else{
        // Payment Failed
        handleAsError(response)
      }
    }
  }
  
  private func handleAsError(_ response: PHResponse<Any>?){
    if let msg = response?.getMessage(){
      sendError("Payment Error: \"" + msg + "\"")
    }
    else{
      sendError("Unknown Payment Error")
    }
  }
}
