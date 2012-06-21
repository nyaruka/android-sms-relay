package com.nyaruka.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.util.Log;

import com.nyaruka.androidrelay.AndroidRelay;
import com.nyaruka.androidrelay.MainActivity;

public class LogCollector {
	public final static String TAG = AndroidRelay.TAG;
	public static final String EXTRA_FORMAT = "com.nyaruka.log.FORMAT";//$NON-NLS-1$
	public static final String EXTRA_BUFFER = "com.nyaruka.log.BUFFER";//$NON-NLS-1$

	final int MAX_LOG_MESSAGE_LENGTH = 100000;

	public static String collectLog(){
		final StringBuilder log = new StringBuilder();
		try {
			ArrayList<String> commandLine = new ArrayList<String>();
			commandLine.add("logcat");//$NON-NLS-1$
			commandLine.add("-d");//$NON-NLS-1$

			commandLine.add("-v");
			commandLine.add("time");

			commandLine.add("-t");
			commandLine.add("2500");

			commandLine.add(AndroidRelay.TAG + ":V");
			commandLine.add("*:S");

			Process process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			String line;
			while ((line = bufferedReader.readLine()) != null) {
				log.append(line);
				log.append(MainActivity.LINE_SEPARATOR);
			}
		} catch (Throwable t){
			Log.e(TAG, "CollectLogTask.doInBackground failed", t);
		}
		
		return log.toString();
	}
}