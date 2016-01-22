package com.timescript.wifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BtComm {
	private String TAG = "BtComm";
	public boolean defState = false;
	public BluetoothAdapter mBluetoothAdapter;
	public BluetoothServerSocket serverSocket;
	public UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private Context context;
	private AcceptThread acceptThread;
	private boolean isConnected = false;

	private BluetoothServerSocket mmServerSocket;
	private BluetoothSocket socket;
	private InputStream inStream = null;
	private OutputStream outStream = null;

	public BtComm(Context context) {
		// TODO Auto-generated constructor stub
		this.context = context;
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	public void enableBT(boolean enable) {
		if (enable) {
			mBluetoothAdapter.enable();
		} else {
			mBluetoothAdapter.disable();
		}
	}
	
	public void waitForConnected(){
		enableBT(true);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
		acceptThread = new AcceptThread();
		acceptThread.start();
		/*
		try {
			acceptThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.d(TAG, "connected success");*/
	}
	
	private class AcceptThread extends Thread {

		public AcceptThread() {
            while (mmServerSocket == null) {
                try {
                    sleep(200);
                    mmServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                            "bttest", uuid);

                    setDiscoverableTimeout(300);
                } catch (Exception e) {
                }
            }
        }

		public void run() {
			// Keep listening until exception occurs or a socket is returned
			while (true) {
                try {
                    Log.d(TAG, "wait for connect ...");
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    manageConnectedSocket(socket);
                }
            }
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel() {
			try {
				mmServerSocket.close();
			} catch (IOException e) {
			}
		}
	}

	private void manageConnectedSocket(BluetoothSocket socket) {
		// TODO Auto-generated method stub
		Log.d(TAG, "new socket connected ...");
		byte[] buffer = new byte[1024];
		int bytes = 0;
		try {
			inStream = socket.getInputStream();
			outStream = socket.getOutputStream();
			while (!isConnected) {
				bytes = inStream.read(buffer);
				final String data = new String(buffer, 0, bytes);
				Log.d(TAG, "data: " + data);
				//start to connect wifi
				startConnectAP(data);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void connectedOK(){
		isConnected = true;
		try {
			outStream.write("OK".getBytes());
			
			acceptThread.cancel();
			inStream.close();
			outStream.close();
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void connectedFail(){
		try {
			outStream.write("FAIL".getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void startConnectAP(String data) {
		// TODO Auto-generated method stub
		String[] wifiData = new String[2];
		wifiData = data.split(";");
		if (wifiData.length != 2) {
			Log.d(TAG, "get the rong data!!!");
			return;
		}
		Log.d(TAG, "wifiName:" + wifiData[0] + "	wifiPwd:" + wifiData[1]);
		WifiAPConnect apConnect = new WifiAPConnect(context) {

			@Override
			public void onNotifyWifiConnected() {
				// TODO Auto-generated method stub
				connectedOK();
				Log.d(TAG, "connect to wifi ok");
			}

			@Override
			public void onNotifyWifiConnectFailed() {
				// TODO Auto-generated method stub
				connectedFail();
				Log.d(TAG, "connect to wifi fail");
			}

			@Override
			public void myUnregisterReceiver(BroadcastReceiver rec) {
				// TODO Auto-generated method stub
				context.unregisterReceiver(rec);
			}

			@Override
			public Intent myRegisterReceiver(BroadcastReceiver rec,
					IntentFilter filter) {
				// TODO Auto-generated method stub
				context.registerReceiver(rec, filter);
				return null;
			}
		};
		apConnect.addNetwork(wifiData[0], wifiData[1]);
	}
	
	public void setDiscoverableTimeout(int timeout) {
		BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
		try {
			Method setDiscoverableTimeout = BluetoothAdapter.class.getMethod("setDiscoverableTimeout", int.class);
			setDiscoverableTimeout.setAccessible(true);
			Method setScanMode =BluetoothAdapter.class.getMethod("setScanMode", int.class,int.class);
			setScanMode.setAccessible(true);
			
			setDiscoverableTimeout.invoke(adapter, timeout);
			setScanMode.invoke(adapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,timeout);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
