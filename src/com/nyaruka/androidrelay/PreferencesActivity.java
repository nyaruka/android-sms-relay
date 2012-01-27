package com.nyaruka.androidrelay;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import android.app.AlertDialog;

import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

public class PreferencesActivity extends PreferenceActivity {
	public static final String TAG = CheckService.class.getSimpleName();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {        
        super.onCreate(savedInstanceState);        
        addPreferencesFromResource(R.xml.preferences);
        
        // Add a listener for our clear button, with a confirmation
        Preference clearMessages = (Preference) findPreference("clear_messages");
        clearMessages.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                // show a confirmation dialog
                        new AlertDialog.Builder(PreferencesActivity.this)
                        .setTitle("WARNING")
                        .setMessage("This will remove all messages, including ones not yet sent to the server or mobile phones.\n\nThis operation cannot be undone.")
                        .setPositiveButton("Reset", new OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                        	AndroidRelay.clearMessages(PreferencesActivity.this.getApplicationContext());
                                            Toast.makeText(getBaseContext(), "Messages cleared", Toast.LENGTH_LONG).show();
                                        }})
                        .setNegativeButton(android.R.string.cancel, null)
                        .show(); 
                        return true;
                }
        });
        
        Preference updateInterval = (Preference) findPreference("update_interval");
        updateInterval.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				WakefulIntentService.scheduleAlarms(new com.nyaruka.androidrelay.AlarmListener(Long.parseLong(newValue.toString())), PreferencesActivity.this.getApplicationContext());
				Log.d(TAG, "Rescheduling alarms based on new update_interval value: " + newValue);
				return true;
			}
    });
    }
}
