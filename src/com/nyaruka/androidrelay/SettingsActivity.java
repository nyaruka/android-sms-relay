package com.nyaruka.androidrelay;

import java.net.URLEncoder;

import com.actionbarsherlock.R;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.log.SendLogActivity;

import android.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity {
	public static final String TAG = AndroidRelay.TAG;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);        
        addPreferencesFromResource(R.xml.preferences);
        
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
        		final Intent intent = new Intent(SendLogActivity.ACTION_SEND_LOG);
        		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(SendLogActivity.EXTRA_SEND_INTENT_ACTION, Intent.ACTION_SENDTO);
                startActivity(intent);
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
        
        Preference setRapidHost = (Preference) findPreference("rapidsms_hostname");
        setRapidHost.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String hostname = newValue.toString();
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
				String backend = prefs.getString("rapidsms_backend", "android");
				String password = prefs.getString("rapidsms_password", null);
				updateEndpoints(hostname, backend, password);
				return true;
			}        
        });
        
        Preference setRapidBackend = (Preference) findPreference("rapidsms_backend");
        setRapidBackend.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String backend = newValue.toString();
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
				String hostname = prefs.getString("rapidsms_hostname", null);
				String password = prefs.getString("rapidsms_password", null);
				updateEndpoints(hostname, backend, password);
				return true;
			}        
        });
        
        Preference setRapidPassword = (Preference) findPreference("rapidsms_password");
        setRapidPassword.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String password = newValue.toString();
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
				String backend = prefs.getString("rapidsms_backend", "android");
				String hostname = prefs.getString("rapidsms_hostname", null);
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

		Toast.makeText(SettingsActivity.this, "RapidSMS Endpoints Set", Toast.LENGTH_LONG).show();
		SettingsActivity.this.finish();
    }
}
