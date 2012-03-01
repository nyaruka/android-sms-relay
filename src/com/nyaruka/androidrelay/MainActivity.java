package com.nyaruka.androidrelay;

import java.util.List;

import java.util.Vector;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Toast;

import com.actionbarsherlock.R;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.androidrelay.data.TextMessage;

public class MainActivity extends FragmentActivity {

	public static final String TAG = "FragmentActivity";
	
	private static MainActivity s_this;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		s_this = this;
	}

	/*
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	  super.onCreateContextMenu(menu, v, menuInfo);
	  MenuInflater inflater = getMenuInflater();
	  inflater.inflate(R.menu.message_context, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
	  AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	  switch (item.getItemId()) {
	  case R.id.msg_reprocess:
		  TextMessage msg = msgAdapter.getItem((int) info.id);
		  if (msg.direction == TextMessage.INCOMING){
			  msg.status = TextMessage.RECEIVED;
			  AndroidRelay.getHelper(this).updateMessage(msg);
			  updateMessage(msg);
			  CheckService.schedule(this.getApplicationContext());
		  }
		  
		  return true;
	  default:
		  return super.onContextItemSelected(item);
	  }
	}
	*/
	
	public static boolean alive(){
		return s_this != null;
	}
	
	public static MessageListFragment getMessageList(){
		return (MessageListFragment) s_this.getSupportFragmentManager().findFragmentById(R.id.message_list);
	}
	
	public static void updateMessage(TextMessage message){
		if (alive()){
			getMessageList().updateMessage(message);
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