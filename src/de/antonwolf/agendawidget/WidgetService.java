package de.antonwolf.agendawidget;

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
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RemoteViews;

public final class WidgetService extends IntentService {
	private static final String TAG = "AgendaWidget";
	private static final String THEAD_NAME = "WidgetServiceThead";

	private static Time dayStart;
	private static Time dayEnd;
	private static Time oneWeekFromNow;
	private static Time yearStart;
	private static Time yearEnd;

	private static Pattern[] birthdayPatterns;

	private final static String CURSOR_FORMAT = "content://com.android.calendar/instances/when/%1$s/%2$s";
	private final static long SEARCH_DURATION = 2 * DateUtils.YEAR_IN_MILLIS;
	private final static String CURSOR_SORT = "startDay ASC, allDay DESC, begin ASC, Instances._id ASC";
	private final static String[] CURSOR_PROJECTION = new String[] { "title",
			"color", "eventLocation", "allDay", "startDay", "startMinute",
			"endDay", "endMinute", "eventTimezone", "end", "hasAlarm" };
	private final static int COLUMN_TITLE = 0;
	private final static int COLUMN_COLOR = 1;
	private final static int COLUMN_LOCATION = 2;
	private final static int COLUMN_ALL_DAY = 3;
	private final static int COLUMN_START_DAY = 4;
	private final static int COLUMN_START_MINUTE = 5;
	private final static int COLUMN_END_DAY = 6;
	private final static int COLUMN_END_MINUTE = 7;
	private final static int COLUMN_TIMEZONE = 8;
	private final static int COLUMN_END = 9;
	private final static int COLUMN_HAS_ALARM = 10;

	public WidgetService() {
		super(THEAD_NAME);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(TAG, "Handling " + intent);

		int id = Integer.parseInt(intent.getData().getHost());
		AppWidgetManager manager = AppWidgetManager
				.getInstance(getApplicationContext());
		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(id);
		if (null == widgetInfo) {
			Log.d(TAG, "Invalid widget ID: " + id);
			return;
		}
		Cursor cursor = null;

		try {
			cursor = getCursor();

			updateTimeRanges();

			long nextUpdate = dayEnd.toMillis(false);

			LinkedList<RemoteViews> events = new LinkedList<RemoteViews>();
			LinkedList<RemoteViews> birthdays = new LinkedList<RemoteViews>();

			boolean bdayLeft = true;
			
			WindowManager winManager = (WindowManager) getSystemService(WINDOW_SERVICE);
			DisplayMetrics metrics = new DisplayMetrics();
			winManager.getDefaultDisplay().getMetrics(metrics);
			int heightInCells = (int) (widgetInfo.minHeight/metrics.density + 2) / 74;
			int maxLines = 5 + (int) ((heightInCells - 1) * 6.6);

			for (int position = 0; position < (maxLines * 4); position++) {
				if (bdayLeft && events.size() + birthdays.size() >= maxLines)
					break;
				if (!cursor.moveToNext())
					break;

				String title = cursor.getString(COLUMN_TITLE);
				String bdayTitle = getBirthday(title);
				String time = formatTime(getStart(cursor), getEnd(cursor),
						1 == cursor.getInt(COLUMN_ALL_DAY));

				if (bdayTitle != null) {
					if (bdayLeft)
						birthdays.addLast(new RemoteViews(getPackageName(),
								R.layout.widget_two_birthdays));
					int timeView = bdayLeft ? R.id.birthday1_time
							: R.id.birthday2_time;
					birthdays.getLast().setTextViewText(timeView, time);
					int titleView = bdayLeft ? R.id.birthday1_title
							: R.id.birthday2_title;
					birthdays.getLast().setTextViewText(titleView, bdayTitle);

					bdayLeft = !bdayLeft;
				} else if (events.size() + birthdays.size() < maxLines) {
					RemoteViews event = new RemoteViews(getPackageName(),
							R.layout.widget_event);
					events.addLast(event);

					event.setTextViewText(R.id.event_title, title);
					int commaFlag = cursor.getString(COLUMN_LOCATION) == null ? View.GONE
							: View.VISIBLE;
					event.setViewVisibility(R.id.event_comma, commaFlag);

					event.setTextViewText(R.id.event_location,
							cursor.getString(COLUMN_LOCATION));
					event.setTextViewText(R.id.event_time, time);
					if (1 != cursor.getInt(COLUMN_ALL_DAY)
							&& cursor.getLong(COLUMN_END) < nextUpdate)
						nextUpdate = cursor.getLong(COLUMN_END);
					event.setTextColor(R.id.event_color,
							cursor.getInt(COLUMN_COLOR));

					int alarmFlag = cursor.getInt(COLUMN_HAS_ALARM) == 1 ? View.VISIBLE
							: View.GONE;
					event.setViewVisibility(R.id.event_alarm, alarmFlag);
				}
			}

			RemoteViews widget = new RemoteViews(getApplicationContext()
					.getPackageName(), widgetInfo.initialLayout);
			widget.removeAllViews(R.id.birthdays);
			for (RemoteViews view : birthdays)
				widget.addView(R.id.birthdays, view);
			widget.removeAllViews(R.id.events);
			for (RemoteViews view : events)
				widget.addView(R.id.events, view);
			manager.updateAppWidget(id, widget);

			PendingIntent pending = PendingIntent.getService(
					getApplicationContext(), 0, intent, 0);
			AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			alarmManager.cancel(pending);
			alarmManager.set(AlarmManager.RTC, nextUpdate + 1000, pending);
		} finally {
			if (cursor != null)
				cursor.close();
		}
	}

