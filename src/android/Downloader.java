package hr.integrator.cordova.plugins.downloader;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.database.Cursor;
import android.net.Uri;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.webkit.CookieManager;

import android.database.Cursor;

import android.util.Log;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.Manifest;

import java.util.HashMap;
import java.util.Map;

import java.io.File;

import android.os.Environment;
import android.os.Build;

public class Downloader extends CordovaPlugin {

  private static final String LOG_TAG = "Downloader";

  public static final String WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
  public static final int DOWNLOAD_ACTION_PERMISSION_REQ_CODE = 1;

  public static final String PERMISSION_DENIED_ERROR = "PERMISSION DENIED";

  DownloadManager downloadManager;
  BroadcastReceiver receiver;

  private CallbackContext downloadReceiverCallbackContext = null;
  private JSONArray executeArgs;
  long downloadId = 0;
  private CordovaInterface cordova;

  @Override
  public void initialize(final CordovaInterface cordova, final CordovaWebView webView) {
      super.initialize(cordova, webView);
      this.cordova = cordova;
	  downloadManager = (DownloadManager) cordova.getActivity()
                .getApplication()
                .getApplicationContext()
                .getSystemService(Context.DOWNLOAD_SERVICE);
  }
  /**
  * Checks if android version is lower then android 29. If so, ask user for write permission.
  * Triggers download method if file don't exist on specific path.
  **/
  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException  {

      executeArgs = args;

      if (downloadReceiverCallbackContext != null) {
          removeDownloadReceiver();
      }
      downloadReceiverCallbackContext = callbackContext;

      if(action.equals("androidTargetSDK")){
          if(callbackContext != null){
                  PluginResult result = new PluginResult(PluginResult.Status.OK, this.cordova.getActivity()
                    .getApplication()
                    .getApplicationContext()
                    .getApplicationInfo().targetSdkVersion);
                  result.setKeepCallback(true);
                  callbackContext.sendPluginResult(result);
              }
      }

      if(action.equals("download")){
             if(this.cordova.getActivity()
                                .getApplication()
                                .getApplicationContext()
                                .getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.Q ){
                     checkIfFileExist(args, callbackContext);
             }
             else {
                if(cordova.hasPermission(WRITE_EXTERNAL_STORAGE)) {
                    checkIfFileExist(args, callbackContext);
                }
                else {
                    cordova.requestPermission(this, DOWNLOAD_ACTION_PERMISSION_REQ_CODE, WRITE_EXTERNAL_STORAGE);
                }
             }
      }
      else{
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
          return false;
      }

      return true;
  }

  /**
  * Check if file is already saved on specific location
  **/
  private void checkIfFileExist(JSONArray args, final CallbackContext callbackContext) {
    try {
        JSONObject params = args.getJSONObject(0).getJSONObject("destinationInExternalPublicDir");
         if(!fileExist(params.optString("subPath"))) {
                  download(args.getJSONObject(0), callbackContext);
              }
              else {
                  downloadReceiverCallbackContext.error(args.getJSONObject(0).optString("title") + ": file already exist");
                  Log.d("downloadInfo", "file already exist");
              }
    } catch (JSONException e) {
        e.printStackTrace();
    }
  }

  private boolean fileExist(String url) {
   File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/" + url);
   if (file == null) {
        throw new IllegalStateException("Failed to get external storage public directory");
   }
   return file.isFile();
  /*
      this part of code checks where file was saved using download manager. For now we don't need this functionality but
      lets leave it here

        DownloadManager.Query query = new DownloadManager.Query();
        Cursor cursor = downloadManager.query(query);
        if(cursor.moveToFirst()){
            String downloadedTo = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
            String[] parsed = downloadedTo.split("/");
             Log.d("test", parsed[parsed.length - 1] + "url" + url);
             if(parsed != null) {
               return parsed[parsed.length - 1].equals(url);
             }
        }
        return false;
   */
    }

