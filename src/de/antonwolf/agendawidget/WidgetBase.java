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

import java.util.Date;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Anton Wolf
 * 
 *         Base class for each widget
 */
public abstract class WidgetBase extends AppWidgetProvider {
	private ContentObserver calendarInstancesObserver;
	static final String TAG = "AgendaWidget";

	private class CursorManager {
		private Cursor cursor;
		private Pattern[] birthayPatterns;

		private Time dayStart;
		private Time dayEnd;
		private Time oneWeekFromNow;
		private Time monthStart;
		private Time monthEnd;
		private Time yearStart;
		private Time yearEnd;

		private String formatToday;
		private String formatThisWeek;
		private boolean formatThisWeekRemoveDot;
		private String formatThisMonth;
		private String formatThisYear;
		private String formatOther;

		private String formatTime;

		public int color;
		public String title;
		public boolean isBirthday;
		public String eventLocation;
		public Time start;
		public Time end;
		public boolean allDay;
		public String time;

		public CursorManager(Context context) {
			// Cursor
			long nowMillis = new Date().getTime();
			String formatString = context.getResources().getString(
					R.string.calendar_instances_uri_format);
			String uriString = String.format(formatString, nowMillis, nowMillis
					+ 2 * DateUtils.YEAR_IN_MILLIS);
			cursor = context.getContentResolver().query(Uri.parse(uriString),
					null, null, null, "startDay ASC, startMinute ASC");
			// Birthday Patterns
			String[] birthdayPatternStrings = context.getResources()
					.getStringArray(R.array.birthday_patterns);
			birthayPatterns = new Pattern[birthdayPatternStrings.length];
			for (int i = 0; i < birthdayPatternStrings.length; i++)
				birthayPatterns[i] = Pattern.compile(birthdayPatternStrings[i]);

			Resources res = context.getResources();

			formatToday = res.getString(R.string.format_today);
			formatThisWeek = res.getString(R.string.format_this_week);
			formatThisWeekRemoveDot = res
					.getBoolean(R.bool.format_this_week_remove_dot);
			formatThisMonth = res.getString(R.string.format_this_month);
			formatThisYear = res.getString(R.string.format_this_year);
			formatOther = res.getString(R.string.format_other);

			formatTime = res.getString(R.string.format_time);

			dayStart = new Time();
			dayStart.setToNow();
			dayStart.hour = dayStart.minute = dayStart.second = 0;

			dayEnd = new Time(dayStart);
			dayEnd.hour = 23;
			dayEnd.minute = dayEnd.second = 59;

			oneWeekFromNow = new Time(dayEnd);
			oneWeekFromNow.monthDay += 7;
			oneWeekFromNow.normalize(false);

			monthEnd = new Time(dayEnd);
			monthEnd.monthDay = monthEnd.getActualMaximum(Time.MONTH_DAY);

			monthStart = new Time(dayStart);
			monthStart.monthDay = 1;

			yearEnd = new Time(dayEnd);
			yearEnd.month = 11;
			yearEnd.monthDay = 31;

			yearStart = new Time(dayStart);
			yearStart.month = 0;
			yearStart.monthDay = 1;
		}

		public boolean moveToNext() {
			if (!cursor.moveToNext()) {
				cursor.close();
				return false;
			}

			color = cursor.getInt(cursor.getColumnIndex("color"));

			title = cursor.getString(cursor.getColumnIndex("title"));

			isBirthday = false;
			for (Pattern pattern : birthayPatterns) {
				Matcher matcher = pattern.matcher(title);
				if (!matcher.find())
					continue;
				title = matcher.group(1);
				isBirthday = true;
				break;
			}

			eventLocation = cursor.getString(cursor
					.getColumnIndex("eventLocation"));
			if (eventLocation != null && eventLocation.trim().length() == 0)
				eventLocation = null;

			allDay = (1 == cursor.getInt(cursor.getColumnIndex("allDay")));

			start = new Time();
			end = new Time();
			String eventTimezone = cursor.getString(cursor
					.getColumnIndex("eventTimezone"));
			if (null != eventTimezone)
				start.timezone = end.timezone = eventTimezone;
			start.setJulianDay(cursor.getInt(cursor.getColumnIndex("startDay")));
			end.setJulianDay(cursor.getInt(cursor.getColumnIndex("endDay")));

			if (!allDay) {
				start.minute = cursor.getInt(cursor
						.getColumnIndex("startMinute"));
				end.minute = cursor.getInt(cursor.getColumnIndex("endMinute"));
			}
			start.normalize(true);
			end.normalize(true);

			time = formatTime();

			return true;
		}

		private String formatTime() {
			String startDay;

			if (!allDay && isToday(start) && isToday(end))
				startDay = "";
			else if (isToday(start))
				startDay = start.format(formatToday);
			else if (isThisWeek(start))
				startDay = formatThisWeekRemoveDot ? start.format(
						formatThisWeek).replace(".", "") : start
						.format(formatThisWeek);
			else if (isThisMonth(start))
				startDay = start.format(formatThisMonth);
			else if (isThisYear(start))
				startDay = start.format(formatThisYear);
			else
				startDay = start.format(formatOther);

			String startHour = allDay ? "" : start.format(formatTime);
			String startTime = startDay
					+ ((startDay == "" || startHour == "") ? "" : " ")
					+ startHour;

			if (Time.compare(start, end) == 0)
				return startTime;
			else {
				String endDay;

				Time endOfStartDay = new Time(start);
				endOfStartDay.hour = 23;
				endOfStartDay.minute = endOfStartDay.second = 59;

				if (isBeforeEndOfDay(start, end))
					endDay = "";
				else if (isToday(end))
					endDay = end.format(formatToday);
				else if (isThisWeek(end))
					endDay = formatThisWeekRemoveDot ? end.format(
							formatThisWeek).replace(".", "") : end
							.format(formatThisWeek);
				else if (isThisMonth(end))
					endDay = end.format(formatThisMonth);
				else if (isThisYear(end))
					endDay = end.format(formatThisYear);
				else
					endDay = end.format(formatOther);

				String endHour = allDay ? "" : end.format(formatTime);
				String endTime = endDay
						+ ((endDay == "" || endHour == "") ? "" : " ")
						+ endHour;
				return startTime + "-" + endTime;
			}
		}

