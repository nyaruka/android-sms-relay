package com.nyaruka.androidrelay.data;

import java.util.Date;

public class TextMessage {

	/** the message is incoming */
	public static final char INCOMING = 'I';
	
	/** the message is outgoing */
	public static final char OUTGOING = 'O';
	
	/** indicates the message is received, but needs to be handled */
	public static final char RECEIVED = 'R';
	
	/** the message has been handled, that is delivered to the server */
	public static final char HANDLED = 'H';
	
	/** we ignored this message */
	public static final char IGNORED = 'I';
	
	/** we've told android to send it, but haven't got a confirmation that it has happened yet */
	public static final char QUEUED = 'Q';
	
	/** we tried to send it (either to the server or to android) but got an error */
	public static final char ERRORED = 'E';	
	
	/** android told us it's on its way */
	public static final char SENT = 'S';
	
	/** the handling for this message is complete, the server was notified it was sent */
	public static final char DONE = 'D';

	public TextMessage(){}
	
	public TextMessage(String number, String text, long serverId){
		this.number = number;
		this.text = text;
		this.created = new Date();
		this.direction = OUTGOING;
		this.status = QUEUED;
		this.serverId = serverId;
	}
	
	public TextMessage respond(String number, String text){
		TextMessage msg = new TextMessage();
		msg.number = number;
		msg.text = text;
		msg.created = new Date();
		msg.direction = OUTGOING;
		msg.status = QUEUED;
		return msg;
	}
	
	public String getStatusText(){
		switch (status){
		case RECEIVED:
			return "Received";
		case HANDLED:
			return "Complete";
		case IGNORED:
			return "Ignored";
		case QUEUED:
			return "Queued";
		case ERRORED:
			if (direction == OUTGOING){ 
				return "Send Error";
			} else {
				return "Server Error";
			}
		case SENT:
			return "Sent";
		case DONE:
			return "Complete";
		default:
			return "--";
		}
	}
	
	public boolean equals(Object other){
		if (other instanceof TextMessage){
			return ((TextMessage)other).id == this.id;
		} else{
			return false;
		}
	}
	
	public long id;
	public String number;
	public String text;
	public String error;
	public Date created;
	public char direction;
	public char status;
	public long serverId;
}
