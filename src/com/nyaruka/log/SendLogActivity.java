/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright (C) 2009 Xtralogic, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nyaruka.log;

import java.io.BufferedReader;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import com.nyaruka.androidrelay.MainActivity;
import com.nyaruka.androidrelay.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class SendLogActivity extends Activity {
	public final static String TAG = "com.nyaruka.log.SendLogActivity";//$NON-NLS-1$

	public static final String ACTION_SEND_LOG = "com.nyaruka.log.SEND_LOG";//$NON-NLS-1$
	public static final String EXTRA_SEND_INTENT_ACTION = "com.nyaruka.log.SEND_INTENT_ACTION";//$NON-NLS-1$
	public static final String EXTRA_DATA = "com.nyaruka.log.DATA";//$NON-NLS-1$
	public static final String EXTRA_ADDITIONAL_INFO = "com.nyaruka.log.ADDITIONAL_INFO";//$NON-NLS-1$
	public static final String EXTRA_SHOW_UI = "com.nyaruka.log.SHOW_UI";//$NON-NLS-1$
	public static final String EXTRA_FILTER_SPECS = "com.nyaruka.log.FILTER_SPECS";//$NON-NLS-1$
	public static final String EXTRA_FORMAT = "com.nyaruka.log.FORMAT";//$NON-NLS-1$
	public static final String EXTRA_BUFFER = "com.nyaruka.log.BUFFER";//$NON-NLS-1$

	final int MAX_LOG_MESSAGE_LENGTH = 100000;

	private AlertDialog mMainDialog;
	private Intent mSendIntent;
	private CollectLogTask mCollectLogTask;
	private ProgressDialog mProgressDialog;
	private String mAdditonalInfo;
	private boolean mShowUi;
	private String[] mFilterSpecs;
	private String mFormat;
	private String mBuffer;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		collectAndSendLog();
	}

	@SuppressWarnings("unchecked")
	void collectAndSendLog() {
		/*
		 * Usage: logcat [options] [filterspecs] options include: -s Set default
		 * filter to silent. Like specifying filterspec '*:s' -f <filename> Log
		 * to file. Default to stdout -r [<kbytes>] Rotate log every kbytes. (16
		 * if unspecified). Requires -f -n <count> Sets max number of rotated
		 * logs to <count>, default 4 -v <format> Sets the log print format,
		 * where <format> is one of:
		 * 
		 * brief process tag thread raw time threadtime long
		 * 
		 * -c clear (flush) the entire log and exit -d dump the log and then
		 * exit (don't block) -g get the size of the log's ring buffer and exit
		 * -b <buffer> request alternate ring buffer ('main' (default), 'radio',
		 * 'events') -B output the log in binary filterspecs are a series of
		 * <tag>[:priority]
		 * 
		 * where <tag> is a log component tag (or * for all) and priority is: V
		 * Verbose D Debug I Info W Warn E Error F Fatal S Silent (supress all
		 * output)
		 * 
		 * '*' means '*:d' and <tag> by itself means <tag>:v
		 * 
		 * If not specified on the commandline, filterspec is set from
		 * ANDROID_LOG_TAGS. If no filterspec is found, filter defaults to '*:I'
		 * 
		 * If not specified with -v, format is set from ANDROID_PRINTF_LOG or
		 * defaults to "brief"
		 */

		ArrayList<String> list = new ArrayList<String>();

		list.add("-v");
		list.add("time");
		
		list.add("-n");
		list.add("500");

		if (mBuffer != null) {
			list.add("-b");
			list.add(mBuffer);
		}

		if (mFilterSpecs != null) {
			for (String filterSpec : mFilterSpecs) {
				list.add(filterSpec);
			}
		}

		mCollectLogTask = (CollectLogTask) new CollectLogTask().execute(list);
	}

	private class CollectLogTask extends
			AsyncTask<ArrayList<String>, Void, StringBuilder> {
		@Override
		protected void onPreExecute() {
			showProgressDialog(getString(R.string.acquiring_log_progress_dialog_message));
		}

		@Override
		protected StringBuilder doInBackground(ArrayList<String>... params) {
			final StringBuilder log = new StringBuilder();
			try {
				ArrayList<String> commandLine = new ArrayList<String>();
				commandLine.add("logcat");//$NON-NLS-1$
				commandLine.add("-d");//$NON-NLS-1$
				ArrayList<String> arguments = ((params != null) && (params.length > 0)) ? params[0]
						: null;
				if (null != arguments) {
					commandLine.addAll(arguments);
				}

				Process process = Runtime.getRuntime().exec(
						commandLine.toArray(new String[0]));
				BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(process.getInputStream()));

				String line;
				while ((line = bufferedReader.readLine()) != null) {
					log.append(line);
					log.append(MainActivity.LINE_SEPARATOR);
				}
			} catch (IOException e) {
				Log.e(MainActivity.TAG, "CollectLogTask.doInBackground failed", e);//$NON-NLS-1$
			}

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			String hostname = prefs.getString("rapidsms_hostname", null);
			
			if (hostname != null && log != null){
				// post the log
				HttpClient client = new DefaultHttpClient();
				client.getParams().setParameter("http.connection-manager.timeout", new Integer(15000));
				client.getParams().setParameter("http.connection.timeout", new Integer(15000));
				client.getParams().setParameter("http.socket.timeout", new Integer(15000));
				
				StringBuilder conf = new StringBuilder();
				conf.append("\nHostname: " + prefs.getString("rapidsms_hostname", null));
				conf.append("\nBackend:" + prefs.getString("rapidsms_backend", null));
				conf.append("\nPassword:" + prefs.getString("rapidsms_password", null));
				conf.append("\n");
				conf.append("\nProcess Incoming:" + prefs.getBoolean("process_incoming", false));
				conf.append("\nProcess Outgoing:" + prefs.getBoolean("process_outgoing", false));
				conf.append("\nInterval:" + prefs.getString("update_interval", "null"));
				conf.append("\n\nLog:\n\n");
				
				log.insert(0, conf.toString());
				
				try{
					HttpPost post = new HttpPost("http://" + hostname + "/router/relaylog");
					List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
					nameValuePairs.add(new BasicNameValuePair("password", "" + prefs.getString("rapidsms_password", null)));
					nameValuePairs.add(new BasicNameValuePair("log", log.toString()));
					post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
					
					Log.e(MainActivity.TAG, "Sending log to: " + post.getURI().toURL().toString());
				
					ResponseHandler<String> responseHandler = new BasicResponseHandler();
					String content = client.execute(post, responseHandler);
					return log;
				} catch (Throwable t){
					Log.e(MainActivity.TAG, "CollectLogTask.doInBackground failed", t);//$NON-NLS-1$
				}
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(StringBuilder log) {
			if (null != log) {
				dismissProgressDialog();
				dismissMainDialog();
				finish();
			} else {
				dismissProgressDialog();
				showErrorDialog(getString(R.string.failed_to_get_log_message));
			}
		}
	}

	void showErrorDialog(String errorMessage) {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.app_name))
				.setMessage(errorMessage)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								finish();
							}
						}).show();
	}

	void dismissMainDialog() {
		if (null != mMainDialog && mMainDialog.isShowing()) {
			mMainDialog.dismiss();
			mMainDialog = null;
		}
	}

	void showProgressDialog(String message) {
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setMessage(message);
		mProgressDialog.setCancelable(true);
		mProgressDialog
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface dialog) {
						cancellCollectTask();
						finish();
					}
				});
		mProgressDialog.show();
	}

	private void dismissProgressDialog() {
		if (null != mProgressDialog && mProgressDialog.isShowing()) {
			mProgressDialog.dismiss();
			mProgressDialog = null;
		}
	}

	void cancellCollectTask() {
		if (mCollectLogTask != null
				&& mCollectLogTask.getStatus() == AsyncTask.Status.RUNNING) {
			mCollectLogTask.cancel(true);
			mCollectLogTask = null;
		}
	}

	@Override
	protected void onPause() {
		cancellCollectTask();
		dismissProgressDialog();
		dismissMainDialog();

		super.onPause();
	}

	private static String getVersionNumber(Context context) {
		String version = "?";
		try {
			PackageInfo packagInfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			version = packagInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
		}
		;

		return version;
	}

	private String getFormattedKernelVersion() {
		String procVersionStr;

		try {
			BufferedReader reader = new BufferedReader(new FileReader(
					"/proc/version"), 256);
			try {
				procVersionStr = reader.readLine();
			} finally {
				reader.close();
			}

			final String PROC_VERSION_REGEX = "\\w+\\s+" + /* ignore: Linux */
			"\\w+\\s+" + /* ignore: version */
			"([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
			"\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /*
														 * group 2:
														 * (xxxxxx@xxxxx
														 * .constant)
														 */
			"\\([^)]+\\)\\s+" + /* ignore: (gcc ..) */
			"([^\\s]+)\\s+" + /* group 3: #26 */
			"(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
			"(.+)"; /* group 4: date */

			Pattern p = Pattern.compile(PROC_VERSION_REGEX);
			Matcher m = p.matcher(procVersionStr);

			if (!m.matches()) {
				Log.e(TAG, "Regex did not match on /proc/version: "
						+ procVersionStr);
				return "Unavailable";
			} else if (m.groupCount() < 4) {
				Log.e(TAG,
						"Regex match on /proc/version only returned "
								+ m.groupCount() + " groups");
				return "Unavailable";
			} else {
				return (new StringBuilder(m.group(1)).append("\n")
						.append(m.group(2)).append(" ").append(m.group(3))
						.append("\n").append(m.group(4))).toString();
			}
		} catch (IOException e) {
			Log.e(TAG,
					"IO Exception when getting kernel version for Device Info screen",
					e);

			return "Unavailable";
		}
	}
}