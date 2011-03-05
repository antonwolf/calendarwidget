package de.antonwolf.agendawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class PickActionActivity extends Activity {
	public final static String EXTRA_WIDGET_ID = "widgetId";
	private final static String TAG = "AgendaWidget";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Log.d(TAG, "PickActionActivity.onCreate()");
		final int widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
		if (widgetId == -1) {
			Log.d(TAG, "No widget id!");
			return;
		}

		setContentView(R.layout.pick_action);

		final Intent calendar = new Intent(Intent.ACTION_VIEW,
				Uri.parse("content://com.android.calendar/time"));

		final Intent settings = new Intent(this, SettingsActivity.class);
		settings.putExtra(SettingsActivity.EXTRA_WIDGET_ID, widgetId);

		findViewById(R.id.open_calendar).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(calendar);
					}
				});
		findViewById(R.id.open_settings).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(settings);
					}
				});
	}
}