  protected boolean download(JSONObject obj, CallbackContext callbackContext) throws JSONException {

    DownloadManager.Request request = deserialiseRequest(obj);

    IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

    webView.getContext().registerReceiver(downloadReceiver, intentFilter);

    this.downloadId = downloadManager.enqueue(request);


    // Don't return any result now, since status results will be sent when events come in from broadcast receiver
    PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
    pluginResult.setKeepCallback(true);
    callbackContext.sendPluginResult(pluginResult);
    return true;
  }

  private void removeDownloadReceiver(){
	  try {
		  webView.getContext().unregisterReceiver(downloadReceiver);
		}
		catch (Exception e) {
			LOG.e(LOG_TAG, "Error unregistering download receiver: " + e.getMessage(), e);
		}
  }



  public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
    for(int r:grantResults)
    {
        if(r == PackageManager.PERMISSION_DENIED)
        {
            downloadReceiverCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
            return;
        }
    }
    switch(requestCode)
    {
        case DOWNLOAD_ACTION_PERMISSION_REQ_CODE:
            download(executeArgs.getJSONObject(0), downloadReceiverCallbackContext);
            break;
    }
  }

  private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {

      long referenceId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

      DownloadManager.Query query = new DownloadManager.Query();
      query.setFilterById(referenceId);
      Cursor cursor = downloadManager.query(query);

      if(cursor.moveToFirst()){
        String downloadedTo = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
        int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        int reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));

        switch(status){
          case DownloadManager.STATUS_SUCCESSFUL:
            sendDownloadResult(downloadedTo);
            break;
          case DownloadManager.STATUS_FAILED:
            downloadReceiverCallbackContext.error(reason);
            break;
          case DownloadManager.STATUS_PAUSED:
          case DownloadManager.STATUS_PENDING:
          case DownloadManager.STATUS_RUNNING:
          default:
            break;
        }
      }
    }
  };

  private void sendDownloadResult(String locationUri) {
      if(this.downloadReceiverCallbackContext != null){
          PluginResult result = new PluginResult(PluginResult.Status.OK, locationUri);
          result.setKeepCallback(true);
          this.downloadReceiverCallbackContext.sendPluginResult(result);
      }
  }
  /**
  * Parsing request sent from web, and making download request for download manager
  **/
  protected DownloadManager.Request deserialiseRequest(JSONObject obj) throws JSONException {
    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(obj.getString("uri")));

    String cookie = CookieManager.getInstance().getCookie(obj.getString("uri"));

    req.addRequestHeader("cookie", cookie);
    req.setTitle(obj.optString("title"));
    req.setDescription(obj.optString("description"));
    req.setMimeType(obj.optString("mimeType", null));

    if (obj.has("destinationInExternalFilesDir")) {
      Context context = cordova.getActivity()
                               .getApplication()
                               .getApplicationContext();
      
      JSONObject params = obj.getJSONObject("destinationInExternalFilesDir");
      req.setDestinationInExternalFilesDir(context, params.optString("dirType"), params.optString("subPath"));
    }
    else if (obj.has("destinationInExternalPublicDir")) {
      JSONObject params = obj.getJSONObject("destinationInExternalPublicDir");
      req.setDestinationInExternalPublicDir(params.optString("dirType"), params.optString("subPath"));
    }
    else if (obj.has("destinationUri")) {
      req.setDestinationUri(Uri.parse(obj.getString("destinationUri")));
    }
      
    req.setVisibleInDownloadsUi(obj.optBoolean("visibleInDownloadsUi", true));
    req.setNotificationVisibility(obj.optInt("notificationVisibility"));
    
    if (obj.has("headers")) {
      JSONArray arrHeaders = obj.optJSONArray("headers");
      for (int i = 0; i < arrHeaders.length(); i++) {
        JSONObject headerObj = arrHeaders.getJSONObject(i);
        req.addRequestHeader(headerObj.optString("header"), headerObj.optString("value"));
      }
    }
    
    return req;
  }

  public void onDestroy() {
    removeDownloadReceiver();
  }
}
