package com.timescript.tm100server;

import android.content.Context;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

import com.timescript.voice.TimeVoice;

public class MainActivity extends Activity {
	public String TAG = "TimeService";
    private Context context;
	private Button button;
    private Button voiceButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
        context = this;
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(context, com.timescript.video.VideoActivity.class));
            }
        });

        voiceButton = (Button) findViewById(R.id.button_voice);
        voiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimeVoice.speak("欢迎使用水牙医");
            }
        });

		Intent startIntent = new Intent(this, MainService.class);
		startService(startIntent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		super.dispatchKeyEvent(event);
		if((event.getKeyCode() == KeyEvent.KEYCODE_V) && (event.getAction() == KeyEvent.ACTION_DOWN)) {
			startActivity(new Intent(this, com.timescript.video.VideoActivity.class));
		}
		return true;
	}
}
