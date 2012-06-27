package com.nyaruka.androidrelay;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.nyaruka.androidrelay.data.TextMessageHelper;

public class AndroidRelay extends Application {

	public static final String TAG = "AndroidRelay";
	
	private TextMessageHelper m_helper;
	private PhoneState m_phoneState;
	
    @Override
    public void onCreate() {
        super.onCreate();
        
        // migrate old preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.contains("rapidsms_hostname")){
        	Editor editor = prefs.edit();
        	editor.putString("router_hostname", prefs.getString("rapidsms_hostname", null));
        	editor.putString("router_password", prefs.getString("rapidsms_password", null));
        	editor.putString("router_backend", prefs.getString("rapidsms_backend", null));
        	
        	editor.remove("rapidsms_hostname");
        	editor.remove("rapidsms_password");
        	editor.remove("rapidsms_backend");
        	
        	editor.commit();
        }
        
        m_helper = new TextMessageHelper(this);
        m_phoneState = new PhoneState();
        
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(m_phoneState, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }
    
    public static void clearMessages(Context context){
    	getHelper(context).clearMessages();
    	MainActivity.clearMessages();
    }
        
    public static TextMessageHelper getHelper(Context context){
    	return ((AndroidRelay)context.getApplicationContext()).m_helper;
    }
    
    public static PhoneState getPhoneState(Context context){
    	return ((AndroidRelay)context.getApplicationContext()).m_phoneState;
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
	
	static class PhoneState extends PhoneStateListener {
		public int state = 0;
		public int strength = 0;
		
		public void onServiceStateChanged(ServiceState serviceState){
			Log.d(TAG, "Service State changed to: " + serviceState.getState() + " -- " + serviceState.getOperatorAlphaLong());
			state = serviceState.getState();
		}
		
		public void	onSignalStrengthsChanged(SignalStrength signalStrength){
			//Log.d(TAG, "Signal strength changed to: " + signalStrength.getGsmSignalStrength());
			strength = signalStrength.getGsmSignalStrength();
		}
	}
}
