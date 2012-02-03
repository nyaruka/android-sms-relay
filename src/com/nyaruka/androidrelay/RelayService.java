package com.nyaruka.androidrelay;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.androidrelay.data.TextMessage;
import com.nyaruka.androidrelay.data.TextMessageHelper;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class RelayService extends Service implements SMSModem.SmsModemListener {

	public static final String TAG = RelayService.class.getSimpleName();
	
	public static RelayService s_this = null;
	public static RelayService get(){ return s_this; }
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate(){
		s_this = this;
		modem = new SMSModem(getApplicationContext(), this);
		Log.d(TAG, "RelayService Created.");
		promoteErroredMessages();
		kickService();
	}
	
	public TextMessageHelper getHelper(){
		return AndroidRelay.getHelper(getApplicationContext());
	}

	/**
	 * Toggles our connection from WIFI and vice versa.  If it does not and we are on WIFI, then 
	 * we try to switch to the mobile network.
	 */
	public void toggleConnection(){
		WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		m_targetWifiState = UNCHANGED;
		
		m_targetWifiState = wifi.isWifiEnabled() ? ON : OFF;
			
		// well that didn't work, let's flip our connection status, that might just help.. we sleep a bit so things can connect
		boolean newWifiState = !wifi.isWifiEnabled();
		Log.d(TAG, "Connection test failed, flipping WIFI state to: " + newWifiState);
		wifi.setWifiEnabled(newWifiState);
		
		// sleep 30 seconds to give the network a chance to connect
		try{
			Thread.sleep(30000);
		} catch (Throwable tt){}
	}
	
	public boolean isConnectionToggled(){
		return m_targetWifiState != UNCHANGED;
	}
	
	/**
	 * Restores the previous connection settings.  That is if we were on WiFi previously, then this 
	 * method will reenable WiFi again.
	 */
	public void restoreConnection(){
		WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (m_targetWifiState == OFF){
			Log.d(TAG, "Restoring WIFI to Off");
			wifi.setWifiEnabled(false);
		} else if (m_targetWifiState == ON){
			Log.d(TAG, "Restoring WIFI to On");
			wifi.setWifiEnabled(true);
		}
		m_targetWifiState = UNCHANGED;
	}
	
	/***
	 * This should be run only when our service starts, and takes care of resending any messages
	 * that were queued but which we never got a reply for.  This could result in double sends
	 * but that's better than leaving a message on the floor.
	 */
	public void promoteErroredMessages(){
		TextMessageHelper helper = getHelper();
			
		List<TextMessage> msgs = helper.withStatus(this.getApplicationContext(), TextMessage.OUTGOING, TextMessage.QUEUED);
		for(TextMessage msg : msgs){
			msg.status = TextMessage.ERRORED;
			helper.updateMessage(msg);
			MainActivity.getMessageList().updateMessage(msg);			
		}
	}

	/***
	 * Goes through all our activations, retriggering syncs for all those that need to be sent.
	 */
	public void requeueErroredIncoming(){
		TextMessageHelper helper = getHelper();
		List<TextMessage> msgs = helper.withStatus(this.getApplicationContext(), TextMessage.INCOMING, TextMessage.ERRORED);
			
		int count = 0;
		for(TextMessage msg : msgs){
			msg.status = TextMessage.RECEIVED;
			helper.updateMessage(msg);
			
			MainActivity.getMessageList().updateMessage(msg);
			
			count++;
			if (count >= 5){
				Log.d(TAG, "Reprocessed five messages, skipping rest.");
				break;
			}
		}	
	}
	
	/**
	 * Trims all but the latest 100 messages in our table.
	 */
	public void trimMessages(){
		TextMessageHelper helper = getHelper();
		helper.trimMessages();
	}
	
	/***
	 * Goes through all the messages which have errors and resends them.  Note that we only try to send
	 * five at a time, so it could take a bit to clear out the backlog.
	 */
	public void resendErroredSMS(){
		TextMessageHelper helper = getHelper();
		List<TextMessage> msgs = helper.erroredOutgoing(getApplicationContext());

		int count = 0;
		for(TextMessage msg : msgs){
			try{
				modem.sendSms(msg.number, msg.text, "" + msg.id);
				msg.status = TextMessage.QUEUED;
				Log.d(TAG, "resent " + msg.id + " -- " + msg.text);
			} catch (Throwable t){
				msg.status = TextMessage.ERRORED;
				Log.d(TAG, "errored " + msg.id + " -- " + msg.text);
			}
			helper.updateMessage(msg);
			MainActivity.getMessageList().updateMessage(msg);
			
			count++;
			if (count >= 5){
				Log.d(TAG, "Resent five messages, skipping rest.");
				break;
			}
		}
	}
	
	/**
	 * Sends all our pending outgoing messages to our server.
	 */
	public void sendPendingMessagesToServer() throws IOException {
		List<TextMessage> msgs = null;
		
		// first send any that haven't yet been tried
		TextMessageHelper helper = getHelper();
		msgs = helper.withStatus(this.getApplicationContext(), TextMessage.INCOMING, TextMessage.RECEIVED);
		for(TextMessage msg : msgs){
			sendMessageToServer(msg);
		}
		
		// then those that had an error when we tried to contact the server
		helper = getHelper();
		msgs = helper.withStatus(this.getApplicationContext(), TextMessage.INCOMING, TextMessage.ERRORED);
		for(TextMessage msg : msgs){
			sendMessageToServer(msg);
		}
	}
	
	public void markDeliveriesOnServer() throws IOException {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String deliveryURL = prefs.getString("delivery_url", null);
			
		if (deliveryURL != null && deliveryURL.length() > 0){
			TextMessageHelper helper = getHelper();
			List<TextMessage> msgs = helper.withStatus(this.getApplicationContext(), TextMessage.OUTGOING, TextMessage.SENT);
			for(TextMessage msg : msgs){
				markMessageDelivered(msg);
			}
		}
	}
	
	public String fetchURL(String url) throws ClientProtocolException, IOException{
		HttpClient client = new DefaultHttpClient();
		client.getParams().setParameter("http.connection-manager.timeout", new Integer(15000));
		client.getParams().setParameter("http.connection.timeout", new Integer(15000));
		client.getParams().setParameter("http.socket.timeout", new Integer(15000));
		
		HttpGet httpget = new HttpGet(url);
		ResponseHandler<String> responseHandler = new BasicResponseHandler();
		String content = client.execute(httpget, responseHandler);
		
		return content;
	}
	
	/**
	 * Sends a message to our server.
	 * @param msg
	 */
	public void sendMessageToServer(TextMessage msg) throws IOException {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String receiveURL = prefs.getString("receive_url", null);
		boolean process_outgoing = prefs.getBoolean("process_outgoing", false);
		TextMessageHelper helper = getHelper();
		
		Log.d(TAG, "Receive URL: " + receiveURL);
		
		// no delivery url means we don't do anything
		if (receiveURL == null || receiveURL.length() == 0){
			return;
		}
		
		String url = receiveURL + "&sender=" + URLEncoder.encode(msg.number) + "&message=" + URLEncoder.encode(msg.text);			
		Log.d(TAG, "Sending: "+ url);
		
		try {
			String content = fetchURL(url);
			
			if (content.trim().length() > 0){
				JSONObject json = new JSONObject(content);
				
				// if we are supposed to process outgoing messages, then read any responses
				if (process_outgoing){
					JSONArray responses = json.getJSONArray("responses");
					for (int i=0; i<responses.length(); i++) {
						JSONObject response = responses.getJSONObject(i);
						String number = "+" + response.getString("contact");
						String message = response.getString("text");					
						long serverId = response.getLong("id");

						if ("O".equals(response.getString("direction"))	&& "Q".equals(response.getString("status"))) {					
							// if this message doesn't already exist
							TextMessage existing = helper.withServerId(this.getApplicationContext(), serverId);
							if (existing == null){
								Log.d(TAG, "Got reply: " + serverId + ": " + message);
								TextMessage toSend = new TextMessage(number, message, serverId);
								helper.createMessage(toSend);
								sendMessage(toSend);
							}
						}
					}
				}
			}
			msg.status = TextMessage.HANDLED;
			msg.error = null;
			Log.d(TAG, "Msg '" + msg.text + "' handed to server.");
		} catch (IOException e){
			msg.error = e.getClass().getSimpleName() + ": " + e.getMessage();
			msg.status = TextMessage.ERRORED;
			throw e;
		} catch (Throwable t) {
			Log.d(TAG, "Got Error: "+ t.getMessage(), t);
			msg.error = t.getClass().getSimpleName() + ": " + t.getMessage();
			msg.status = TextMessage.ERRORED;
		} finally {     
			helper.updateMessage(msg);
			MainActivity.getMessageList().updateMessage(msg);
		}
	}
	
	public void markMessageDelivered(TextMessage msg) throws IOException {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String deliveryURL = prefs.getString("delivery_url", null);
		TextMessageHelper helper = getHelper();		
		
		Log.d(TAG, "Delivery URL: " + deliveryURL);
		
		// no delivery url means we don't do anything
		if (deliveryURL == null || deliveryURL.length() == 0){
			return;
		}
		
		if (msg.serverId <= 0){
			msg.status = TextMessage.DONE;
			msg.error = "Ignored due to 0 id";
		} else {
			String url = deliveryURL + "&message_id=" + msg.serverId;
			Log.d(TAG, "Sending: "+ url);
		
			try {
				String content = fetchURL(url);
				msg.status = TextMessage.DONE;
				msg.error = null;
				Log.d(TAG, "Msg " + msg.serverId + " marked as delivered.");
			} catch (IOException e){
				msg.status = TextMessage.SENT;
				msg.error = e.getClass().getSimpleName() + ": " + e.getMessage();
				throw e;
			} catch (Throwable t) {
				Log.d(TAG, "Got Error: "+ t.getMessage(), t);
				msg.status = TextMessage.SENT;
				msg.error = t.getClass().getSimpleName() + ": " + t.getMessage();
			} finally {
				helper.updateMessage(msg);
				MainActivity.getMessageList().updateMessage(msg);						
			}
		}
	}
	
	/**
	 * Sends a message to our server.
	 * @param msg
	 */
	public void checkOutbox() throws IOException {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String updateInterval = prefs.getString("update_interval", "30000");
		long interval = Long.parseLong(updateInterval);

		String outboxURL = prefs.getString("outbox_url", null);
		TextMessageHelper helper = getHelper();		
		
		// no delivery url means we don't do anything
		if (outboxURL == null || outboxURL.length() == 0){
			return;
		}
		
		// if our update interval is set to 0, then that means we shouldn't be checking, so skip
		if (interval == 0){
			return;
		}
		
		Log.d(TAG, "Outbox URL: " + outboxURL);
		
		try {
			String content = fetchURL(outboxURL);
			
			if (content.trim().length() > 0){
				JSONObject json = new JSONObject(content);
				JSONArray responses = json.getJSONArray("outbox");
				for (int i=0; i<responses.length(); i++) {
					JSONObject response = responses.getJSONObject(i);		
					if ("O".equals(response.getString("direction")) && "Q".equals(response.getString("status"))) {
						String number = "+" + response.getString("contact");
						String message = response.getString("text");
						long serverId = response.getLong("id");
						
						// if this message doesn't already exist
						TextMessage existing = helper.withServerId(this.getApplicationContext(), serverId);
						if (existing == null){
							Log.d(TAG, "New outgoing msg: " + serverId + ": " + message);
							TextMessage toSend = new TextMessage(number, message, serverId);
							helper.createMessage(toSend);
							sendMessage(toSend);
						} else {
							if (existing.status == TextMessage.DONE){
								existing.status = TextMessage.SENT;
								helper.updateMessage(existing);
							}
							Log.d(TAG, "Ignoring message: " + serverId + " already queued.");
						}
					}
				}
			}
			Log.d(TAG, "Outbox fetched from server");
		} catch (IOException e){
			throw e;
		} catch (Throwable t) {
			Log.d(TAG, "Got Error: "+ t.getMessage(), t);
		}        
	}

	// triggers our background service to go do things
	public void kickService(){
		WakefulIntentService.sendWakefulWork(this, CheckService.class);
	}

	public void sendMessage(TextMessage msg){
		Log.d(TAG, "=== SMS OUT: " + msg.number + ": " + msg.text);		
		try {
			modem.sendSms(msg.number, msg.text, "" + msg.id);
		} catch (Exception e){
			msg.status = TextMessage.ERRORED;
		}
		MainActivity.getMessageList().updateMessage(msg);
	}
	
	public void onNewSMS(String number, String message) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean process_messages = prefs.getBoolean("process_incoming", false);
		
		// if we aren't supposed to process messages, ignore this message
		if (!process_messages){
			return;
		}
		
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = new TextMessage();
		msg.number = number;
		msg.text = message;
		msg.created = new Date();
		msg.direction = TextMessage.INCOMING;
		msg.status = TextMessage.RECEIVED;
		helper.createMessage(msg);
	
		Log.d(TAG, "=== SMS IN:" + msg.number + ": " + msg.text);
		MainActivity.getMessageList().updateMessage(msg);
		
		kickService();
	}	
	
	public void onSMSSendError(String token, String errorDetails) {
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = helper.withId(Long.parseLong(token));
		msg.status = TextMessage.ERRORED;
		msg.error = "SMS send error";
		helper.updateMessage(msg);
		
		Log.d(TAG, "=== SMS ERROR:" + token + " Details: " + errorDetails);
		MainActivity.getMessageList().updateMessage(msg);
	}

	public void onSMSSent(String token) {
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = helper.withId(Long.parseLong(token));
		msg.status = TextMessage.SENT;
		msg.error = "";
		helper.updateMessage(msg);
		
		Log.d(TAG, "=== SMS SENT: " + token);
		MainActivity.getMessageList().updateMessage(msg);
		kickService();
	}
	
	public SMSModem modem;
	
	public static final int UNCHANGED = 0;
	public static final int ON = 1;
	public static final int OFF = -1;
	
	/** whether the WiFi network is set */
	private int m_targetWifiState = UNCHANGED;
}
