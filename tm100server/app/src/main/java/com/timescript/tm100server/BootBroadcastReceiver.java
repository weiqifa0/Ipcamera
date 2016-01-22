package com.timescript.tm100server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootBroadcastReceiver extends BroadcastReceiver{
	private static final String BOOT_ACTION = "android.intent.action.BOOT_COMPLETED";
	private static final String SHUTDOWN_ACTION = "android.intent.action.ACTION_SHUTDOWN";

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		if(intent.getAction().equals(BOOT_ACTION)) {
			Intent startIntent = new Intent(Intent.ACTION_RUN);
			startIntent.setClass(context, MainService.class);
			context.startService(startIntent);
		}
	}

}
