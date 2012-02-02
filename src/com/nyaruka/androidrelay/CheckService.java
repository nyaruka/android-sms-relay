package com.nyaruka.androidrelay;

import java.net.SocketException;

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
		
		// grab the relayer service, seeing if it started
		RelayService relayer = RelayService.get();
		if (relayer == null){
			Log.d(TAG, "No RelayService started yet, awaiting.");
			return;
		}

		try{
			// do all the work of sending messages and checking for new ones
			doCheckWork(relayer);
		} catch (Throwable t){
			Log.d(TAG, "Error running check service.", t);
		}
				
		// reschedule ourselves
		schedule(this.getApplicationContext());
	}
	
	protected void doCheckWork(RelayService relayer){
		try{
			relayer.resendErroredSMS();
		} catch (Throwable t){
			Log.d(TAG, "Error resending SMSes.", t);
		}
				
		try{
			relayer.sendPendingMessagesToServer();
		} catch (Throwable t){
			try{
				Log.d(TAG, "Error resending to server, toggling connection", t);
				relayer.toggleConnection();
				relayer.sendPendingMessagesToServer();
			} catch (Throwable tt){
				Log.d(TAG, "Error sending messages to server", t);
			}
		}

		try{
			relayer.markDeliveriesOnServer();
		} catch (Throwable t){
			if (!relayer.isConnectionToggled()){
				try{
					Log.d(TAG, "Error marking deliveries on the server, toggling connection", t);
					relayer.toggleConnection();
					relayer.sendPendingMessagesToServer();
				} catch (Throwable tt){
					Log.d(TAG, "Error marking deliveries on the server", t);
				}
			} else {
				Log.d(TAG, "Error marking deliveries on the server", t);
			}
		}
				
		try{
			relayer.checkOutbox();
		} catch (Throwable t){
			if (!relayer.isConnectionToggled()){
				try{
					Log.d(TAG, "Error checking outbox, toggling connection", t);
					relayer.toggleConnection();
					relayer.sendPendingMessagesToServer();
				} catch (Throwable tt){
					Log.d(TAG, "Error checking outbox", t);
				}
			} else {
				Log.d(TAG, "Error checking outbox", t);
			}
		}
				
		try {
			relayer.trimMessages();					
		} catch (Throwable t){
			Log.d(TAG, "Error trimming message", t);
		}
				
		// restore our connection
		relayer.restoreConnection();
	}

	public static void schedule(Context context){
		WakefulIntentService.scheduleAlarms(new com.nyaruka.androidrelay.AlarmListener(), context);
	}
}