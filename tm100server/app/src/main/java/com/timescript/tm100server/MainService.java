package com.timescript.tm100server;

import com.timescript.message.UDPServer;
import com.timescript.voice.TimeVoice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;

public class MainService extends Service {
	public static String TAG = "TimeServices";
    public TimeVoice timeVoice;
	public UserProfiles userProfiles = null;
	private static TakePhotosTask takePhotosTask = null;
	private static BrushRecorder brushRecorder = null;
	private AudioManager mAudioManager;
	private ComponentName comp;
	private TimescriptButtonReceiver receiver;
	public static boolean devicesOnLine = false;
	public static boolean bootFromOTA = false;
    public static Context context;

    public UDPServer udpServer;
	
	public static SensorManager sensorManager;
	public static SensorEventListener sensorEventListener = new SensorEventListener() {
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			// TODO Auto-generated method stub
			if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
				float data[] = event.values;
				//Log.d(TAG, "X: "+data[0]+"\n"+"Y: "+data[1]+"\n"+"Z: "+data[2]);
				brushRecorder.sensorDataProccess(data[0], data[1], data[2]);
			}
		}
		
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
			
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		Log.d(TAG, "MainService onBind");
		return null;
	}
	
	private static boolean takePhotoRunning = false;
	private static boolean brushRunning = false;
	public static void testStart(int flag) {
		if(flag == 1) { //takePhoto test
			takePhotoRunning = !takePhotoRunning;
			if (takePhotoRunning) {
				takePhotosTask.start();
			} else {
				takePhotosTask.stop();
			}
		} else if(flag == 2){ //brush test
			brushRunning = !brushRunning;
			if(brushRunning) {
				brushRecorder.startRecord();
				sensorManager.registerListener(sensorEventListener, 
						sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
						SensorManager.SENSOR_DELAY_NORMAL);
			} else {
				brushRecorder.stopRecord();
				sensorManager.unregisterListener(sensorEventListener);
			}
		}
	}
	
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub

		super.onStartCommand(intent, flags, startId);
		return START_REDELIVER_INTENT;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onStart(Intent intent, int startId) {
		// TODO Auto-generated method stub
		super.onStart(intent, startId);
        context = this;

        //init iflytek voice
        timeVoice = new TimeVoice(context);
        timeVoice.initVoice();

		// init wifi connect
        WifiManager mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
		ConnectivityManager mWifiCon = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifiInfo = mWifiCon
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if (!mWifiInfo.isConnected()) {
            Log.d(TAG, "wifi not connect");
			//use wifi p2p communication
			//startService(new Intent(this, WiFiDirectService.class));
			//use audio communication
			//startService(new Intent(this, RecWifiData.class));
			//use bt communication

			//BtComm btRec = new BtComm(this);
			//btRec.waitForConnected();
            Intent intent1 = new Intent(context, com.timescript.qr.activity.CaptureActivity.class);
            intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent1);
		} else {
        }

		// init user data
		userProfiles = new UserProfiles(this);
		userProfiles.loadData();

        // wait for client connect
        udpServer = new UDPServer();
        udpServer.waitForConn();

		// register a media button receiver to start take picture or brush
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		comp = new ComponentName(getPackageName(),
				TimescriptButtonReceiver.class.getName());
		mAudioManager.registerMediaButtonEventReceiver(comp);

		takePhotosTask = new TakePhotosTask(this);
		brushRecorder = new BrushRecorder(this);
		

		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		Log.d(TAG, "MainService destory");
        userProfiles.saveData();

		mAudioManager.unregisterMediaButtonEventReceiver(comp);
	}

	public static class TimescriptButtonReceiver extends BroadcastReceiver {
		private static boolean startTakePicture = false;
		private static boolean startBrush = false;

		static boolean isRecording = false;

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			KeyEvent keyEvent = (KeyEvent) intent
					.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if ((keyEvent.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK)
					&& (keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
				// start to take picture and send to server
				if (!MainService.devicesOnLine) {
					Log.d(TAG, "devices is offline!!!");
					return;
				}
				startTakePicture = !startTakePicture;
				if (startTakePicture) {
					takePhotosTask.start();
				} else {
					takePhotosTask.stop();
				}
			} else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT) {
				// start to brush the tooth, and need catch the time here
				/*
				if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
					Log.d(TAG, "KeyEvent.KEYCODE_MEDIA_EJECT ACTION_DOWN");
					brushRecorder.startRecord();
					sensorManager.registerListener(sensorEventListener, 
							sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
							SensorManager.SENSOR_DELAY_NORMAL);
				} else if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
					Log.d(TAG, "KeyEvent.KEYCODE_MEDIA_EJECT ACTION_UP");
					brushRecorder.stopRecord();
					sensorManager.unregisterListener(sensorEventListener);
				}
				*/
				if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
					isRecording = !isRecording;
					if(isRecording) {
						brushRecorder.startRecord();
						sensorManager.registerListener(sensorEventListener, 
								sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
								SensorManager.SENSOR_DELAY_NORMAL);
					}else {
						brushRecorder.stopRecord();
						sensorManager.unregisterListener(sensorEventListener);
					}
				}
			} else if(keyEvent.getKeyCode() == KeyEvent.KEYCODE_V) {
				context.startActivity(new Intent(context, com.timescript.video.VideoActivity.class));
			}
		}

	}

	public static void processMsg(Object obj) {
		if(obj != null) {
			if(obj.equals("VIDEO")) {
				Intent intent = new Intent(context, com.timescript.video.VideoActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(intent);
			}
		}

	}
}
