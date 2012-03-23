package com.nyaruka.androidrelay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public final class SMSModem extends BroadcastReceiver {

	private static final String SMS_DELIVER_REPORT_ACTION = "com.nyaruka.androidrelay.SMS_DELIVER_REPORT";
	private static final String SMS_DELIVER_REPORT_TOKEN_EXTRA = "token";

	private static final String TAG = AndroidRelay.TAG;
	private final Context context;
	private final SmsManager smsManager;
	private final SmsModemListener listener;

	private final Map<String, Integer> pendingSMS = new HashMap<String, Integer>();

	public interface SmsModemListener {
		public void onSMSSent(String token);
		public void onSMSSendError(String token, String errorDetails);
		public void onNewSMS(String address, String message);
	}

	public SMSModem(Context c, SmsModemListener l) {
		context = c;
		listener = l;
		smsManager = SmsManager.getDefault();
		
		final IntentFilter receivedFilter = new IntentFilter();
		receivedFilter.addAction("android.provider.Telephony.SMS_RECEIVED");
		context.registerReceiver(this, receivedFilter);

		final IntentFilter deliveryFilter = new IntentFilter();
		deliveryFilter.addAction(SMS_DELIVER_REPORT_ACTION);
		deliveryFilter.addDataScheme("sms");
		context.registerReceiver(this, deliveryFilter);
	}

	public void sendSms(String address, String message, String token) {
		if (message != null && address != null && token != null) {
			final ArrayList<String> parts = smsManager.divideMessage(message);
			final Intent intent = new Intent(SMS_DELIVER_REPORT_ACTION);
			intent.setData(Uri.fromParts("sms", token, ""));
			intent.putExtra(SMS_DELIVER_REPORT_TOKEN_EXTRA, token);
			final PendingIntent sentIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			final ArrayList<PendingIntent> intents = new ArrayList<PendingIntent>();
			for (int i = 0; i < parts.size(); i++) {
				intents.add(sentIntent);
			}
			pendingSMS.put(token, parts.size());
			Log.d(TAG, "Sending [" + intent.getData() + "] " + address + " - " + parts);
			smsManager.sendMultipartTextMessage(address, null, parts, intents, null);
		}
	}

	public void clear() {
		context.unregisterReceiver(this);
	}

	@Override
	public void onReceive(Context c, Intent intent) {
		final String action = intent.getAction();
		if (action.equalsIgnoreCase("android.provider.Telephony.SMS_RECEIVED")) {
			final Bundle bundle = intent.getExtras();
			if (bundle != null) {
				Object[] pdusObj = (Object[]) bundle.get("pdus");
				final SmsMessage[] messages = new SmsMessage[pdusObj.length];
				for (int i = 0; i < pdusObj.length; i++) {
					messages[i] = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
					final String address = messages[i].getDisplayOriginatingAddress();
					final String message = messages[i].getDisplayMessageBody();
					listener.onNewSMS(address, message);
				}
			}
		} else if (action.equalsIgnoreCase(SMS_DELIVER_REPORT_ACTION)) {
			final int resultCode = getResultCode();
			final String token = intent.getStringExtra(SMS_DELIVER_REPORT_TOKEN_EXTRA);
			Log.d(TAG, "Deliver report, result code '" + resultCode	+ "', token '" + token + "' URI: " + intent.getData());
			if (resultCode == Activity.RESULT_OK) {
				if (pendingSMS.containsKey(token)) {
					pendingSMS.put(token, pendingSMS.get(token).intValue() - 1);
					if (pendingSMS.get(token).intValue() == 0) {
						pendingSMS.remove(token);
						listener.onSMSSent(token);
					}
				}
			} else {
				if (pendingSMS.containsKey(token)) {
					pendingSMS.remove(token);
					listener.onSMSSendError(token, extractError(resultCode,
							intent));
				}
			}
		}
	}

	private String extractError(int resultCode, Intent i) {
		switch (resultCode) {
		case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
			if (i.hasExtra("errorCode")) {
				return String.valueOf(i.getIntExtra("errorCode",-1));
			} else {
				return "Unknown error. No 'errorCode' field.";
			}
		case SmsManager.RESULT_ERROR_NO_SERVICE:
			return "No service";
		case SmsManager.RESULT_ERROR_RADIO_OFF:
			return "Radio off";
		case SmsManager.RESULT_ERROR_NULL_PDU:
			return "PDU null";
		default:
			return "really unknown error";
		}
	}
}