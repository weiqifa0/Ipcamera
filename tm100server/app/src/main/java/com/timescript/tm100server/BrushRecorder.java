package com.timescript.tm100server;

import java.lang.System;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.androidsocket.upload.UploadUtilsAsync;

import android.content.Context;
import android.util.Log;

public class BrushRecorder {
	private String TAG = "Toothbrush";
	private Context context;
	private long startTime = 0;
	private long stopTime = 0;
	private long upTimes = 0;
	private long belowTimes = 0;
	private boolean upFlag = true;
	private long upDownStart = 0;
	//private long timeout = 3 * 60 * 1000; // 3 minute
	private long timeout = 10 * 1000; // 10s
	private boolean timerRunning = false;
	private Timer timer = new Timer(true);
	private RecordTimerTask task;

	public BrushRecorder(Context context) {
		// TODO Auto-generated constructor stub
		this.context = context;
	}

	public void startRecord() {
		if ((startTime == 0)
				|| ((System.currentTimeMillis() - stopTime) > timeout)) {
			startTime = System.currentTimeMillis();
		}
		upDownStart = System.currentTimeMillis();
	}

	public void stopRecord() {
		stopTime = System.currentTimeMillis();
		if (!timerRunning) {
			task = new RecordTimerTask();
			timer.schedule(task, timeout);
			timerRunning = true;
		} else {
			task.cancel();
			task = new RecordTimerTask();
			timer.schedule(task, timeout);
		}
		
		if(upFlag){
			upTimes += System.currentTimeMillis() - upDownStart;
		}else{
			belowTimes += System.currentTimeMillis() - upDownStart;
		}
	}

	private float priticalZ = (float) -1.5;
	public void sensorDataProccess(float x, float y, float z) {

		if(x > -3.5)
			priticalZ = 2;
		
		if (z < priticalZ) { // brush below
			if (upFlag) {
				Log.d(TAG, "brush below");
				upTimes += System.currentTimeMillis() - upDownStart;
				upDownStart = System.currentTimeMillis();
			}
			upFlag = false;
		} else { // brush up
			if (!upFlag) {
				Log.d(TAG, "brush up");
				belowTimes += System.currentTimeMillis() - upDownStart;
				upDownStart = System.currentTimeMillis();
			}
			upFlag = true;
		}
	}

	public void sendRecordToServer(long start, long stop) {
		SimpleDateFormat dataFormat = new SimpleDateFormat(
				"yy/MM/dd HH:mm:ss");
		Log.d(TAG, dataFormat.format(new Date(start)) + "--" + dataFormat.format(new Date(stop)));
		Log.d(TAG, "upTimes: " + upTimes/1000 + "s");
		Log.d(TAG, "belowTimes: " + belowTimes/1000 + "s");
		if (UserProfiles.getUploadFlag()) {
			UploadUtilsAsync uploadAsync = null;
			Map<String, String> paramMap = new HashMap<String, String>();
			paramMap.put("deviceNo", UserProfiles.getSerialNum());
			paramMap.put("qqAccount", UserProfiles.getBindQQNum() + "");
			paramMap.put("brushTime", dataFormat.format(new Date(start)) + "--" + dataFormat.format(new Date(stop)));
			uploadAsync = new UploadUtilsAsync(paramMap, null, UploadUtilsAsync.UPLOAD_BRUSH_TIME);
			uploadAsync.execute();
		}
	}

	public class RecordTimerTask extends TimerTask {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			timerRunning = false;
			sendRecordToServer(startTime, stopTime);
			startTime = 0;
			stopTime = 0;
			upTimes = 0;
			belowTimes = 0;
		}
	}
}
