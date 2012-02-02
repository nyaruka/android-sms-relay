package com.nyaruka.androidrelay;

import java.util.List;
import java.util.Vector;

import com.actionbarsherlock.R;
import com.nyaruka.androidrelay.data.TextMessage;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class MessageListFragment extends ListFragment {
	public static final String TAG = "MessageListFragment";
	
	private TextMessageAdapter m_adapter;
	private static Vector<TextMessage> s_updateMessages = new Vector<TextMessage>();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onActivityCreated(Bundle savedState) {
		super.onActivityCreated(savedState);
		
		//View view = super.onCreateView(inflater, container, savedInstanceState);
		List<TextMessage> msgs = AndroidRelay.getHelper(getActivity()).getAllMessages();
		m_adapter = new TextMessageAdapter(getActivity(), R.layout.message, msgs);
	
		setListAdapter(m_adapter);
		ListView lv = getListView();
		lv.setBackgroundColor(Color.WHITE);
		lv.setCacheColorHint(Color.WHITE);
	}
	
	private Handler m_clearHandler = new Handler() {
        public void handleMessage(Message msg) {
        	m_adapter.clear();
        }
    };
    
    public void clearMessages(){
    	m_clearHandler.sendMessage(new Message());
    }
    
    private Handler m_updateHandler = new Handler() {
        public void handleMessage(Message msg) {
       	 if (msg == null){ return; }
        	 
        	 try{
        		 while (s_updateMessages.size() > 0){
        			 TextMessage textMessage = s_updateMessages.remove(0);
        		
        			 int pos = m_adapter.getPosition(textMessage);
        			 if (pos >= 0){
        				 m_adapter.remove(textMessage); 
        			 } else {
        				 pos = 0;
        			 }
        			 m_adapter.insert(textMessage, pos);
        			 while (m_adapter.getCount() > 100){
        				 m_adapter.remove(m_adapter.getItem(100));
        			 }
        	 
        			 m_adapter.notifyDataSetChanged();
        		 }
        	 } catch (Throwable t){
        		 Log.d(TAG, "Error updating UI", t);
        	 }
         }
    };
    
    public void updateMessage(TextMessage textMessage){
    	Message msg = new Message();
    	s_updateMessages.add(textMessage);
    	m_updateHandler.sendMessage(msg);
    }

	static int SUCCESS = Color.rgb(38,183,12);
	static int ERROR = Color.rgb(210, 0, 0);
    
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
				LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
					textview.setTextColor(ERROR);
				} else if (msg.status == TextMessage.QUEUED || msg.status == TextMessage.IGNORED || 
						   msg.status == TextMessage.RECEIVED || msg.status == TextMessage.SENT){
					textview.setTextColor(Color.GRAY);
				} else {
					textview.setTextColor(SUCCESS);
				}
									
				textview = (TextView) view.findViewById(R.id.log);
				textview.setTextColor(ERROR);
				
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
