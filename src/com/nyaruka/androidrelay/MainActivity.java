package com.nyaruka.androidrelay;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.android.actionbarcompat.ActionBarActivity;
import com.nyaruka.androidrelay.data.TextMessage;

public class MainActivity extends ActionBarActivity {

	public static final String TAG = MainActivity.class.getSimpleName();
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
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if ( keyCode == KeyEvent.KEYCODE_MENU ) {
			Intent settingsActivity = new Intent(getBaseContext(), SettingsActivity.class);
			startActivity(settingsActivity);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
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
	    return super.onOptionsItemSelected(item);
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
	    return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	protected void onResume() {
		super.onResume();		
		startService(new Intent(this, RelayService.class));
	}
	

}