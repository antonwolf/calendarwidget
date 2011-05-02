package de.antonwolf.agendawidget;

import java.util.ArrayList;
import java.util.Formatter;
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

	private final static String COLOR_DOT = "●\t";
	private final static String COLOR_HIDDEN = "\t";
	private final static String SEPARATOR_COMMA = ", ";

	private final static Pattern IS_EMPTY_PATTERN = Pattern.compile("^\\s*$");

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
		final WidgetPreferences prefs = new WidgetPreferences(widgetId, this);
		final int maxLines = prefs.getLines();
		final List<Event> birthdayEvents = new ArrayList<Event>(maxLines * 2);
		final List<Event> agendaEvents = new ArrayList<Event>(maxLines);

		Cursor cursor = null;
		try {
			cursor = getCursor();

			while (birthdayEvents.size() / 2 + agendaEvents.size() < maxLines) {
				Event event = null;
				while (event == null && !cursor.isAfterLast())
					event = readEvent(cursor, prefs);
				if (event == null)
					break;

				if (event.isBirthday) {
					if (!birthdayEvents.contains(event))
						birthdayEvents.add(event);
				} else
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

		final boolean calendarColor = prefs.isCalendarColor();

		for (int i = 0; (i < maxLines - agendaEvents.size())
				&& (i <= birthdayEvents.size() - 1); i++) {
			final RemoteViews view = new RemoteViews(packageName,
					R.layout.birthdays);
			view.setTextViewText(R.id.birthday1_text,
					formatEventText(birthdayEvents.get(i * 2), calendarColor));
			if (i <= birthdayEvents.size() - 2)
				view.setTextViewText(R.id.birthday2_text,
						formatEventText(birthdayEvents.get(i * 2 + 1), false));
			else
				view.setTextViewText(R.id.birthday2_text, "");
			widget.addView(R.id.widget, view);
		}

		for (Event event : agendaEvents) {
			final RemoteViews view = new RemoteViews(packageName,
					R.layout.event);
			view.setTextViewText(R.id.event_text,
					formatEventText(event, calendarColor));
			int alarmFlag = event.hasAlarm ? View.VISIBLE : View.GONE;
			view.setViewVisibility(R.id.event_alarm, alarmFlag);
			widget.addView(R.id.widget, view);
		}
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

	private Event readEvent(final Cursor cursor, final WidgetPreferences prefs) {
		if (!cursor.moveToNext())
			return null; // no next item
		if (!prefs.isCalendar(cursor.getInt(COL_CALENDAR)))
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

		if (event.allDay
				&& !prefs.getBirthdays().equals(
						WidgetPreferences.BIRTHDAY_NORMAL))
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
			final boolean showColor) {
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
		formatTime(builder, event);
		builder.append(' ');
		final int timeEndPos = builder.length();
		builder.setSpan(new ForegroundColorSpan(0xaaffffff), timeStartPos,
				timeEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		builder.append(event.title);
		final int titleEndPos = builder.length();
		builder.setSpan(new ForegroundColorSpan(0xffffffff), timeEndPos,
				titleEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		if (event.location != null) {
			builder.append(SEPARATOR_COMMA);
			builder.append(event.location);
			builder.setSpan(new ForegroundColorSpan(0xaaffffff), titleEndPos,
					builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		return builder;
	}

	private void formatTime(final SpannableStringBuilder builder,
			final Event event) {
		final Formatter formatter = new Formatter(builder);

		final boolean isStartToday = (todayStart <= event.startMillis && event.startMillis <= tomorrowStart);
		final boolean isEndToday = (todayStart <= event.endMillis && event.endMillis <= tomorrowStart);
		final boolean showStartDay = !isStartToday || !isEndToday
				|| event.allDay;

		// all-Day events
		if (event.allDay) {
			if (showStartDay)
				appendDay(formatter, builder, event.startMillis,
						event.startTime);

			if (event.startDay != event.endDay) {
				builder.append('-');
				appendDay(formatter, builder, event.endMillis, event.endTime);
			}
			return;
		}

		// events with no duration
		if (event.startMillis == event.endMillis) {
			if (showStartDay) {
				appendDay(formatter, builder, event.startMillis,
						event.startTime);
				builder.append(' ');
			}
			appendHour(formatter, builder, event.startMillis);
			return;
		}

		// events with duration
		if (showStartDay) {
			appendDay(formatter, builder, event.startMillis, event.startTime);
			builder.append(' ');
		}
		appendHour(formatter, builder, event.startMillis);
		builder.append('-');

		final Time helper = new Time();
		helper.timezone = Time.getCurrentTimezone();
		long endOfStartDay = helper.setJulianDay(event.endDay + 1);
		if (event.endMillis >= endOfStartDay) {
			appendDay(formatter, builder, event.endMillis, event.endTime);
			builder.append(' ');
		}
		appendHour(formatter, builder, event.endMillis);
	}

	private void appendHour(final Formatter formatter,
			final SpannableStringBuilder builder, final long time) {
		if (getResources().getBoolean(R.bool.format_24hours))
			formatter.format("%tk:%<tM", time);
		else {
			formatter.format("%tl:%<tM%<tp", time);
			int len = builder.length();
			builder.setSpan(new RelativeSizeSpan(0.7f), len - 2, len, 0);
		}
	}

	private void appendDay(final Formatter formatter,
			final SpannableStringBuilder builder, final long time,
			final Time day) {
		if (yesterdayStart <= time && time < dayAfterTomorrowStart) {
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
