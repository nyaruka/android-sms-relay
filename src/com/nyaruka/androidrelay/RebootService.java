package com.nyaruka.androidrelay;

import android.app.IntentService;
import android.content.Intent;

public class RebootService extends IntentService {

	public RebootService() {
		super(RebootService.class.getName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		try {
			Runtime.getRuntime().exec(new String[]{ "su", "-c", "reboot" });
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
}
