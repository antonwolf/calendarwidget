package de.antonwolf.agendawidget;

import java.util.ArrayList;
import java.util.List;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public final class WidgetPreferences {
	public final class CalendarPreferences {
		public int calendarId;
		public String displayName;

		private CalendarPreferences(int calendarId, String displayName) {
			this.calendarId = calendarId;
			this.displayName = displayName;
		}
	}

	private int widgetId;
	private Context context;
	private SharedPreferences prefs;

	public WidgetPreferences(int appWidgetId, Context context) {
		widgetId = appWidgetId;
		this.context = context;
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}

	public String getBirthdayRecognitionKey() {
		return widgetId + "bDayRecognition";
	}

	public boolean isBirthdayRecognition() {
		return prefs.getBoolean(getBirthdayRecognitionKey(), true);
	}

	public String getBirthdayDisplayKey() {
		return widgetId + "bDayDisplay";
	}

	public boolean isBirthdayDisplay() {
		return prefs.getBoolean(getBirthdayDisplayKey(), true);
	}

	public String getLinesKey() {
		return widgetId + "lines";
	}

	public int getLines() {
		String defaultValue = Integer.toString(getLinesDefault());
		String lines = prefs.getString(getLinesKey(), defaultValue);
		return Integer.parseInt(lines);
	}

	private int getLinesDefault() {
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(widgetId);

		WindowManager winManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		winManager.getDefaultDisplay().getMetrics(metrics);

		int heightInCells = (int) (widgetInfo.minHeight / metrics.density + 2) / 74;

		return 5 + (int) ((heightInCells - 1) * 5.9);
	}

	public List<CalendarPreferences> getCalendars() {
		Cursor cursor = null;

		try {
			cursor = context.getContentResolver().query(
					Uri.parse("content://com.android.calendar/calendars"),
					new String[] { "_id", "displayName" }, null, null,
					"displayName ASC");
			ArrayList<CalendarPreferences> result = new ArrayList<WidgetPreferences.CalendarPreferences>(
					cursor.getCount());
			while (cursor.moveToNext())
				result.add(new CalendarPreferences(cursor.getInt(0), cursor
						.getString(1)));
			return result;
		} finally {
			if (null != cursor)
				cursor.close();
		}
	}

	public String getCalendarKey(int calendar) {
		return widgetId + "calendar" + calendar;
	}

	public boolean isCalendar(int calendar) {
		return prefs.getBoolean(getCalendarKey(calendar), true);
	}

}
