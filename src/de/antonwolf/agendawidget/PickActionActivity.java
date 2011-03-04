package de.antonwolf.agendawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class PickActionActivity extends Activity {
	static final String TAG = "AgendaWidget";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "PickActionActivity.onCreate()");
		setContentView(R.layout.pick_action);
		findViewById(R.id.open_calendar).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						Intent intent = new Intent(
								Intent.ACTION_VIEW,
								Uri.parse("content://com.android.calendar/time"));
						startActivity(intent);
					}
				});
	}
}
