package de.antonwolf.agendawidget;

import java.util.Formatter;
import java.util.LinkedList;
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

	private final static Pattern isEmpty = Pattern.compile("^\\s*$");

	public WidgetService() {
		super(THEAD_NAME);
	}

	@Override
	protected synchronized void onHandleIntent(Intent intent) {
		Log.d(TAG, "Handling " + intent);

		int widgetId = Integer.parseInt(intent.getData().getHost());
		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(widgetId);

		if (null == widgetInfo) {
			Log.d(TAG, "Invalid widget ID: " + widgetId);
			return;
		}

		WidgetPreferences prefs = new WidgetPreferences(widgetId, this);

		updateTimeRanges();

		String packageName = getPackageName();

		long nextUpdate = tomorrowStart;

		LinkedList<RemoteViews> events = new LinkedList<RemoteViews>();
		LinkedList<RemoteViews> birthdays = new LinkedList<RemoteViews>();

		boolean bdayLeft = true;

		int maxLines = prefs.getLines();

		Cursor cursor = null;

		try {
			cursor = getCursor();

			while (!bdayLeft || ((events.size() + birthdays.size()) < maxLines)) {
				Event event = readEvent(cursor, prefs);
				// no next item? abort!
				if (event == null)
					break;

				if (!event.allDay && event.endMillis < nextUpdate)
					nextUpdate = event.endMillis;

				SpannableStringBuilder builder = new SpannableStringBuilder();

				formatTime(builder, event);
				builder.append(' ');
				int timeEndPos = builder.length();
				builder.setSpan(new ForegroundColorSpan(0xaaffffff), 0,
						timeEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				builder.append(event.title);
				int titleEndPos = builder.length();
				builder.setSpan(new ForegroundColorSpan(0xffffffff),
						timeEndPos, titleEndPos,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				if (event.location != null) {
					builder.append(", ");
					builder.append(event.location);
					builder.setSpan(new ForegroundColorSpan(0xaaffffff),
							titleEndPos, builder.length(),
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}

				if (event.isBirthday) {
					if (bdayLeft)
						birthdays.addLast(new RemoteViews(packageName,
								R.layout.birthdays));

					int view = bdayLeft ? R.id.birthday1_text
							: R.id.birthday2_text;
					birthdays.getLast().setTextViewText(view, builder);

					bdayLeft = !bdayLeft;
				} else {
					if (events.size() + birthdays.size() >= maxLines)
						continue;
					RemoteViews eventView = new RemoteViews(packageName,
							R.layout.event);
					events.addLast(eventView);

					eventView.setTextViewText(R.id.event_text, builder);

					eventView.setTextColor(R.id.event_color, event.color);

					int alarmFlag = event.hasAlarm ? View.VISIBLE : View.GONE;
					eventView.setViewVisibility(R.id.event_alarm, alarmFlag);
				}
			}

			RemoteViews widget = new RemoteViews(getPackageName(),
					widgetInfo.initialLayout);
			widget.removeAllViews(R.id.widget);
			for (RemoteViews view : birthdays)
				widget.addView(R.id.widget, view);
			for (RemoteViews view : events)
				widget.addView(R.id.widget, view);

			Intent pickAction = new Intent("pick", Uri.parse("widget://"
					+ widgetId), this, PickActionActivity.class);
			pickAction.putExtra(PickActionActivity.EXTRA_WIDGET_ID, widgetId);
			PendingIntent pickPending = PendingIntent.getActivity(this, 0,
					pickAction, 0);
			widget.setOnClickPendingIntent(R.id.widget, pickPending);

			manager.updateAppWidget(widgetId, widget);

			PendingIntent pending = PendingIntent
					.getService(this, 0, intent, 0);
			AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			alarmManager.cancel(pending);
			alarmManager.set(AlarmManager.RTC, nextUpdate + 1000, pending);
		} finally {
			if (cursor != null)
				cursor.close();
		}
	}

	private Event readEvent(Cursor cur, WidgetPreferences prefs) {
		if (!cur.moveToNext())
			return null;

		// Calendar is disabled, return next row
		if (!prefs.isCalendar(cur.getInt(COL_CALENDAR)))
			return readEvent(cur, prefs);

		Event event = new Event();

		if (1 == cur.getInt(COL_ALL_DAY))
			event.allDay = true;

		event.endDay = cur.getInt(COL_END_DAY);
		event.endTime = new Time();

		if (event.allDay) {
			event.endTime.timezone = Time.getCurrentTimezone();
			event.endMillis = event.endTime.setJulianDay(event.endDay);
		} else {
			event.endMillis = cur.getLong(COL_END_MILLIS);
			event.endTime.set(event.endMillis);
		}

		// Skip events in the past
		if ((event.allDay && event.endMillis < todayStart)
				|| (!event.allDay && event.endMillis <= System
						.currentTimeMillis()))
			return readEvent(cur, prefs);

		event.title = cur.getString(COL_TITLE);
		if (event.title == null)
			event.title = "";

		if (!prefs.getBirthdays().equals(WidgetPreferences.BIRTHDAY_NORMAL))
			for (Pattern pattern : getBirthdayPatterns()) {
				Matcher matcher = pattern.matcher(event.title);
				if (!matcher.find())
					continue;
				event.title = matcher.group(1);
				event.isBirthday = true;
				break;
			}

		// Skip birthday events if necessary
		if (event.isBirthday
				&& prefs.getBirthdays().equals(WidgetPreferences.BIRTHDAY_HIDE))
			return readEvent(cur, prefs);

		event.startDay = cur.getInt(COL_START_DAY);
		event.startTime = new Time();
		if (event.allDay) {
			event.startTime.timezone = event.endTime.timezone;
			event.startMillis = event.startTime.setJulianDay(event.startDay);
		} else {
			event.startMillis = cur.getLong(COL_START_MILLIS);
			event.startTime.set(event.startMillis);
		}

		event.location = cur.getString(COL_LOCATION);
		if (event.location != null && isEmpty.matcher(event.location).find())
			event.location = null;

		event.color = cur.getInt(COL_COLOR);

		event.hasAlarm = cur.getInt(COL_HAS_ALARM) == 1;

		return event;
	}

	private Cursor getCursor() {
		long start = todayStart - 1000 * 60 * 60 * 24;
		long end = start + SEARCH_DURATION;
		String uriString = String.format(CURSOR_FORMAT, start, end);
		return getContentResolver().query(Uri.parse(uriString),
				CURSOR_PROJECTION, null, null, CURSOR_SORT);
	}

	private void updateTimeRanges() {
		Time now = new Time();
		now.setToNow();
		int julianDay = Time.getJulianDay(System.currentTimeMillis(),
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

	private void formatTime(SpannableStringBuilder builder, Event event) {
		Formatter formatter = new Formatter(builder);

		boolean isStartToday = (todayStart <= event.startMillis && event.startMillis <= tomorrowStart);
		boolean isEndToday = (todayStart <= event.endMillis && event.endMillis <= tomorrowStart);
		boolean showStartDay = !isStartToday || !isEndToday || event.allDay;

		//all-Day events
		if (event.allDay) {
			if (showStartDay)
				appendDay(formatter, builder, event.startMillis, event.startTime);
			
			if (event.startDay != event.endDay) {
				builder.append('-');
				appendDay(formatter, builder, event.endMillis, event.endTime);
			}
			return;
		}

		//events with no duration
		if (event.startMillis == event.endMillis) {
			if (showStartDay) {
				appendDay(formatter, builder, event.startMillis, event.startTime);
				builder.append(' ');
			}
			appendHour(formatter, builder, event.startMillis);
			return;
		}

		//events with duration
		if (showStartDay) {
			appendDay(formatter, builder, event.startMillis, event.startTime);
			builder.append(' ');
		}
		appendHour(formatter, builder, event.startMillis);
		builder.append('-');
		
		Time helper = new Time();
		helper.timezone = Time.getCurrentTimezone();
		long endOfStartDay = helper.setJulianDay(event.endDay+1);
		if (event.endMillis >= endOfStartDay) {
			appendDay(formatter, builder, event.endMillis, event.endTime);
			builder.append(' ');
		}
		appendHour(formatter, builder, event.endMillis);
	}

	private void appendHour(Formatter formatter,
			SpannableStringBuilder builder, long time) {
		if (getResources().getBoolean(R.bool.format_24hours))
			formatter.format("%tk:%<tM", time);
		else {
			formatter.format("%tl:%<tM%<tp", time);
			int len = builder.length();
			builder.setSpan(new RelativeSizeSpan(0.7f), len - 2, len, 0);
		}
	}

	private void appendDay(Formatter formatter, SpannableStringBuilder builder,
			long time, Time day) {
		if (yesterdayStart <= time && time < dayAfterTomorrowStart) {
			int from = builder.length();
			if (time < todayStart)
				builder.append(getResources().getString(
						R.string.format_yesterday));
			else if (time < tomorrowStart)
				builder.append(getResources().getString(R.string.format_today));
			else
				builder.append(getResources().getString(
						R.string.format_tomorrow));
			
			builder.setSpan(new RelativeSizeSpan(0.7f), from, builder.length(), 0);
		} else if (todayStart <= time && time < oneWeekFromNow) // this week?
			builder.append(getResources().getStringArray(
					R.array.format_day_of_week)[day.weekDay]);
		else if (yearStart <= time && time < yearEnd) { // this year?
			formatter.format(getResources()
					.getString(R.string.format_this_year), time);
		} else
			// other time
			formatter.format(getResources().getString(R.string.format_other),
					time);
	}
}
