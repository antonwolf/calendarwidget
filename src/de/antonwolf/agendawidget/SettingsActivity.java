package de.antonwolf.agendawidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

public class SettingsActivity extends PreferenceActivity {
	public final static String EXTRA_WIDGET_ID = "widgetId";
	static final String TAG = "AgendaWidget";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
		Log.d(TAG, "SettingsActivity.onCreate(" + widgetId + ")");
		if (-1 == widgetId)
			return;

		PreferenceScreen screen = getPreferenceManager()
				.createPreferenceScreen(this);
		setPreferenceScreen(screen);

		PreferenceCategory display = new PreferenceCategory(this);
		display.setTitle(R.string.settings_display);
		screen.addPreference(display);

		ListPreference lines = new ListPreference(this);
		lines.setTitle(R.string.settings_display_lines);
		lines.setKey(widgetId + "lines");
		lines.setSummary(R.string.settings_display_lines_summary);
		lines.setEntries(R.array.settings_display_lines_entries);
		lines.setEntryValues(new String[] { "3", "4", "5", "6", "7", "8", "9",
				"10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
				"20", "21", "22", "23", "24", "25" });
		lines.setDefaultValue(getDefaultLines(widgetId, this));
		display.addPreference(lines);

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
	protected void onResume() {
		super.onResume();
		
		int widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
		Log.d(TAG, "SettingsActivity.onResume(" + widgetId + ")");
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		int widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
		Log.d(TAG, "SettingsActivity.onPause(" + widgetId + ")");
		if (-1 == widgetId)
			return;

		Intent intent = new Intent("update", Uri.parse("widget://" + widgetId),
				this, WidgetService.class);
		Log.d(TAG, "Sending " + intent);
		startService(intent);
	}

	public static String getDefaultLines(int widgetId, Context context) {
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(widgetId);

		WindowManager winManager = (WindowManager) context
				.getSystemService(WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		winManager.getDefaultDisplay().getMetrics(metrics);
		int heightInCells = (int) (widgetInfo.minHeight / metrics.density + 2) / 74;
		int maxLines = 5 + (int) ((heightInCells - 1) * 5.9);
		return Integer.toString(maxLines);
	}
}
