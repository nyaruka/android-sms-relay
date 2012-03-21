package com.nyaruka.androidrelay;

import com.nyaruka.androidrelay.data.TextMessageHelper;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class AndroidRelay extends Application {

	private TextMessageHelper m_helper;
	
    @Override
    public void onCreate() {
        super.onCreate();
        m_helper = new TextMessageHelper(this);
    }
    
    public static void clearMessages(Context context){
    	getHelper(context).clearMessages();
    	MainActivity.clearMessages();
    }
        
    public static TextMessageHelper getHelper(Context context){
    	return ((AndroidRelay)context.getApplicationContext()).m_helper;
    }
    
	public static String getVersionNumber(Context context) {
		String version = "?";
		try {
			PackageInfo packagInfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			version = packagInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
		};

		return version;
	}
}
