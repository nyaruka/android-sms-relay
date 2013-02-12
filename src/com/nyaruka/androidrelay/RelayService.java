package com.nyaruka.androidrelay;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.androidrelay.data.TextMessage;
import com.nyaruka.androidrelay.data.TextMessageHelper;

public class RelayService extends Service implements SMSModem.SmsModemListener {

	public static final String TAG = AndroidRelay.TAG;
	
	public static RelayService s_this = null;
	public static RelayService get(){ return s_this; }
	
	// what was the last alert type we sent
	public static long s_lastAlert;
	
	// constants for s_lastAlert
	public static final int ALERT_POWER_ON = 0;
	public static final int ALERT_50_PERCENT = 1;
	public static final int ALERT_25_PERCENT = 2;
	public static final int ALERT_5_PERCENT = 3;
	
	// APN related
	public static final Uri APN_TABLE_URI =  Uri.parse("content://telephony/carriers");
	public static final Uri PREFERRED_APN_URI = Uri.parse("content://telephony/carriers/preferapn");
	
	public static boolean doReset = false;
	public static boolean doSendLog = false;

	public int createAPN(String name, String apnAddr) {
		int id = -1;

		TelephonyManager tele = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String mcc = tele.getSimOperator().substring(0, 3);
		String mnc = tele.getSimOperator().substring(3);
		
		ContentResolver resolver = this.getContentResolver();
		ContentValues values = new ContentValues();
		values.put("name", name);
		values.put("apn", apnAddr);

		values.put("mcc", mcc);
		values.put("mnc", mnc);
		values.put("numeric", tele.getSimOperator());
		
		Cursor cursor = null;
		
		try {
			Uri newAPN = resolver.insert(APN_TABLE_URI, values);
			if(newAPN != null) {
				cursor = resolver.query(newAPN, null, null, null, null);
				Log.d(TAG, "New APN added! Yeee.");

				// Obtain the apn id
                int idindex = cursor.getColumnIndex("_id");
                cursor.moveToFirst();
                id = cursor.getShort(idindex);
			}
		}catch (SQLException e) {
			Log.d(TAG, e.getMessage());
		}finally {
			if (cursor != null)
				cursor.close();
		}
		
		return id;
	}
	
