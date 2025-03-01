package com.example.flutter_sms

import android.annotation.TargetApi
import android.app.Activity
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar


class FlutterSmsPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var mChannel: MethodChannel
    public var activity: Activity? = null
    private val REQUEST_CODE_SEND_SMS = 205

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        setupCallbackChannels(flutterPluginBinding.binaryMessenger)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        teardown()
    }

    private fun setupCallbackChannels(messenger: BinaryMessenger) {
        mChannel = MethodChannel(messenger, "flutter_sms")
        mChannel.setMethodCallHandler(this)
    }

    private fun teardown() {
        mChannel.setMethodCallHandler(null)
    }

    // V1 embedding entry point. This is deprecated and will be removed in a future Flutter
    // release but we leave it here in case someone's app does not utilize the V2 embedding yet.
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val inst = FlutterSmsPlugin()
            inst.activity = registrar.activity()
            inst.setupCallbackChannels(registrar.messenger())
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "sendSMS" -> {
                // if (!canSendSMS()) {
                //     result.error(
                //             "device_not_capable",
                //             "The current device is not capable of sending text messages.",
                //             "A device may be unable to send messages if it does not support messaging or if it is not currently configured to send messages. This only applies to the ability to send text messages via iMessage, SMS, and MMS.")
                //     return
                // }
                val message = call.argument<String?>("message") ?: ""
                val recipients = call.argument<String?>("recipients") ?: ""
                val sendDirect = call.argument<Int?>("sendDirect")
                sendSMS(result, recipients, message!!, sendDirect)
            }
            "canSendSMS" -> result.success(canSendSMS())
            else -> result.notImplemented()
        }
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private fun canSendSMS(): Boolean {
        if (!activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            return false
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("smsto:")
        val activityInfo = intent.resolveActivityInfo(activity!!.packageManager, intent.flags.toInt())
        return !(activityInfo == null || !activityInfo.exported)
    }

    private fun sendSMS(result: Result, phones: String, message: String, sendDirect: Int?) {
        if (sendDirect != null) {
            sendSMSDirect(result, phones, message, sendDirect);
        } else {
            sendSMSDialog(result, phones, message);
        }
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.System.getInt(context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    fun isNoSignalStrength(context: Context): Boolean {
        val telephonyManager = context.getSystemService(Service.TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.signalStrength!!.level == 0
    }

    private fun sendSMSDirect(result: Result, phones: String, message: String, subcriptionId: Int) {
        // SmsManager is android.telephony
        if (isAirplaneModeOn(activity!!.applicationContext)) {
            result.error("radioOff", "Airplane mode on", "Airplane mode is on")
            return
        }
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isNoSignalStrength(activity!!.applicationContext)
                } else {
                    false
                }) {
            result.error("radioOff", "No Signal", "No Signal")
            return
        }
        val smsSentFlag = "SMS_SENT_ACTION"
        val sentIntent = PendingIntent.getBroadcast(activity, 0, Intent(smsSentFlag), PendingIntent.FLAG_IMMUTABLE)
        val mSmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.applicationContext?.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subcriptionId)!!
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subcriptionId)
        }
        activity?.applicationContext?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Refer https://developer.android.com/reference/android/telephony/SmsManager#sendTextMessage(java.lang.String,%20java.lang.String,%20java.lang.String,%20android.app.PendingIntent,%20android.app.PendingIntent)

                when (resultCode) {
                    Activity.RESULT_OK -> result.success("SMS Sent")
                    SmsManager.RESULT_ERROR_RADIO_OFF,
                    SmsManager.RESULT_ERROR_NO_SERVICE,
                    SmsManager.RESULT_RADIO_NOT_AVAILABLE,
                    SmsManager.RESULT_MODEM_ERROR,
                    SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE,
                    SmsManager.RESULT_RIL_NETWORK_NOT_READY,
                    -> result.error("radioOff", "Radio Off :$resultCode", "Radio is off resultCode :$resultCode")

                    else -> result.error("failed", "Sms Send Failed :$resultCode", "Generic Error resultCode :$resultCode")
                }
                activity?.applicationContext?.unregisterReceiver(this)
            }

        }, IntentFilter(smsSentFlag)
        )
        val numbers = phones.split(";")

        for (num in numbers) {
            Log.d("Flutter SMS", "msg.length() : " + message.toByteArray().size)
            if (message.toByteArray().size > 80) {
                val partMessage = mSmsManager.divideMessage(message)
                mSmsManager.sendMultipartTextMessage(num, null, partMessage, null, null)
            } else {
                mSmsManager.sendTextMessage(num, null, message, sentIntent, null)
            }
        }

    }

    private fun sendSMSDialog(result: Result, phones: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("smsto:$phones")
        intent.putExtra("sms_body", message)
        intent.putExtra(Intent.EXTRA_TEXT, message)
        activity?.startActivityForResult(intent, REQUEST_CODE_SEND_SMS)
        result.success("SMS Sent!")
    }
}
