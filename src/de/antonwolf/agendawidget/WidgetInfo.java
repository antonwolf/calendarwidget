/*
 * Copyright (C) 2011 by Anton Wolf
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *  
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.antonwolf.agendawidget;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

final class WidgetInfo {
	public final static class CalendarPreferences {
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

			key = String.format(CALENDARS_KEY, widgetId, calendarId);
			enabled = prefs.getBoolean(key, enabledDefault);
		}
	}

	public enum DateFormat {
		DOT_DAY_MONTH("%1$te.%1$tm", "%1$te.%1$tm.%1$ty"), SLASH_DAY_MONTH(
				"%1$te/%1$tm", "%1$te/%1$tm/%1$ty"), SLASH_MONTH_DAY(
				"%1$tm/%1$td", "%1$tm/%1$td/%1$ty"), SLASH_YEAR_MONTH_DAY(
				"%1$tm/%1$td", "%1$ty/%1$tm/%1$td");

		public final String shortFormat;
		public final String longFormat;

		private DateFormat(String shortFormat, String longFormat) {
			this.shortFormat = shortFormat;
			this.longFormat = longFormat;
		}
	}

	public static final String BIRTHDAY_SPECIAL = "special";
	public static final String BIRTHDAY_NORMAL = "normal";
	public static final String BIRTHDAY_HIDE = "hidden";

	public final int widgetId;

	public final String birthdays;
	public final String birthdaysDefault;
	public final String birthdaysKey;
	private static final String BIRTHDAYS_KEY = "%dbirthdays";

	public final String lines;
	public final String linesDefault;
	public final String linesKey;
	private static final String LINES_KEY = "%dlines";

	public final String size;
	public final String sizeDefault = "100";;
	public final String sizeKey;
	private static final String SIZE_KEY = "%dsize";

	public final String opacity;
	public final String opacityDefault = "60";
	public final String opacityKey;
	private static final String OPACITY_KEY = "%dopacity";

	public final boolean calendarColor;
	public final boolean calendarColorDefault = true;
	public final String calendarColorKey;
	private static final String CALENDAR_COLOR_KEY = "%dcalendarColor";

	public final boolean tomorrowYesterday;
	public final boolean tomorrowYesterdayDefault = true;
	public final String tomorrowYesterdayKey;
	private static final String TOMORROW_YESTERDAY_KEY = "%dtommorowYesterday";

	public final boolean weekday;
	public final boolean weekdayDefault = true;
	public final String weekdayKey;
	private static final String WEEKDAY_KEY = "%dweekday";

	public final boolean endTime;
	public final boolean endTimeDefault;
	public final String endTimeKey;
	private static final String END_TIME_KEY = "%dendTime";

	public final boolean twentyfourHours;
	public final boolean twentyfourHoursDefault;
	public final String twentyfourHoursKey;
	private static final String TWENTYFOUR_HOURS_KEY = "%dtwentyfourHours";

	public final DateFormat dateFormat;
	public final DateFormat dateFormatDefault;
	public final String dateFormatKey;
	private static final String DATE_FORMAT_KEY = "%ddateFormat";

	public final Map<Integer, CalendarPreferences> calendars;
	private static final String CALENDARS_KEY = "%dcalendar%d";

	public WidgetInfo(int widgetId, Context context) {
		this.widgetId = widgetId;
		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		final AppWidgetManager manager = AppWidgetManager.getInstance(context);
		final AppWidgetProviderInfo widgetInfo = manager
				.getAppWidgetInfo(widgetId);

		final WindowManager winManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		final DisplayMetrics metrics = new DisplayMetrics();
		winManager.getDefaultDisplay().getMetrics(metrics);

		final int heightInCells = (int) (widgetInfo.minHeight / metrics.density + 2) / 74;
		final int widthInCells = (int) (widgetInfo.minWidth / metrics.density + 2) / 74;

		final Resources res = context.getResources();

		birthdaysKey = String.format(BIRTHDAYS_KEY, widgetId);
		birthdaysDefault = widthInCells > 2 ? BIRTHDAY_SPECIAL
				: BIRTHDAY_NORMAL;
		birthdays = prefs.getString(birthdaysKey, birthdaysDefault);

		int linesInt = 5 + (int) ((heightInCells - 1) * 5.9);
		linesDefault = Integer.toString(linesInt);
		linesKey = String.format(LINES_KEY, widgetId);
		lines = prefs.getString(linesKey, linesDefault);

		sizeKey = String.format(SIZE_KEY, widgetId);
		size = prefs.getString(sizeKey, sizeDefault);

		opacityKey = String.format(OPACITY_KEY, widgetId);
		opacity = prefs.getString(opacityKey, opacityDefault);

		calendarColorKey = String.format(CALENDAR_COLOR_KEY, widgetId);
		calendarColor = prefs
				.getBoolean(calendarColorKey, calendarColorDefault);

		tomorrowYesterdayKey = String.format(TOMORROW_YESTERDAY_KEY, widgetId);
		tomorrowYesterday = prefs.getBoolean(tomorrowYesterdayKey,
				tomorrowYesterdayDefault);

		weekdayKey = String.format(WEEKDAY_KEY, widgetId);
		weekday = prefs.getBoolean(weekdayKey, weekdayDefault);

		endTimeKey = String.format(END_TIME_KEY, widgetId);
		endTimeDefault = widthInCells > 2;
		endTime = prefs.getBoolean(endTimeKey, endTimeDefault);

		twentyfourHoursKey = String.format(TWENTYFOUR_HOURS_KEY, widgetId);
		twentyfourHoursDefault = res.getBoolean(R.bool.format_24hours);
		twentyfourHours = prefs.getBoolean(twentyfourHoursKey,
				twentyfourHoursDefault);

		dateFormatKey = String.format(DATE_FORMAT_KEY, widgetId);
		dateFormatDefault = DateFormat.valueOf(res
				.getString(R.string.format_date));
		dateFormat = DateFormat.valueOf(prefs.getString(dateFormatKey,
				dateFormatDefault.toString()));

		calendars = getCalendars(context, widgetId);
	}

	private static Map<Integer, CalendarPreferences> getCalendars(
			Context context, int widgetId) {
		Cursor cursor = null;
		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		try {
			cursor = context.getContentResolver().query(
					Uri.parse("content://com.android.calendar/calendars"),
					new String[] { "_id", "displayName", "color" }, null, null,
					"displayName ASC");
			final Map<Integer, CalendarPreferences> calendars = new HashMap<Integer, CalendarPreferences>(
					cursor.getCount());

			while (cursor.moveToNext())
				calendars.put(
						cursor.getInt(0),
						new CalendarPreferences(prefs, widgetId, cursor
								.getInt(0), cursor.getString(1), cursor
								.getInt(2)));
			return calendars;
		} finally {
			if (null != cursor)
				cursor.close();
		}
	}

	public static void delete(Context context, int widgetId) {
		Editor editor = PreferenceManager.getDefaultSharedPreferences(context)
				.edit();
		editor.remove(String.format(BIRTHDAYS_KEY, widgetId));
		editor.remove(String.format(LINES_KEY, widgetId));
		editor.remove(String.format(SIZE_KEY, widgetId));
		editor.remove(String.format(OPACITY_KEY, widgetId));
		editor.remove(String.format(CALENDAR_COLOR_KEY, widgetId));
		editor.remove(String.format(TOMORROW_YESTERDAY_KEY, widgetId));
		editor.remove(String.format(WEEKDAY_KEY, widgetId));
		editor.remove(String.format(END_TIME_KEY, widgetId));
		editor.remove(String.format(TWENTYFOUR_HOURS_KEY, widgetId));
		editor.remove(String.format(DATE_FORMAT_KEY, widgetId));
		for (final Entry<Integer, CalendarPreferences> cinfo : getCalendars(
				context, widgetId).entrySet()) {
			editor.remove(cinfo.getValue().key);
		}
		editor.commit();
	}

}
