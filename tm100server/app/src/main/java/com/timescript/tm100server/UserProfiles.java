package com.timescript.tm100server;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.androidsocket.upload.UploadUtilsAsync;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;

public class UserProfiles {
	public static String TAG = "Toothbrush";
	public static Context context = null;
	//the same as user id
	private static String serialNum = null;
	
	private static String userName = null;
	private static long bindQQNum = 0;
	//decide auto upload the data to server
	private static boolean autoUpload = false;

	// user config info
	private static int mode = 0;
	private static int defaultTime = 0;

	
	public UserProfiles(Context context) {
		// TODO Auto-generated constructor stub
		this.context = context;
	}

	public static void loadData() {
		//load data from database
		SharedPreferences userData = context.getSharedPreferences("user_data", Context.MODE_WORLD_READABLE);
		serialNum = userData.getString("serialNum", null);
		userName = userData.getString("userName", "UserName");
		bindQQNum = userData.getLong("bindQQNum", 0);
		autoUpload = userData.getBoolean("autoUpload", true);
		if(serialNum == null) {
			serialNum = getDeviceSerialNumber();
			saveData();
		}
	}
	
	public static void saveData() {
		SharedPreferences userData = context.getSharedPreferences("user_data", Context.MODE_WORLD_READABLE);
		Editor editor = userData.edit();
		editor.putString("serialNum", serialNum);
		editor.putString("userName", userName);
		editor.putLong("bindQQNum", bindQQNum);
		editor.putBoolean("autoUpload", autoUpload);
		editor.commit();
		uploadData();
	}
	
	public static void uploadData(){
		UploadUtilsAsync uploadAsync = null;
		Map<String, String> paramMap = new HashMap<String, String>();
		paramMap.put("deviceNo", UserProfiles.getSerialNum());
		paramMap.put("userName", UserProfiles.getUserName());
		paramMap.put("qqAccount", UserProfiles.getBindQQNum() + "");
		uploadAsync = new UploadUtilsAsync(paramMap, null, UploadUtilsAsync.UPLOAD_USER_INFO);
		uploadAsync.execute();
	}

	public static String getSerialNum() {
		return serialNum;
	}
	public static void setSerialNum(String str) {
		if(!str.equals(serialNum)) {
			serialNum = str;
			saveData();
		}
	}
	
	public static String getUserName() {
		return userName;
	}
	public static void setUserName(String str) {
		if(!str.equals(userName)) {
			userName = str;
			saveData();
		}
	}
	
	public static long getBindQQNum() {
		return bindQQNum;
	}
	public static void setBindQQNum(long num) {
		if(num != bindQQNum) {
			bindQQNum = num;
			saveData();
		}
	}
	
	public static boolean getUploadFlag() {
		return autoUpload;
	}
	public static void setUploadFlag(boolean flag) {
		if(flag != autoUpload) {
			autoUpload = flag;
			saveData();
		}
	}
	
	public static String getDeviceSerialNumber(){
	    String serial = null;
	    try {
	    Class<?> c =Class.forName("android.os.SystemProperties");
	       Method get =c.getMethod("get", String.class);
	       serial = (String)get.invoke(c, "ro.serialno");
	    } catch (Exception e) {
	       e.printStackTrace();
	    }
	    Log.d(TAG, "get the serialno " + serial);
	    return serial;
	}
}
