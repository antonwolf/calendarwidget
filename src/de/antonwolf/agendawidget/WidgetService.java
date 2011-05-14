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

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public final class WidgetService extends IntentService {
	private final static class Event {
		public boolean allDay = false;
		public int color;
		public int endDay;
		public long endMillis;
		public Time endTime;
		public boolean hasAlarm;
		public boolean isBirthday = false;
		public String location;
		public long startMillis;
		public Time startTime;
		public int startDay;
		public String title;

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Event))
				return false;

			final Event other = (Event) o;

			return isBirthday && other.isBirthday
					&& other.startDay == this.startDay
					&& other.title.equals(this.title);
		}
	}

	private static final String TAG = "AgendaWidget";
	private static final String THEAD_NAME = "WidgetServiceThead";

	private static long yesterdayStart;
	private static long todayStart;
	private static long tomorrowStart;
	private static long dayAfterTomorrowStart;
	private static long oneWeekFromNow;
	private static long yearStart;
	private static long yearEnd;

	private static Pattern[] birthdayPatterns;

	private final static String CURSOR_FORMAT = "content://com.android.calendar/instances/when/%1$s/%2$s";
	private final static long SEARCH_DURATION = 2 * DateUtils.YEAR_IN_MILLIS;
	private final static String CURSOR_SORT = "begin ASC, end DESC, title ASC";
	private final static String[] CURSOR_PROJECTION = new String[] { "title",
			"color", "eventLocation", "allDay", "startDay", "endDay", "end",
			"hasAlarm", "calendar_id", "begin" };
	private final static int COL_TITLE = 0;
	private final static int COL_COLOR = 1;
	private final static int COL_LOCATION = 2;
	private final static int COL_ALL_DAY = 3;
	private final static int COL_START_DAY = 4;
	private final static int COL_END_DAY = 5;
	private final static int COL_END_MILLIS = 6;
	private final static int COL_HAS_ALARM = 7;
	private final static int COL_CALENDAR = 8;
	private final static int COL_START_MILLIS = 9;

	private final static String COLOR_DOT = "■\t";
	private final static String COLOR_HIDDEN = "\t";
	private final static String SEPARATOR_COMMA = ", ";

	private final static long DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

	private final static Pattern IS_EMPTY_PATTERN = Pattern.compile("^\\s*$");
	private final static int[] BACKGROUNDS = new int[] {
			R.drawable.background_0, R.drawable.background_20,
			R.drawable.background_40, R.drawable.background_60,
			R.drawable.background_80, R.drawable.background_100 };
	private final static int DATETIME_COLOR = 0xb8ffffff;

	public WidgetService() {
		super(THEAD_NAME);
	}

	@Override
	protected synchronized void onHandleIntent(final Intent intent) {
		Log.d(TAG, "Handling " + intent);

		final int widgetId = Integer.parseInt(intent.getData().getHost());
		final AppWidgetManager manager = AppWidgetManager.getInstance(this);
		final AppWidgetProviderInfo widgetInfo = manager
				.getAppWidgetInfo(widgetId);

		if (null == widgetInfo) {
			Log.d(TAG, "Invalid widget ID!");
			return;
		}
		computeTimeRanges();
		final WidgetInfo info = new WidgetInfo(widgetId, this);
		final int maxLines = Integer.parseInt(info.lines);
		final List<Event> birthdayEvents = new ArrayList<Event>(maxLines * 2);
		final List<Event> agendaEvents = new ArrayList<Event>(maxLines);

		Cursor cursor = null;
		try {
			cursor = getCursor();

			while (true) {
				boolean widgetFull = Math.ceil(birthdayEvents.size() / 2.0)
						+ agendaEvents.size() >= maxLines;
				boolean evenBirthdayCount = birthdayEvents.size() % 2 == 0;
				if (widgetFull && evenBirthdayCount)
					break; // widget is full

				Event event = null;
				while (event == null && !cursor.isAfterLast())
					event = readEvent(cursor, info);
				if (event == null)
					break; // no further events

				if (event.isBirthday) {
					if (!birthdayEvents.contains(event))
						birthdayEvents.add(event);
				} else if (!widgetFull)
					agendaEvents.add(event);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}

		final String packageName = getPackageName();
		final RemoteViews widget = new RemoteViews(getPackageName(),
				widgetInfo.initialLayout);
		widget.removeAllViews(R.id.widget);
		widget.setOnClickPendingIntent(R.id.widget,
				getOnClickPendingIntent(widgetId));

		final boolean calendarColor = info.calendarColor;

		Iterator<Event> bdayIterator = birthdayEvents.iterator();
		while (bdayIterator.hasNext()) {
			final RemoteViews view = new RemoteViews(packageName,
					R.layout.birthdays);
			view.setTextViewText(R.id.birthday1_text,
					formatEventText(bdayIterator.next(), calendarColor, info));
			if (bdayIterator.hasNext())
				view.setTextViewText(R.id.birthday2_text,
						formatEventText(bdayIterator.next(), false, info));
			else
				view.setTextViewText(R.id.birthday2_text, "");
			widget.addView(R.id.widget, view);
		}

		for (Event event : agendaEvents) {
			final RemoteViews view = new RemoteViews(packageName,
					R.layout.event);
			view.setTextViewText(R.id.event_text,
					formatEventText(event, calendarColor, info));
			int alarmFlag = event.hasAlarm ? View.VISIBLE : View.GONE;
			view.setViewVisibility(R.id.event_alarm, alarmFlag);
			widget.addView(R.id.widget, view);
		}

		final int opacityIndex = Integer.parseInt(info.opacity) / 20;
		final int background = BACKGROUNDS[opacityIndex];
		widget.setInt(R.id.widget, "setBackgroundResource", background);

		manager.updateAppWidget(widgetId, widget);
		scheduleNextUpdate(agendaEvents, intent);
	}

	private void scheduleNextUpdate(final List<Event> search,
			final Intent intent) {
		long nextUpdate = tomorrowStart;
		for (Event event : search)
			if (!event.allDay && event.endMillis < nextUpdate)
				nextUpdate = event.endMillis;

		PendingIntent pending = PendingIntent.getService(this, 0, intent, 0);
		AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmManager.cancel(pending);
		alarmManager.set(AlarmManager.RTC, nextUpdate + 1000, pending);
	}

	private Event readEvent(final Cursor cursor, final WidgetInfo info) {
		if (!cursor.moveToNext())
			return null; // no next item
		if (!info.calendars.get(cursor.getInt(COL_CALENDAR)).enabled)
			return null; // Calendar is disabled

		final Event event = new Event();

		if (1 == cursor.getInt(COL_ALL_DAY))
			event.allDay = true;

		event.endDay = cursor.getInt(COL_END_DAY);
		event.endTime = new Time();

		if (event.allDay) {
			event.endTime.timezone = Time.getCurrentTimezone();
			event.endMillis = event.endTime.setJulianDay(event.endDay);
		} else {
			event.endMillis = cursor.getLong(COL_END_MILLIS);
			event.endTime.set(event.endMillis);
		}
		if ((event.allDay && event.endMillis < todayStart)
				|| (!event.allDay && event.endMillis <= System
						.currentTimeMillis()))
			return null; // Skip events in the past

		event.title = cursor.getString(COL_TITLE);
		if (event.title == null)
			event.title = "";

		if (event.allDay && !info.birthdays.equals(WidgetInfo.BIRTHDAY_NORMAL))
			for (Pattern pattern : getBirthdayPatterns()) {
				Matcher matcher = pattern.matcher(event.title);
				if (!matcher.find())
					continue;
				event.title = matcher.group(1);
				event.isBirthday = true;
				break;
			}

		// Skip birthday events if necessary
		if (event.isBirthday && info.birthdays.equals(WidgetInfo.BIRTHDAY_HIDE))
			return null;

		event.startDay = cursor.getInt(COL_START_DAY);
		event.startTime = new Time();
		if (event.allDay) {
			event.startTime.timezone = event.endTime.timezone;
			event.startMillis = event.startTime.setJulianDay(event.startDay);
		} else {
			event.startMillis = cursor.getLong(COL_START_MILLIS);
			event.startTime.set(event.startMillis);
		}

		event.location = cursor.getString(COL_LOCATION);
		if (event.location != null
				&& IS_EMPTY_PATTERN.matcher(event.location).find())
			event.location = null;

		event.color = cursor.getInt(COL_COLOR);
		event.hasAlarm = cursor.getInt(COL_HAS_ALARM) == 1;
		return event;
	}

	private PendingIntent getOnClickPendingIntent(final int widgetId) {
		final Intent pickAction = new Intent("pick", Uri.parse("widget://"
				+ widgetId), this, PickActionActivity.class);
		pickAction.putExtra(PickActionActivity.EXTRA_WIDGET_ID, widgetId);
		return PendingIntent.getActivity(this, 0, pickAction, 0);
	}

	private Cursor getCursor() {
		final long start = todayStart - 1000 * 60 * 60 * 24;
		final long end = start + SEARCH_DURATION;
		final String uriString = String.format(CURSOR_FORMAT, start, end);
		return getContentResolver().query(Uri.parse(uriString),
				CURSOR_PROJECTION, null, null, CURSOR_SORT);
	}

	private void computeTimeRanges() {
		final Time now = new Time();
		now.setToNow();
		final int julianDay = Time.getJulianDay(System.currentTimeMillis(),
				now.gmtoff);

		yearStart = now.setJulianDay(julianDay - now.yearDay);
		now.year++;
		yearEnd = now.toMillis(false);
		yesterdayStart = now.setJulianDay(julianDay - 1);
		todayStart = now.setJulianDay(julianDay);
		tomorrowStart = now.setJulianDay(julianDay + 1);
		dayAfterTomorrowStart = now.setJulianDay(julianDay + 2);
		oneWeekFromNow = now.setJulianDay(julianDay + 8);
	}

	private synchronized Pattern[] getBirthdayPatterns() {
		if (birthdayPatterns == null) {
			String[] strings = getResources().getStringArray(
					R.array.birthday_patterns);
			birthdayPatterns = new Pattern[strings.length];
			for (int i = 0; i < strings.length; i++) {
				birthdayPatterns[i] = Pattern.compile(strings[i]);
			}
		}
		return birthdayPatterns;
	}

	private CharSequence formatEventText(final Event event,
			final boolean showColor, final WidgetInfo info) {
		if (event == null)
			return "";

		final SpannableStringBuilder builder = new SpannableStringBuilder();

		if (showColor) {
			if (event.isBirthday)
				builder.append(COLOR_HIDDEN);
			else {
				builder.append(COLOR_DOT);
				builder.setSpan(new ForegroundColorSpan(event.color), 0, 1,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}

		final int timeStartPos = builder.length();
		formatTime(builder, event, info);
		builder.append(' ');
		final int timeEndPos = builder.length();
		builder.setSpan(new ForegroundColorSpan(DATETIME_COLOR), timeStartPos,
				timeEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		builder.append(event.title);
		final int titleEndPos = builder.length();
		builder.setSpan(new ForegroundColorSpan(0xffffffff), timeEndPos,
				titleEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		if (event.location != null) {
			builder.append(SEPARATOR_COMMA);
			builder.append(event.location);
			builder.setSpan(new ForegroundColorSpan(DATETIME_COLOR),
					titleEndPos, builder.length(),
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}

		final float size = Integer.parseInt(info.size) / 100f;
		builder.setSpan(new RelativeSizeSpan(size), 0, builder.length(), 0);

		return builder;
	}

	private void formatTime(final SpannableStringBuilder builder,
			final Event event, final WidgetInfo info) {
		final Formatter formatter = new Formatter(builder);

		final boolean isStartToday = (todayStart <= event.startMillis && event.startMillis <= tomorrowStart);
		final boolean isEndToday = (todayStart <= event.endMillis && event.endMillis <= tomorrowStart);
		final boolean showStartDay = !isStartToday || !isEndToday
				|| event.allDay;

		// all-Day events
		if (event.allDay) {
			if (showStartDay)
				appendDay(formatter, builder, event.startMillis,
						event.startTime, info);

			if (event.startDay != event.endDay) {
				builder.append('-');
				appendDay(formatter, builder, event.endMillis, event.endTime,
						info);
			}
			return;
		}

		// events with no duration
		if (!info.endTime || event.startMillis == event.endMillis) {
			if (showStartDay) {
				appendDay(formatter, builder, event.startMillis,
						event.startTime, info);
				builder.append(' ');
			}
			appendHour(formatter, builder, event.startMillis, info);
			return;
		}

		// events with duration
		if (showStartDay) {
			appendDay(formatter, builder, event.startMillis, event.startTime,
					info);
			builder.append(' ');
		}
		appendHour(formatter, builder, event.startMillis, info);
		builder.append('-');

		if (Math.abs(event.endMillis - event.startMillis) > DAY_IN_MILLIS) {
			appendDay(formatter, builder, event.endMillis, event.endTime, info);
			builder.append(' ');
		}
		appendHour(formatter, builder, event.endMillis, info);
	}

	private void appendHour(final Formatter formatter,
			final SpannableStringBuilder builder, final long time,
			WidgetInfo info) {
		if (info.twentyfourHours)
			formatter.format("%1$tk:%1$tM", time);
		else {
			formatter.format("%1$tl:%1$tM", time);
			int start = builder.length();
			formatter.format("%1$tp", time);
			int end = builder.length();
			builder.setSpan(new RelativeSizeSpan(0.7f), start, end, 0);
		}
	}

	private void appendDay(final Formatter formatter,
			final SpannableStringBuilder builder, final long time,
			final Time day, final WidgetInfo info) {
		final boolean tomorrowYesterday = info.tomorrowYesterday;
		final long specialStart = tomorrowYesterday ? yesterdayStart
				: todayStart;
		final long specialEnd = tomorrowYesterday ? dayAfterTomorrowStart
				: tomorrowStart;
		final boolean weekday = info.weekday;
		final long weekEnd = weekday ? oneWeekFromNow : tomorrowStart;

		if (specialStart <= time && time < specialEnd) {
			final int from = builder.length();
			if (time < todayStart)
				builder.append(getResources().getString(
						R.string.format_yesterday));
			else if (time < tomorrowStart)
				builder.append(getResources().getString(R.string.format_today));
			else
				builder.append(getResources().getString(
						R.string.format_tomorrow));

			final RelativeSizeSpan smaller = new RelativeSizeSpan(0.7f);
			builder.setSpan(smaller, from, builder.length(), 0);
		} else if (todayStart <= time && time < weekEnd) // this week?
			builder.append(getResources().getStringArray(
					R.array.format_day_of_week)[day.weekDay]);
		else if (yearStart <= time && time < yearEnd) // this year?
			formatter.format(info.dateFormat.shortFormat, time);
		else
			// not this year
			formatter.format(info.dateFormat.longFormat, time);
	}
}
