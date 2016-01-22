package com.timescript.tm100server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.androidsocket.upload.UploadUtilsAsync;
import com.androidsocket.upload.UploadUtilsAsync.OnSuccessListener;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.util.Log;
import android.view.SurfaceView;

public class TakePhotosTask {
	public String TAG = "Toothbrush";
	public Context context;
	private boolean started = false;
	public Timer timer = null;
	public Camera camera;
	public String filePath;
	private UploadUtilsAsync uploadAsync = null;

	private PictureCallback pictureCallback = new PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onPictureTaken");
			if (UserProfiles.getUploadFlag()) {
				Bitmap bmBitmap = BitmapFactory.decodeByteArray(data, 0,
						data.length);
				filePath = "/sdcard/toothbrush/" + System.currentTimeMillis()
						+ ".jpg";
				File imgFile = new File(filePath);
				try {
					BufferedOutputStream outputStream = new BufferedOutputStream(
							new FileOutputStream(imgFile));
					bmBitmap.compress(Bitmap.CompressFormat.JPEG, 100,
							outputStream);
					outputStream.flush();
					outputStream.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.d(TAG, "pictureCallback err!");
				}
				
				// send to server
				ArrayList<String> paths = new ArrayList<String>();
				paths.add(filePath);
				Map<String, String> paramMap = new HashMap<String, String>();
				paramMap.put("deviceNo", UserProfiles.getSerialNum());
				paramMap.put("qqAccount", UserProfiles.getBindQQNum() + "");
				paramMap.put("pictureTime", System.currentTimeMillis() + "");

				uploadAsync = new UploadUtilsAsync(paramMap, paths, UploadUtilsAsync.UPLOAD_IMG);
				uploadAsync.setListener(new OnSuccessListener() {

					@Override
					public void onSuccess(String result) {
					}
				});
				uploadAsync.execute();
			}
		}
	};

	public class PhotoTimerTask extends TimerTask {

		@Override
		public void run() {
			// TODO Auto-generated method stub

			Log.d(TAG, "TakePhotosTask timertask running");
			if (started) {
				try {
					camera.startPreview();
					camera.takePicture(null, null, pictureCallback);
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
			} else {
				if (timer != null) {
					timer.cancel();
					timer = null;
					this.cancel();
				}
				camera.stopPreview();
				camera.release();
			}
		}
	}

	public TakePhotosTask(Context context) {
		// TODO Auto-generated constructor stub
		this.context = context;
		File file = new File("/sdcard/toothbrush/");
		if (!file.exists()) {
			file.mkdir();
		}
	}

	public void start() {
		if (started) {
			Log.d(TAG, "TakephotoTask alreay in running");
			return;
		}
		started = true;
		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub

				camera = Camera.open(0);
				SurfaceView view = new SurfaceView(context);
				try {
					camera.setPreviewDisplay(view.getHolder());
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					Log.d(TAG, "setPreviewDisplay err!");
				}
				timer = new Timer(true);
				timer.schedule(new PhotoTimerTask(), 0, 6000);
			}
		}).run();
	}

	public void stop() {
		Log.d(TAG, "TakePhotosTask stop");
		started = false;
	}

}
