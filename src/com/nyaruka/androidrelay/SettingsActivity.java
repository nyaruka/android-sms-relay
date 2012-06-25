package com.nyaruka.androidrelay;

import java.net.URLEncoder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class SettingsActivity extends PreferenceActivity {
	public static final String TAG = AndroidRelay.TAG;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {        
    	super.onCreate(savedInstanceState);
    	View title = getWindow().findViewById(android.R.id.title);
    	title.setPadding(5, 0, 0, 0);
        addPreferencesFromResource(R.xml.preferences);
        
        // Add a listener for relay password
        Preference relayPassword = (Preference) findPreference("relay_password");
        relayPassword.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

			public boolean onPreferenceChange(Preference preference,Object newValue) {
				String password = newValue.toString();
				Log.d(TAG, "New password " + password);
				
				return true;
			}
        });
        
        Preference prefNetwork = (Preference) findPreference("pref_net");
        prefNetwork.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				
				Log.d(TAG, "Your preferred network has been changed to: " + newValue);
				return true;
			}
        });
        

        
        // Add a listener for our clear button, with a confirmation
        Preference clearMessages = (Preference) findPreference("clear_messages");
        clearMessages.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                // show a confirmation dialog
                        new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("WARNING")
                        .setMessage("This will remove all messages, including ones not yet sent to the server or mobile phones.\n\nThis operation cannot be undone.")
                        .setPositiveButton("Reset", new OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                        	AndroidRelay.clearMessages(SettingsActivity.this);
                                            Toast.makeText(SettingsActivity.this, "Messages cleared", Toast.LENGTH_LONG).show();
                                        }})
                        .setNegativeButton(android.R.string.cancel, null)
                        .show(); 
                        return true;
                }
        });
        
        Preference sendLog = (Preference) findPreference("send_log");
        sendLog.setOnPreferenceClickListener(new OnPreferenceClickListener(){
        	public boolean onPreferenceClick(Preference preference){
        		RelayService.doSendLog = true;
        		WakefulIntentService.scheduleAlarms(new com.nyaruka.androidrelay.AlarmListener(1), getApplicationContext());
	    	    Toast.makeText(SettingsActivity.this, "Log will be sent shortly.", Toast.LENGTH_LONG).show();
                return true;
        	}
        });
        
        Preference updateInterval = (Preference) findPreference("update_interval");
        updateInterval.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				WakefulIntentService.scheduleAlarms(new com.nyaruka.androidrelay.AlarmListener(Long.parseLong(newValue.toString())), SettingsActivity.this);
				Log.d(TAG, "Rescheduling alarms based on new update_interval value: " + newValue);
				return true;
			}
        });
        
        Preference setRapidHost = (Preference) findPreference("router_hostname");
        setRapidHost.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String hostname = newValue.toString();
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
				String backend = prefs.getString("router_backend", "android");
				String password = prefs.getString("router_password", null);
				updateEndpoints(hostname, backend, password);
				return true;
			}        
        });
        
        Preference setRapidBackend = (Preference) findPreference("router_backend");
        setRapidBackend.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String backend = newValue.toString();
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
				String hostname = prefs.getString("router_hostname", null);
				String password = prefs.getString("router_password", null);
				updateEndpoints(hostname, backend, password);
				return true;
			}        
        });
        
        Preference setRapidPassword = (Preference) findPreference("router_password");
        setRapidPassword.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String password = newValue.toString();
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
				String backend = prefs.getString("router_backend", "android");
				String hostname = prefs.getString("router_hostname", null);
				updateEndpoints(hostname, backend, password);
				return true;
			}        
        });
    }
    
    public void updateEndpoints(String hostname, String backend, String password){
		Editor editor = findPreference("receive_url").getEditor();
		
		if (hostname == null){
			Toast.makeText(SettingsActivity.this, "Please set Hostname", Toast.LENGTH_LONG).show();
			return;
		}
		
		String receiveURL = "http://" + hostname + "/router/receive?backend=" + URLEncoder.encode(backend) + "&";
		if (password != null){
			receiveURL += "password=" + URLEncoder.encode(password) + "&";
		}
		editor.putString("receive_url", receiveURL);
		
		String outboxURL = "http://" + hostname + "/router/outbox?backend=" + URLEncoder.encode(backend) + "&";
		if (password != null){
			outboxURL += "password=" + URLEncoder.encode(password) + "&";
		}
		editor.putString("outbox_url", outboxURL);
		
		String deliveryURL = "http://" + hostname + "/router/delivered?backend=" + URLEncoder.encode(backend) + "&";
		if (password != null){
			deliveryURL += "password=" + URLEncoder.encode(password) + "&";
		}
		editor.putString("delivery_url", deliveryURL);
		editor.commit();

		Toast.makeText(SettingsActivity.this, "Router Endpoints Set", Toast.LENGTH_LONG).show();
		SettingsActivity.this.finish();
    }
}
