package com.nyaruka.androidrelay;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.view.MenuInflater;
import android.widget.Toast;

import com.actionbarsherlock.R;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.androidrelay.data.TextMessage;

public class MainActivity extends FragmentActivity {

	public static final String TAG = "FragmentActivity";
    public final static String LINE_SEPARATOR = System.getProperty("line.separator");//$NON-NLS-1$
	
	private static MainActivity s_this;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		s_this = this;
	}
	
	public static boolean alive(){
		return s_this != null;
	}
	
	public static MessageListFragment getMessageList(){
		return (MessageListFragment) s_this.getSupportFragmentManager().findFragmentById(R.id.message_list);
	}
	
	public static void updateMessage(TextMessage message){
		if (alive() && getMessageList() != null){
			getMessageList().updateMessage(message);
		}
	}
	
	public static void clearMessages(){
		if (alive() && getMessageList() != null){
			getMessageList().clearMessages();
		}
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    	case R.id.settings:
	    		Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
	    		startActivity(settingsActivity);
	    		return true;
	    		 
	    	case R.id.refresh:
	    	    WakefulIntentService.scheduleAlarms(new com.nyaruka.androidrelay.AlarmListener(1), getApplicationContext());
	    	    Toast.makeText(MainActivity.this, "Syncing messages", Toast.LENGTH_LONG).show();
	    		return true;
	    }
	    return false;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main, menu);
	    return true;
	}
	
	@Override
	protected void onResume() {
		super.onResume();		
		startService(new Intent(this, RelayService.class));
	}
	

}