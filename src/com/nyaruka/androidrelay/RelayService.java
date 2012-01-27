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
import android.content.Intent;
import android.content.SharedPreferences;
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
	}
	
	public TextMessageHelper getHelper(){
		return AndroidRelay.getHelper(getApplicationContext());
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
			MainActivity.updateMessage(msg);			
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
			
			MainActivity.updateMessage(msg);
			
			count++;
			if (count >= 5){
				Log.d(TAG, "Reprocessed five messages, skipping rest.");
				break;
			}
		}	
	}
	
	/***
	 * Goes through all the messages which have errors and resends them.  Note that we only try to send
	 * five at a time, so it could take a bit to clear out the backlog.
	 */
	public void resendErroredSMS(){
		TextMessageHelper helper = getHelper();
		List<TextMessage> msgs = helper.withStatus(this.getApplicationContext(), TextMessage.OUTGOING, TextMessage.ERRORED);

		int count = 0;
		for(TextMessage msg : msgs){
			modem.sendSms(msg.number, msg.text, "" + msg.id);
			msg.status = TextMessage.QUEUED;
			helper.updateMessage(msg);

			MainActivity.updateMessage(msg);
				
			Log.d(TAG, "resent " + msg.id + " -- " + msg.text);
			
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
	public void sendPendingMessagesToServer(){
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
	
	public void markDeliveriesOnServer(){
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
	public void sendMessageToServer(TextMessage msg){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		String receiveURL = prefs.getString("receive_url", null);
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
			msg.status = TextMessage.HANDLED;
			msg.error = null;
			Log.d(TAG, "Msg given to server.");
		} catch (Throwable t) {
			Log.d(TAG, "Got Error: "+ t.getMessage(), t);
			msg.error = t.getMessage();
			msg.status = TextMessage.ERRORED;
		}        
		
		helper.updateMessage(msg);
		MainActivity.updateMessage(msg);
	}
	
	public void markMessageDelivered(TextMessage msg){
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
				msg.status = TextMessage.DONE;
				msg.error = null;
				Log.d(TAG, "Msg marked as delivered.");
			} catch (Throwable t) {
				Log.d(TAG, "Got Error: "+ t.getMessage(), t);
				msg.status = TextMessage.SENT;
				msg.error = t.getMessage();
			}        
		}
		
		helper.updateMessage(msg);
		MainActivity.updateMessage(msg);		
	}
	
	/**
	 * Sends a message to our server.
	 * @param msg
	 */
	public void checkOutbox(){
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
							Log.d(TAG, "Got reply: " + serverId + ": " + message);
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
		MainActivity.addTextMessage(msg);
		modem.sendSms(msg.number, msg.text, "" + msg.id);
	}
	
	public void onNewSMS(String number, String message) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean process_messages = prefs.getBoolean("process_messages", false);
		
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
		MainActivity.addTextMessage(msg);
		
		kickService();
	}	
	
	public void onSMSSendError(String token, String errorDetails) {
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = helper.withId(Long.parseLong(token));
		msg.status = TextMessage.ERRORED;
		helper.updateMessage(msg);
		
		Log.d(TAG, "=== SMS ERROR:" + token + " Details: " + errorDetails);
		MainActivity.updateMessage(msg);
	}

	public void onSMSSent(String token) {
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = helper.withId(Long.parseLong(token));
		msg.status = TextMessage.SENT;
		helper.updateMessage(msg);
		
		Log.d(TAG, "=== SMS SENT: " + token);
		MainActivity.updateMessage(msg);
	}
	
	public SMSModem modem;
}
