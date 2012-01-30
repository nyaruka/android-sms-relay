package com.nyaruka.androidrelay;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class CheckService extends WakefulIntentService {
	public static final String TAG = CheckService.class.getSimpleName();
	
	public CheckService() {
		super(CheckService.class.getName());
	}

	@Override
	protected void doWakefulWork(Intent intent) {
		Log.d(TAG, "==Check service running");
		
		// make sure our SMS modem is hooked up
		if (!BootStrapper.checkService(this.getApplicationContext())){
			Log.d(TAG, "RelayService not started yet, waiting.");
			schedule(this.getApplicationContext());
			return;
		}
		
		try{
			RelayService relayer = RelayService.get();
			if (relayer != null){
				// check our connection
				relayer.checkConnection();
				
				try{
					relayer.resendErroredSMS();
				} catch (Throwable t){
					Log.d(TAG, "Error resending SMSes.", t);
				}
				
				try{
					relayer.sendPendingMessagesToServer();
				} catch (Throwable t){
					Log.d(TAG, "Error resending to server.", t);
				}

				try{
					relayer.markDeliveriesOnServer();
				} catch (Throwable t){
					Log.d(TAG, "Error marking deliveries on the server", t);
				}
				
				try{
					relayer.checkOutbox();
				} catch (Throwable t){
					Log.d(TAG, "Error checking outbox", t);
				}
				
				try {
					relayer.trimMessages();					
				} catch (Throwable t){
					Log.d(TAG, "Error trimming message", t);
				}
				
				// restore our connection
				relayer.restoreConnection();
			} else {
				Log.d(TAG, "No RelayService started yet.");
			}
		} catch (Exception e){
			Log.d(TAG, "Error running check service.", e);
		}
		
		// reschedule ourselves
		schedule(this.getApplicationContext());
	}

	public static void schedule(Context context){
		WakefulIntentService.scheduleAlarms(new com.nyaruka.androidrelay.AlarmListener(), context);
	}
}