		private boolean isBeforeEndOfDay(Time day, Time test) {
			Time nextDay = new Time(day);
			nextDay.hour = 23;
			nextDay.minute = 59;
			nextDay.second = 59;
			return Time.compare(test, nextDay) <= 0;
		}

		private boolean isToday(Time time) {
			return Time.compare(time, dayEnd) <= 0
					&& Time.compare(dayStart, time) <= 0;
		}

		private boolean isThisWeek(Time time) {
			return Time.compare(time, oneWeekFromNow) <= 0
					&& Time.compare(dayStart, time) <= 0;
		}

		private boolean isThisMonth(Time time) {
			return Time.compare(time, monthEnd) <= 0
					&& Time.compare(monthStart, time) <= 0;
		}

		private boolean isThisYear(Time time) {
			return Time.compare(time, yearEnd) <= 0
					&& Time.compare(yearStart, time) <= 0;
		}
	}

	@Override
	public void onEnabled(Context context) {
		Log.d(TAG, "WidgetProvider.onEnabled()");
		registerContentObserver(context);
	}

	@Override
	public void onDisabled(Context context) {
		Log.d(TAG, "WidgetProvider.onDisabled()");
		unregisterContentObserver(context);
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
		Log.d(TAG, "WidgetProvider.onUpdate(" + Arrays.toString(ids) + ")");
		unregisterContentObserver(context);
		registerContentObserver(context);
		for (int appWidgetId : ids)
			updateWidget(context, manager, appWidgetId);
	}

	protected abstract int getLineCount();

	private void unregisterContentObserver(Context context) {
		Log.d(TAG, "WidgetProvider.unregisterContentObserver()");
		if (calendarInstancesObserver != null)
			context.getContentResolver().unregisterContentObserver(
					calendarInstancesObserver);
	}

	private void registerContentObserver(final Context context) {
		if (calendarInstancesObserver == null) {
			final ComponentName name = new ComponentName(context,
					this.getClass());

			calendarInstancesObserver = new ContentObserver(new Handler()) {
				@Override
				public void onChange(boolean selfChange) {
					Log.d(TAG, "ContentObserver.onChange()");
					AppWidgetManager m = AppWidgetManager.getInstance(context);
					int[] ids = m.getAppWidgetIds(name);
					onUpdate(context, m, ids);
				}
			};
		}
		Log.d(TAG, "WidgetProvider.registerContentObserver()");
		String uriString = context.getResources().getString(
				R.string.calendar_instances_uri);
		Uri instancesUri = Uri.parse(uriString);
		context.getContentResolver().registerContentObserver(instancesUri,
				true, calendarInstancesObserver);
	}

	private void updateWidget(final Context context,
			final AppWidgetManager manager, final int id) {

		Log.d(TAG, "WidgetProvider.updateContents(" + id + ")");

		Time nextUpdate = new Time();
		nextUpdate.setToNow();
		nextUpdate.monthDay += 1;
		nextUpdate.hour = nextUpdate.minute = nextUpdate.second = 0;
		nextUpdate.normalize(false);

		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(id);
		RemoteViews widget = new RemoteViews(context.getPackageName(),
				widgetInfo.initialLayout);

		widget.removeAllViews(R.id.events);

		CursorManager cursor = new CursorManager(context);

		boolean birthdayLeft = true;
		int birthdayLines = 0;
		int eventLines = 0;
		int maxLines = getLineCount();
		RemoteViews birthdayView = null;

		for (int position = 0; position < (maxLines * 4); position++) {
			cursor.moveToNext();
			if (cursor.isBirthday) {
				if (birthdayLeft) {
					birthdayView = new RemoteViews(context.getPackageName(),
							R.layout.widget_two_birthdays);
					widget.addView(R.id.birthdays, birthdayView);
					birthdayLines++;
				}
				birthdayView.setTextViewText(birthdayLeft ? R.id.birthday1_time
						: R.id.birthday2_time, cursor.time);
				birthdayView.setTextViewText(
						birthdayLeft ? R.id.birthday1_title
								: R.id.birthday2_title, cursor.title);

				if (!birthdayLeft && eventLines + birthdayLines >= maxLines)
					break;
				birthdayLeft = !birthdayLeft;
			} else if (eventLines + birthdayLines < maxLines) {
				eventLines++;
				RemoteViews event = new RemoteViews(context.getPackageName(),
						R.layout.widget_event);

				event.setTextViewText(R.id.event_title, cursor.title);
				if (cursor.eventLocation == null)
					event.setViewVisibility(R.id.event_comma, View.GONE);

				event.setTextViewText(R.id.event_location, cursor.eventLocation);
				event.setTextViewText(R.id.event_time, cursor.time);
				if (!cursor.allDay && cursor.end.before(nextUpdate))
					nextUpdate = cursor.end;
				event.setTextColor(R.id.event_color, cursor.color);
				widget.addView(R.id.events, event);
			}
		}

		manager.updateAppWidget(id, widget);

		Intent intent = new Intent();
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.setComponent(new ComponentName(context, this.getClass()));
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] { id });
		PendingIntent pendingintent = PendingIntent.getBroadcast(context, 0,
				intent, 0);
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC, nextUpdate.toMillis(false) + 600,
				pendingintent);
	}

}
