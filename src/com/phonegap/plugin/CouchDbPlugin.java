package com.phonegap.plugin;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import com.couchbase.libcouch.CouchDB;
import com.couchbase.libcouch.ICouchClient;
import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;
import com.phonegap.api.PluginResult.Status;
import com.photoshare.AndCouch;

public class CouchDbPlugin extends Plugin {
	
	protected static final String TAG = "CouchDbPlugin";
	public static final String PREFS_NAME = "CouchDbPrefs";
	private ServiceConnection couchServiceConnection;
	private ProgressDialog installProgress;
	private String callbackId = null;
	
	private String getSyncPoint() {
		SharedPreferences settings = this.ctx.getSharedPreferences(PREFS_NAME, 0);
		String syncPoint = settings.getString("syncpoint", "http://couchbase.ic.ht/photo-share");
		return syncPoint;
	}

	@Override
	public PluginResult execute(String action, JSONArray data, String callbackId) {
		Log.d(TAG, "CouchDbPlugin called with "+action);
		PluginResult result = null;
		this.callbackId = callbackId;
		if(action.equals("start")) {
			startCouch();
			result = new PluginResult(Status.NO_RESULT);
			result.setKeepCallback(true);
		} else if(action.equals("getSyncPoint")) {
			result = new PluginResult(Status.OK, getSyncPoint());
		}
		return result;
	}
	
	private void couchError() {
		AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
		builder.setMessage("Error")
				.setPositiveButton("Try Again?",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								startCouch();
							}
						})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								ctx.moveTaskToBack(true);
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
		Log.e(TAG, "ERROR");
	}
	
	/*
	* Will check for the existence of the photoshare database and create it if it doesn't exist,
	*/
	private void ensureDoc(String dbName, String url) {

		try {
			String data = readAsset(ctx.getAssets(), dbName + ".json");
			String ddocUrl = url + dbName + "/_design/" + dbName;
			Log.d(TAG, "ddocUrl: "+ddocUrl);

			AndCouch req = AndCouch.get(ddocUrl);

			if (req.status == 404) {
				AndCouch.put(url + dbName, null);
				AndCouch.put(ddocUrl, data);
			}
		} catch (JSONException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		} catch(IOException e) {
			Log.e(TAG, e.getMessage());
			e.printStackTrace();
		}
	}
	/*
	 * CouchClient implementation
	 */
	private final ICouchClient mCallback = new ICouchClient.Stub() {
		@Override
		public void couchStarted(String host, int port) {
			
			if (installProgress != null) {
				installProgress.dismiss();
			}

			String url = "http://" + host + ":" + Integer.toString(port) + "/";
			ensureDoc("photoshare", url);
			String location = url + "photoshare/_design/photoshare/index.html";
			try {
				JSONObject startObj = new JSONObject();
				startObj.put("message", "Couch Started!");
				startObj.put("location", location);
				success(new PluginResult(Status.OK, startObj), callbackId);
				Log.d(TAG, "Couch Started!");
			} catch(JSONException e) {
				Log.e(TAG, e.getMessage());
			}
		}

		@Override
		public void installing(int completed, int total) {
			ensureProgressDialog();
			installProgress.setTitle("Initialising CouchDB");
			installProgress.setProgress(completed);
			installProgress.setMax(total);
			Log.d(TAG, "CouchDb Installing "+completed+"/"+total);
		}

		@Override
		public void exit(String error) {
			Log.v(TAG, error);
			couchError();
		}
	};
	
	private void ensureProgressDialog() {
		if (installProgress == null) {
			installProgress = new ProgressDialog(ctx);
			installProgress.setTitle(" ");
			installProgress.setCancelable(false);
			installProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			installProgress.show();
		}
	}
	
	private void startCouch() {
		Log.d(TAG, "Starting CouchDB");
		couchServiceConnection = CouchDB.getService(this.ctx.getBaseContext(), mCallback);
	}
	
	public static String readAsset(AssetManager assets, String path) throws IOException {
		InputStream is = assets.open(path);
		int size = is.available();
		byte[] buffer = new byte[size];
		is.read(buffer);
		is.close();
		return new String(buffer);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		try {
			this.ctx.unbindService(couchServiceConnection);
		} catch (IllegalArgumentException e) {
		}
	}

}
