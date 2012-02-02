package com.nyaruka.androidrelay;

import com.nyaruka.androidrelay.data.TextMessageHelper;

import android.app.Application;
import android.content.Context;

public class AndroidRelay extends Application {

	private TextMessageHelper m_helper;
	
    @Override
    public void onCreate() {
        super.onCreate();
        m_helper = new TextMessageHelper(this);
    }
    
    public static void clearMessages(Context context){
    	getHelper(context).clearMessages();
    	MainActivity.getMessageList().clearMessages();
    }
        
    public static TextMessageHelper getHelper(Context context){
    	return ((AndroidRelay)context.getApplicationContext()).m_helper;
    }
}
