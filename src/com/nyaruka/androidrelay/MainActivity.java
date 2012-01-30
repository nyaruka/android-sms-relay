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
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Toast;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.nyaruka.androidrelay.data.TextMessage;

public class MainActivity extends ListActivity {

	public static final String TAG = "MainActivity";
	
	private TextMessageAdapter msgAdapter;
	private static MainActivity s_this;
	private static Vector<TextMessage> s_updateMessages = new Vector<TextMessage>();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		
		List<TextMessage> msgs = AndroidRelay.getHelper(this).getAllMessages();
		msgAdapter = new TextMessageAdapter(this, R.layout.message, msgs);
		
		setListAdapter(msgAdapter);

		ListView lv = getListView();
		lv.setBackgroundColor(Color.WHITE);
		lv.setCacheColorHint(Color.WHITE);
		lv.setTextFilterEnabled(true);
		
		registerForContextMenu(lv);
		
		s_this = this;
	}
	
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
	
    // update an existing message on our UI list
    public static void updateMessage(TextMessage textMessage){
		Message msg = new Message();
		if (s_this != null){
			s_updateMessages.add(textMessage);
			s_this.updateHandler.sendMessage(msg);
		}
    }
    
    public static void clearMessages(){
    	s_this.clearHandler.sendMessage(new Message());
    }
    
    private Handler clearHandler = new Handler() {
        public void handleMessage(Message msg) {
        	msgAdapter.clear();
        }
    };
    
    private Handler updateHandler = new Handler() {
        public void handleMessage(Message msg) {
       	 if (msg == null){ return; }
        	 
        	 try{
        		 while (s_updateMessages.size() > 0){
        			 TextMessage textMessage = s_updateMessages.remove(0);
        			 
        			 int pos = msgAdapter.getPosition(textMessage);
        			 if (pos >= 0){
        				 msgAdapter.remove(textMessage); 
        			 } else {
        				 pos = 0;
        			 }
        			 msgAdapter.insert(textMessage, pos);
        			 while (msgAdapter.getCount() > 100){
        				 msgAdapter.remove(msgAdapter.getItem(100));
        			 }
        	 
        			 msgAdapter.notifyDataSetChanged();
        		 }
        	 } catch (Throwable t){
        		 Log.d(TAG, "Error updating UI", t);
        	 }
         }
    };
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    	case R.id.settings:
	    		 startActivity(new Intent(this, PreferencesActivity.class));
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
	
	private class TextMessageAdapter extends ArrayAdapter<TextMessage> {
		private List<TextMessage> objects = null;

		public TextMessageAdapter(Context context, int textviewid, List<TextMessage> objects) {
			super(context, textviewid, objects);
			this.objects = objects;
		}

		@Override
		public int getCount() {
			return ((null != objects) ? objects.size() : 0);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public TextMessage getItem(int position) {
			return ((null != objects) ? objects.get(position) : null);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;

			if (null == view) {
				LayoutInflater vi = (LayoutInflater) MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = vi.inflate(R.layout.message, null);
			}

			TextMessage msg = objects.get(position);

			if (msg != null){
				TextView textview = (TextView) view.findViewById(R.id.messageNumber);
				if (msg.direction == 'I'){
					textview.setText("From: " + msg.number);
				} else {
					textview.setText("To: " + msg.number);						
				}
				
				textview = (TextView) view.findViewById(R.id.messageText);
				textview.setText(msg.text);
				
				textview = (TextView) view.findViewById(R.id.messageDate);
				textview.setText(msg.created.toString());
				
				textview = (TextView) view.findViewById(R.id.messageStatus);
				textview.setText(msg.getStatusText());
				
				if (msg.status == TextMessage.ERRORED){
					textview.setTextColor(Color.RED);
				} else if (msg.status == TextMessage.QUEUED || msg.status == TextMessage.IGNORED || msg.status == TextMessage.RECEIVED){
					textview.setTextColor(Color.GRAY);
				} else {
					textview.setTextColor(Color.GREEN);					
				}
									
				textview = (TextView) view.findViewById(R.id.log);
				textview.setTextColor(Color.RED);
				
				if (msg.error != null){
					textview.setText(msg.error);
				} else {
					textview.setText("");
				}
			}

			return view;
		}
	}
}