package com.nyaruka.androidrelay.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.*;

public class TextMessageHelper extends SQLiteOpenHelper {
	
	private static final String DATABASE_NAME = "sms_relay.db";
	private static final int DATABASE_VERSION = 1;
	
	public static final String[] TEXT_MESSAGE_COLS = new String[] { "id", "number", "text", "created", "direction", "status", "serverId" };
	
	public TextMessageHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase sqLiteDatabase) {
		String createMessagesTable = "CREATE TABLE `messages` " + "("
				+ "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ "`number` VARCHAR, " 
				+ "`text` VARCHAR, " 
				+ "`created` DATETIME, " 
				+ "`direction` CHAR(1), " 
				+ "`status` CHAR(1), " 
				+ "`serverId` INTEGER " 
				+ ") ";
		sqLiteDatabase.execSQL(createMessagesTable);
	}

	@Override
	public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
		sqLiteDatabase.execSQL("drop table messages");
		onCreate(sqLiteDatabase);
	}
	
	public void clearMessages(){
		final SQLiteDatabase writableDatabase = getWritableDatabase();
		writableDatabase.delete("messages", null, null);
	}
	
	public void trimMessages(){
		final SQLiteDatabase writableDatabase = getWritableDatabase();
		writableDatabase.execSQL("DELETE FROM `messages` WHERE id NOT IN (SELECT ID FROM `messages` ORDER BY `id` DESC LIMIT 100) and (`status` = 'H' OR `status` = 'D')");		
	}

	public void createMessage(TextMessage message) {
		final SQLiteDatabase writableDatabase = getWritableDatabase();
		final ContentValues values = new ContentValues();

		values.put("number", message.number);
		values.put("text", message.text);
		values.put("created", message.created.getTime());
		values.put("direction", "" + message.direction);
		values.put("status", "" + message.status);
		values.put("serverId", message.serverId);

		message.id = writableDatabase.insertOrThrow("messages", null, values);
	}

	public void updateMessage(TextMessage message){
		final SQLiteDatabase writableDatabase = getWritableDatabase();
		try {
			final ContentValues values = new ContentValues();
			
			values.put("number", message.number);
			values.put("text", message.text);
			values.put("created", message.created.getTime());
			values.put("direction", "" + message.direction);
			values.put("status", "" + message.status);
			values.put("serverId", message.serverId);

			writableDatabase.update("messages", values, "id = ?", new String[] { "" + message.id });
		} finally {
		}
	}
	
	public List<TextMessage> getAllMessages(){
		final SQLiteDatabase readableDatabase = getReadableDatabase();
		final Cursor cursor = readableDatabase.query("messages",
			TEXT_MESSAGE_COLS, null, null, null, null, "id DESC", null);

		return listFromCursor(cursor);
	}
	
	public List<TextMessage> withStatus(Context context, char direction, char status){
		final SQLiteDatabase readableDatabase = getReadableDatabase();
		final Cursor cursor = readableDatabase.query("messages", TEXT_MESSAGE_COLS,
				"direction = ? AND status = ?", new String[] { "" + direction, "" + status}, null, null, "id DESC", "100");
				
		return listFromCursor(cursor);
	}
	
	public List<TextMessage> erroredOutgoing(Context context){
		final SQLiteDatabase readableDatabase = getReadableDatabase();
		final Cursor cursor = readableDatabase.query("messages", TEXT_MESSAGE_COLS,
				"direction = 'O' AND status = 'E'", null, null, null, "id DESC", "100");
				
		return listFromCursor(cursor);
	}
	
	public TextMessage withServerId(Context context, Long serverId){
		final SQLiteDatabase readableDatabase = getReadableDatabase();
		
		final Cursor cursor = readableDatabase.query("messages", TEXT_MESSAGE_COLS,
				"serverId = ?", new String[] { serverId.toString() }, null, null, "id DESC", null);
		
		return firstFromCursor(cursor);
	}
	
	public TextMessage withId(Long id) {
		final SQLiteDatabase readableDatabase = getReadableDatabase();
		
		final Cursor cursor = readableDatabase.query("messages", TEXT_MESSAGE_COLS,
				"id = ?", new String[] { id.toString() }, null, null, null, null);
		
		return firstFromCursor(cursor);
	}

	public List<TextMessage> listFromCursor(Cursor messageCursor){
		List<TextMessage> messages = new ArrayList<TextMessage>();
		
		try {
			while (messageCursor.moveToNext()) {
				final TextMessage message = messageFromCursor(messageCursor);
				messages.add(message);
			}
		} finally {
			messageCursor.close();
		}
		
		return messages;
	}
	
	public TextMessage firstFromCursor(Cursor cursor){
		try {
			if (cursor.moveToNext()){
				return messageFromCursor(cursor);
			} else {
				return null;
			}
		} finally {
			cursor.close();
		}
	}

	private TextMessage messageFromCursor(Cursor cursor) {
		final TextMessage message = new TextMessage();
		
		// "id", "number", "text", "created", "direction", "status", "serverId" };
		message.id = cursor.getLong(0);
		message.number = cursor.getString(1);
		message.text = cursor.getString(2);
		message.created = new Date(cursor.getLong(3));
		message.direction = cursor.getString(4).charAt(0);
		message.status = cursor.getString(5).charAt(0);
		message.serverId = cursor.getLong(6);
				
		return message;
	}

}