package com.nyaruka.androidrelay;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class AlarmListener implements WakefulIntentService.AlarmListener {
	public static final long FREQUENCY = 30 * 1000;
	
	// 0 means we should look it up in the preferences
	private long m_interval = 0;
	
	public AlarmListener(long interval){
		m_interval = interval;
	}
	
	public AlarmListener(){
	}
	
	public long getMaxAge() {
		return AlarmManager.INTERVAL_HOUR;
	}

	public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		boolean process = prefs.getBoolean("process_incoming", false) || prefs.getBoolean("process_outgoing", false);
		
		if (m_interval == 0){
			String updateInterval = prefs.getString("update_interval", "" + FREQUENCY);
			m_interval = Long.parseLong(updateInterval);
		}
			
		if (m_interval > 0 && process){
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + m_interval, pi);
		}
	}

	public void sendWakefulWork(Context ctxt) {
		WakefulIntentService.sendWakefulWork(ctxt, CheckService.class);
	}
}
