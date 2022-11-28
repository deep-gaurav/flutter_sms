import 'dart:async';

import 'src/flutter_sms_platform.dart';

/// Open SMS Dialog on iOS/Android/Web
Future<String> sendSMS({
  required String message,
  required List<String> recipients,
  int? sendDirect,
  Duration? closeTime,
}) async {
  var isResponded = false;
  if (closeTime != null) {
    Timer(closeTime, () {
      if (isResponded == false) {
        closeSMS();
      }
    });
  }
  try {
    var response = await FlutterSmsPlatform.instance.sendSMS(
      message: message,
      recipients: recipients,
      sendDirect: sendDirect,
    );
    isResponded = true;
    return response;
  } catch (e) {
    isResponded = true;
    rethrow;
  }
}

Future<bool> closeSMS() {
  return FlutterSmsPlatform.instance.closeSMS();
}

/// Launch SMS Url Scheme on all platforms
Future<bool> launchSms({
  String? message,
  String? number,
}) =>
    FlutterSmsPlatform.instance.launchSms(number, message);

/// Launch SMS Url Scheme on all platforms
Future<bool> launchSmsMulti({
  required String message,
  required List<String> numbers,
}) =>
    FlutterSmsPlatform.instance.launchSmsMulti(numbers, message);

/// Check if you can send SMS on this platform
Future<bool> canSendSMS() => FlutterSmsPlatform.instance.canSendSMS();