	public int getDefaultAPN() {
		int id = -1;
		ContentResolver resolver = this.getContentResolver();
		Cursor cursor = resolver.query(PREFERRED_APN_URI, new String[] { "_id", "name" }, null, null, null);
		
		if (cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					id = cursor.getInt(cursor.getColumnIndex("_id"));
				}
			} catch (SQLException e) {
				Log.d(TAG, e.getMessage());
			} finally {
			cursor.close();
			}
		}
		return id;
	}
	
	public boolean setDefaultAPN(int id) {
		boolean res = false;
        ContentResolver resolver = this.getContentResolver();
        ContentValues values = new ContentValues();

        values.put("apn_id", id); 

        try {
            resolver.update(PREFERRED_APN_URI, values, null, null);
            Cursor cursor = resolver.query(PREFERRED_APN_URI, new String[]{"name","apn"}, "_id="+id, null, null);
            if(cursor != null) {
                res = true;
                cursor.close();
            }
        }
        catch (SQLException e) {
            Log.d(TAG, e.getMessage());
        }
        return res;
	}

	public void deleteAPN(int id) {
		ContentResolver resolver = this.getContentResolver();
        
        try {
        	resolver.delete(APN_TABLE_URI, "_id=?", new String[] { Integer.toString(id)});
        } catch(SQLException e) {
        	
        }
	}
	
	public void deleteUnfavoriteAPNs() {
		ContentResolver resolver = this.getContentResolver();
		
		try {
        	resolver.delete(APN_TABLE_URI, "apn LIKE ?", new String[]{"relay.nyaruka.com"});
		} catch(SQLException e) {
			
		}
	}
	
	public void tickleDefaultAPN() {
		int id_default = this.getDefaultAPN();
		int id_fakeAPN = this.createAPN("SMS Relay", "relay.nyaruka.com");

		Log.d(TAG, "Tickling the Default APN" + id_default +" by fake APN " + id_fakeAPN);

		// make the fake APN the default for
		this.setDefaultAPN(id_fakeAPN);

		// 30 seconds before
		try{
			Thread.sleep(30000);
		} catch (Throwable tt){}

		// switching back to the real working APN
		this.setDefaultAPN(id_default);
		
		//and delete the fake APN not in use anymore
		//this.deleteAPN(id_fakeAPN);
		this.deleteUnfavoriteAPNs();
	}
	
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

	public void checkPowerStatus(){
		Context c = getApplicationContext();
		Intent intent = this.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		
		// get whether we are plugged in
		boolean hasPower = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
		
		// get our battery level as percentage
		int level = intent.getIntExtra("level", 0);

		// no power
		if (!hasPower){
			// less than 5% and haven't already reported it
			if (level <= 5 && s_lastAlert != ALERT_5_PERCENT){
				if (sendAlert(c, "Relayer has no power and battery level at " + level + "%", "")){
					s_lastAlert = ALERT_5_PERCENT;
				}
			} 
			// less than 25% and haven't already reported it
			else if (level <= 25 && level > 5 && s_lastAlert != ALERT_25_PERCENT){
				if (sendAlert(c, "Relayer has no power and battery level at " + level + "%", "")){
					s_lastAlert = ALERT_25_PERCENT;
				}
			} 
			// less than 50% and haven't already reported it
			else if (level <= 50 && level > 25 && s_lastAlert != ALERT_50_PERCENT){
				if (sendAlert(c, "Relayer has no power and battery level at " + level + "%", "")){
					s_lastAlert = ALERT_50_PERCENT;
				}
			}
		} 
		// is having power a new condition, IE, did we scream about it?
		else if (s_lastAlert != ALERT_POWER_ON){
			if (sendAlert(c, "Relayer regained power, battery level at " + level + "%", "")){
				s_lastAlert = ALERT_POWER_ON;
			}
		}
	}

	/**
	 * Sends an alert to our server with the passed in subject.  The body will contain
	 * configuration attributes and debugging information.
	 * 
	 * TODO: Would be nice to echo these out to SMS if the client is configured 
	 *       with an alert phone number.
	 *       
	 * @param subject The subject of the alert to send to the server
	 */
	public static boolean sendAlert(Context context, String subject, String body){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String hostname = prefs.getString("router_hostname", null);
		
		Log.d(TAG, "__SENDING ALERT: " + subject);
		
		if (hostname != null && subject != null){
			// send the alert off
			HttpClient client = new DefaultHttpClient();
			client.getParams().setParameter("http.connection-manager.timeout", new Integer(60000));
			client.getParams().setParameter("http.connection.timeout", new Integer(60000));
			client.getParams().setParameter("http.socket.timeout", new Integer(60000));
			
			StringBuilder conf = new StringBuilder();
			conf.append("SMS Relay Version: " + AndroidRelay.getVersionNumber(context));
			conf.append("\n");
			conf.append("\nHostname: " + prefs.getString("router_hostname", null));
			String backend = prefs.getString("router_backend", null);
			conf.append("\nBackend:" + backend);
			conf.append("\nPassword:" + prefs.getString("router_password", null));
			conf.append("\n");
			conf.append("\nProcess Incoming:" + prefs.getBoolean("process_incoming", false));
			conf.append("\nProcess Outgoing:" + prefs.getBoolean("process_outgoing", false));
			conf.append("\nInterval:" + prefs.getString("update_interval", "null"));
			
			TextMessageHelper helper = AndroidRelay.getHelper(context);
			int total = helper.getAllMessages().size();
			int unsynced = helper.withStatus(context, TextMessage.INCOMING, TextMessage.RECEIVED).size();

			conf.append("\n");
			conf.append("\nTotal Messages:" + total);
			conf.append("\nUnsynced:" + unsynced);
			List<TextMessage> erroredOut = helper.withStatus(context, TextMessage.OUTGOING, TextMessage.ERRORED);
			conf.append("\n\nErrored Out: " + erroredOut.size());
			for (TextMessage msg : erroredOut){
				conf.append("\n" + msg.number + ": " + msg.text);
			}
			List<TextMessage> erroredIn = helper.withStatus(context, TextMessage.INCOMING, TextMessage.ERRORED);
			conf.append("\n\nErrored In: " + erroredIn.size());
			for (TextMessage msg : erroredIn){
				conf.append("\n" + msg.number + ": " + msg.text);
			}
			conf.append("\n\nLog:\n\n");
			
			// prepend our configuration to our body
			body = conf.toString() + body;
			try{
				HttpPost post = new HttpPost("http://" + hostname + "/router/alert");
				List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
				nameValuePairs.add(new BasicNameValuePair("password", "" + prefs.getString("router_password", null)));
				nameValuePairs.add(new BasicNameValuePair("subject", "[" + backend + "] " + subject));
				nameValuePairs.add(new BasicNameValuePair("body", body));
				post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
				
				Log.e(MainActivity.TAG, "Sending log to: " + post.getURI().toURL().toString());
			
				ResponseHandler<String> responseHandler = new BasicResponseHandler();
				client.execute(post, responseHandler);
				return true;
			} catch (Throwable t){
				Log.e(MainActivity.TAG, "Sending of alert failed", t);
				return false;
			}
		}
		
		return false;
	}
	
	/**
	 * Toggles our connection from WIFI and vice versa.  If it does not and we are on WIFI, then 
	 * we try to switch to the mobile network.
	 */
	public void toggleConnection(){
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			
		// well that didn't work, let's flip our connection status, that might just help.. we sleep a bit so things can connect
		boolean newWifiState = !wifi.isWifiEnabled();
		Log.d(TAG, "Toggling Connection: Connection test failed, flipping WIFI state to: " + newWifiState);
		wifi.setWifiEnabled(newWifiState);
		
		// sleep 30 seconds to give the network a chance to connect
		try{
			Thread.sleep(30000);
		} catch (Throwable tt){}
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
				Log.d(TAG, "Sending [" + msg.id + "] - " + msg.number + " - "+ msg.text);
				modem.sendSms(msg.number, msg.text, "" + msg.id);
				msg.status = TextMessage.QUEUED;
			} catch (Throwable t){
				msg.status = TextMessage.ERRORED;
				Log.d(TAG, "Errored [" + msg.id + "] - " + msg.number + " - "+ msg.text, t);
			}
			helper.updateMessage(msg);
			MainActivity.updateMessage(msg);
			
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
		client.getParams().setParameter("http.connection-manager.timeout", new Integer(25000));
		client.getParams().setParameter("http.connection.timeout", new Integer(25000));
		client.getParams().setParameter("http.socket.timeout", new Integer(25000));
		
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
		} catch (HttpResponseException e){
			Log.d(TAG, "HTTP ERROR Got Error: "+ e.getMessage(), e);
			msg.error = e.getClass().getSimpleName() + ": " + e.getMessage();
			msg.status = TextMessage.ERRORED;			
		} catch (IOException e){
			msg.error = e.getClass().getSimpleName() + ": " + e.getMessage();
			msg.status = TextMessage.ERRORED;
			throw e;
		} catch (Throwable t) {
			Log.d(TAG, "THROWABLE Got Error: "+ t.getMessage(), t);
			msg.error = t.getClass().getSimpleName() + ": " + t.getMessage();
			msg.status = TextMessage.ERRORED;
		} finally {     
			helper.updateMessage(msg);
			MainActivity.updateMessage(msg);
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
				fetchURL(url);
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
				MainActivity.updateMessage(msg);
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
		} catch (HttpResponseException e){
			Log.d(TAG, "Got Error: "+ e.getMessage(), e);
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
		MainActivity.updateMessage(msg);
	}

	public void onNewSMS(String number, String message) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean process_messages = prefs.getBoolean("process_incoming", false);

		TextMessageHelper helper = getHelper();
		String keyword = prefs.getString("relay_password", "nyaruka").toLowerCase();

		// if we aren't supposed to process messages, ignore this message
		if (!process_messages){
			return;
		}
		
		// if someone sends 'keyword log' then send a log to the server
		if (message.equalsIgnoreCase(keyword + " log")){
			RelayService.doSendLog = true;
            
			int unsynced = helper.withStatus(getApplicationContext(), TextMessage.INCOMING, TextMessage.RECEIVED).size();
			int erroredOut = helper.withStatus(getApplicationContext(), TextMessage.OUTGOING, TextMessage.ERRORED).size();
			int erroredIn = helper.withStatus(getApplicationContext(), TextMessage.INCOMING, TextMessage.ERRORED).size();
			
			modem.sendSms(number, "Log being sent. " + unsynced + " unsynced.  " + erroredOut + " out errors.  " + erroredIn + " in errors.", "-1");
            return;
		}

		if(message.equalsIgnoreCase(keyword + " reset")){
			// change the label to true
			doReset = true;
			Log.d(TAG, "The reset process is set to " + Boolean.toString(doReset));
			
			// start the check service for reset changes to take effect
			kickService();
		
			modem.sendSms(number, "Reset process has been started. ", "-1");			
			return;
		}

		if(message.equalsIgnoreCase(keyword + " clear")){
			Log.d(TAG, "Clearing all messages");
			AndroidRelay.clearMessages(RelayService.this);
            Toast.makeText(RelayService.this, "Messages cleared", Toast.LENGTH_LONG).show();
            
            modem.sendSms(number, "All messages have been removed.", "-1");			
			return;
		}
		
		{
			SharedPreferences.Editor editor = prefs.edit();

			// process message with 'keyword data and change the network connection to specified mode
			if(message.equalsIgnoreCase(keyword + " data")) {
				Log.d(TAG, "Switching to data connection");

				editor.putString("pref_net", "1");
				editor.commit();

				modem.sendSms(number, "Your preferred network is now set to 'MOBILE DATA'", "-1");			
				return;
			}
		
			// process message with 'keyword wifi and change the network connection to specified mode
			if(message.equalsIgnoreCase(keyword + " wifi")) {
				Log.d(TAG, "Switching to wifi connection");
			
				editor.putString("pref_net", "0");
				editor.commit();

				modem.sendSms(number, "Your preferred network is now set to 'WIFI'", "-1");
				return;
			}
		}


		TextMessage msg = null;

		msg = new TextMessage();
		msg.number = number;
		msg.text = message;
		msg.created = new Date();
		msg.direction = TextMessage.INCOMING;
		msg.status = TextMessage.RECEIVED;
		helper.createMessage(msg);
	
		Log.d(TAG, "=== SMS IN:" + msg.number + ": " + msg.text);
		MainActivity.updateMessage(msg);
		
		kickService();
	}	
	
	public void onSMSSendError(String token, String errorDetails) {
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = helper.withId(Long.parseLong(token));
		
		if (msg != null){
			msg.status = TextMessage.ERRORED;
			msg.error = "SMS send error";
			helper.updateMessage(msg);
		
			Log.d(TAG, "=== SMS ERROR:" + token + " Details: " + errorDetails);
			MainActivity.updateMessage(msg);
		}
	}

	public void onSMSSent(String token) {
		TextMessage msg = null;
		TextMessageHelper helper = getHelper();		
		
		msg = helper.withId(Long.parseLong(token));
		
		if (msg != null){
			msg.status = TextMessage.SENT;
			msg.error = "";
			helper.updateMessage(msg);
		
			Log.d(TAG, "=== SMS SENT: " + token);
			MainActivity.updateMessage(msg);
			kickService();
		}
	}
	
	public SMSModem modem;
}
