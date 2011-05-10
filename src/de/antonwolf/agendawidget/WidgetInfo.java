package de.antonwolf.agendawidget;

import java.util.HashMap;
import java.util.Map;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

public final class WidgetInfo {
	public final class CalendarPreferences {
		public final int calendarId;
		public final int color;
		public final String displayName;
		public final String key;
		public final boolean enabledDefault = true;
		public final boolean enabled;

		private CalendarPreferences(SharedPreferences prefs, int widgetId,
				int calendarId, String displayName, int color) {
			this.calendarId = calendarId;
			this.color = color;
			this.displayName = displayName;

			key = widgetId + "calendar" + calendarId;
			enabled = prefs.getBoolean(key, enabledDefault);
		}
	}

	public static final String BIRTHDAY_SPECIAL = "special";
	public static final String BIRTHDAY_NORMAL = "normal";
	public static final String BIRTHDAY_HIDE = "hidden";

	public final int widgetId;

	public final String birthdays;
	public final String birthdaysDefault;
	public final String birthdaysKey;

	public final String lines;
	public final String linesDefault;
	public final String linesKey;

	public final String size;
	public final String sizeDefault = "100";;
	public final String sizeKey;

	public final String opacity;
	public final String opacityDefault = "60";;
	public final String opacityKey;

	public final boolean calendarColor;
	public final boolean calendarColorDefault = true;
	public final String calendarColorKey;

	public final boolean tomorowYesterday;
	public final boolean tomorowYesterdayDefault = true;
	public final String tomorowYesterdayKey;

	public final boolean weekday;
	public final boolean weekdayDefault = true;
	public final String weekdayKey;

	public final boolean endTime;
	public final boolean endTimeDefault;
	public final String endTimeKey;

	public final Map<Integer, CalendarPreferences> calendars;

	public WidgetInfo(int widgetId, Context context) {
		this.widgetId = widgetId;
		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(widgetId);

		WindowManager winManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics metrics = new DisplayMetrics();
		winManager.getDefaultDisplay().getMetrics(metrics);

		int heightInCells = (int) (widgetInfo.minHeight / metrics.density + 2) / 74;
		int widthInCells = (int) (widgetInfo.minWidth / metrics.density + 2) / 74;

		birthdaysKey = widgetId + "birthdays";
		birthdaysDefault = widthInCells > 2 ? BIRTHDAY_SPECIAL
				: BIRTHDAY_NORMAL;
		birthdays = prefs.getString(birthdaysKey, birthdaysDefault);

		int linesInt = 5 + (int) ((heightInCells - 1) * 5.9);
		linesDefault = Integer.toString(linesInt);
		linesKey = widgetId + "lines";
		lines = prefs.getString(linesKey, linesDefault);

		sizeKey = widgetId + "size";
		size = prefs.getString(sizeKey, sizeDefault);

		opacityKey = widgetId + "opacity";
		opacity = prefs.getString(opacityKey, opacityDefault);

		calendarColorKey = widgetId + "calendarColor";
		calendarColor = prefs
				.getBoolean(calendarColorKey, calendarColorDefault);

		tomorowYesterdayKey = widgetId + "tommorowYesterday";
		tomorowYesterday = prefs.getBoolean(tomorowYesterdayKey,
				tomorowYesterdayDefault);

		weekdayKey = widgetId + "weekday";
		weekday = prefs.getBoolean(weekdayKey, weekdayDefault);

		endTimeKey = widgetId + "endTime";
		endTimeDefault = widthInCells > 2;
		endTime = prefs.getBoolean(endTimeKey, endTimeDefault);

		Map<Integer, CalendarPreferences> calendars = null;
		Cursor cursor = null;
		try {
			cursor = context.getContentResolver().query(
					Uri.parse("content://com.android.calendar/calendars"),
					new String[] { "_id", "displayName", "color" }, null, null,
					"displayName ASC");
			calendars = new HashMap<Integer, CalendarPreferences>(
					cursor.getCount());

			while (cursor.moveToNext())
				calendars.put(
						cursor.getInt(0),
						new CalendarPreferences(prefs, widgetId, cursor
								.getInt(0), cursor.getString(1), cursor
								.getInt(2)));
		} finally {
			if (null != cursor)
				cursor.close();
		}
		this.calendars = calendars;
	}
}