	private Cursor getCursor() {
		long now = System.currentTimeMillis();
		long end = now + SEARCH_DURATION;
		String uriString = String.format(CURSOR_FORMAT, now, end);
		return getContentResolver().query(Uri.parse(uriString),
				CURSOR_PROJECTION, null, null, CURSOR_SORT);
	}

	private synchronized void updateTimeRanges() {
		if (null != dayEnd
				&& dayEnd.toMillis(false) < System.currentTimeMillis())
			return;

		dayStart = new Time();
		dayStart.setToNow();
		dayStart.hour = dayStart.minute = dayStart.second = 0;

		dayEnd = new Time(dayStart);
		dayEnd.hour = 23;
		dayEnd.minute = dayEnd.second = 59;

		oneWeekFromNow = new Time(dayEnd);
		oneWeekFromNow.monthDay += 7;
		oneWeekFromNow.normalize(false);

		yearEnd = new Time(dayEnd);
		yearEnd.month = 11;
		yearEnd.monthDay = 31;

		yearStart = new Time(dayStart);
		yearStart.month = 0;
		yearStart.monthDay = 1;
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

	private String getBirthday(String title) {
		for (Pattern pattern : getBirthdayPatterns()) {
			Matcher matcher = pattern.matcher(title);
			if (!matcher.find())
				continue;
			return matcher.group(1);
		}
		return null;
	}

	private Time getStart(Cursor cursor) {
		Time value = new Time();
		String timezone = cursor.getString(COLUMN_TIMEZONE);
		if (null != timezone)
			value.timezone = timezone;
		value.setJulianDay(cursor.getInt(COLUMN_START_DAY));
		if (cursor.getInt(COLUMN_ALL_DAY) != 1)
			value.minute = cursor.getInt(COLUMN_START_MINUTE);
		value.normalize(true);
		return value;
	}

	private Time getEnd(Cursor cursor) {
		Time value = new Time();
		String timezone = cursor.getString(COLUMN_TIMEZONE);
		if (null != timezone)
			value.timezone = timezone;
		value.setJulianDay(cursor.getInt(COLUMN_END_DAY));
		if (cursor.getInt(COLUMN_ALL_DAY) != 1)
			value.minute = cursor.getInt(COLUMN_END_MINUTE);
		value.normalize(true);
		return value;
	}

	private String formatTime(Time start, Time end, boolean allDay) {
		String startDay;

		if (!allDay && isToday(start) && isToday(end))
			startDay = "";
		else if (isToday(start))
			startDay = start.format(getResources().getString(
					R.string.format_today));
		else if (isThisWeek(start))
			startDay = getResources().getBoolean(
					R.bool.format_this_week_remove_dot) ? start.format(
					getResources().getString(R.string.format_this_week))
					.replace(".", "") : start.format(getResources().getString(
					R.string.format_this_week));
		else if (isThisYear(start))
			startDay = start.format(getResources().getString(
					R.string.format_this_year));
		else
			startDay = start.format(getResources().getString(
					R.string.format_other));

		String startHour = allDay ? "" : start.format(getResources().getString(
				R.string.format_time));
		String startTime = startDay
				+ ((startDay == "" || startHour == "") ? "" : " ") + startHour;

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
				endDay = end.format(getResources().getString(
						R.string.format_today));
			else if (isThisWeek(end))
				endDay = getResources().getBoolean(
						R.bool.format_this_week_remove_dot) ? end.format(
						getResources().getString(R.string.format_this_week))
						.replace(".", "") : end.format(getResources()
						.getString(R.string.format_this_week));
			else if (isThisYear(end))
				endDay = end.format(getResources().getString(
						R.string.format_this_year));
			else
				endDay = end.format(getResources().getString(
						R.string.format_other));

			String endHour = allDay ? "" : end.format(getResources().getString(
					R.string.format_time));
			String endTime = endDay
					+ ((endDay == "" || endHour == "") ? "" : " ") + endHour;
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

	private boolean isThisYear(Time time) {
		return Time.compare(time, yearEnd) <= 0
				&& Time.compare(yearStart, time) <= 0;
	}

}
