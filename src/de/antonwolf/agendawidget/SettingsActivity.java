package de.antonwolf.agendawidget;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
	public final static String EXTRA_WIDGET_ID = "widgetId";
	static final String TAG = "AgendaWidget";

	private int widgetId = -1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
		if (-1 == widgetId)
			return;

		PreferenceScreen screen = getPreferenceManager()
				.createPreferenceScreen(this);
		setPreferenceScreen(screen);

		PreferenceCategory birthdays = new PreferenceCategory(this);
		birthdays.setTitle(R.string.settings_birthdays);
		screen.addPreference(birthdays);

		CheckBoxPreference bDayRecognition = new CheckBoxPreference(this);
		bDayRecognition.setDefaultValue(true);
		bDayRecognition.setKey(widgetId + "bDayRecognition");
		bDayRecognition.setTitle(R.string.settings_birthdays_recognition);
		bDayRecognition
				.setSummaryOn(R.string.settings_birthdays_recognition_special);
		bDayRecognition
				.setSummaryOff(R.string.settings_birthdays_recognition_normal);
		birthdays.addPreference(bDayRecognition);

		CheckBoxPreference bDayDisplay = new CheckBoxPreference(this);
		bDayDisplay.setDefaultValue(true);
		bDayDisplay.setKey(widgetId + "bDayDisplay");
		bDayDisplay.setTitle(R.string.settings_birthdays_display);
		bDayDisplay.setSummaryOn(R.string.settings_birthdays_display_show);
		bDayDisplay.setSummaryOff(R.string.settings_birthdays_display_hide);
		birthdays.addPreference(bDayDisplay);

		PreferenceCategory calendars = new PreferenceCategory(this);
		calendars.setTitle(R.string.settings_calendars);
		screen.addPreference(calendars);

		Cursor cursor = null;

		try {
			cursor = getContentResolver().query(
					Uri.parse("content://com.android.calendar/calendars"),
					new String[] { "_id", "displayName" }, null, null,
					"displayName ASC");

			while (cursor.moveToNext()) {
				CheckBoxPreference calendar = new CheckBoxPreference(this);
				calendar.setDefaultValue(true);
				calendar.setKey(widgetId + "calendar" + cursor.getInt(0));
				calendar.setSummaryOn(R.string.settings_calendars_show);
				calendar.setSummaryOff(R.string.settings_calendars_hide);
				calendar.setTitle(cursor.getString(1));
				calendars.addPreference(calendar);
			}
		} finally {
			if (null != cursor)
				cursor.close();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "SettingsActivity.onPause()");
		if (-1 == widgetId)
			return;

		Intent intent = new Intent("update", Uri.parse("widget://" + widgetId),
				this, WidgetService.class);
		Log.d(TAG, "Sending " + intent);
		startService(intent);
	}
}
