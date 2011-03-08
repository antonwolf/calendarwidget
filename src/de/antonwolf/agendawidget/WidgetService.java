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
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public final class WidgetService extends IntentService {
	private static final String TAG = "AgendaWidget";
	private static final String THEAD_NAME = "WidgetServiceThead";

	private static long dayStart;
	private static long dayEnd = Long.MAX_VALUE;
	private static long oneWeekFromNow;
	private static long yearStart;
	private static long yearEnd;

	private static Pattern[] birthdayPatterns;

	private final static String CURSOR_FORMAT = "content://com.android.calendar/instances/when/%1$s/%2$s";
	private final static long SEARCH_DURATION = 2 * DateUtils.YEAR_IN_MILLIS;
	private final static String CURSOR_SORT = "startDay ASC, allDay DESC, begin ASC, Instances._id ASC";
	private final static String[] CURSOR_PROJECTION = new String[] { "title",
			"color", "eventLocation", "allDay", "startDay", "endDay",
			"eventTimezone", "end", "hasAlarm", "calendar_id", "begin" };
	private final static int COL_TITLE = 0;
	private final static int COL_COLOR = 1;
	private final static int COL_LOCATION = 2;
	private final static int COL_ALL_DAY = 3;
	private final static int COL_START_DAY = 4;
	private final static int COL_END_DAY = 5;
	private final static int COL_TIMEZONE = 6;
	private final static int COL_END = 7;
	private final static int COL_HAS_ALARM = 8;
	private final static int COL_CALENDAR = 9;
	private final static int COL_START = 10;

	public WidgetService() {
		super(THEAD_NAME);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.d(TAG, "Handling " + intent);

		int widgetId = Integer.parseInt(intent.getData().getHost());
		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		AppWidgetProviderInfo widgetInfo = manager.getAppWidgetInfo(widgetId);
		if (null == widgetInfo) {
			Log.d(TAG, "Invalid widget ID: " + widgetId);
			return;
		}

		updateTimeRanges();

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		long nextUpdate = dayEnd;

		LinkedList<RemoteViews> events = new LinkedList<RemoteViews>();
		LinkedList<RemoteViews> birthdays = new LinkedList<RemoteViews>();

		boolean bdayLeft = true;

		int maxLines = Integer.parseInt(prefs.getString(widgetId + "lines",
				SettingsActivity.getDefaultLines(widgetId, this)));

		Cursor cur = null;

		try {
			cur = getCursor();

			for (int position = 0; position < (maxLines * 4); position++) {
				if (bdayLeft && events.size() + birthdays.size() >= maxLines)
					break;
				if (!cur.moveToNext())
					break;
				String calPref = widgetId + "calendar"
						+ cur.getInt(COL_CALENDAR);

				if (!prefs.getBoolean(calPref, true)) {
					position--;
					continue;
				}

				long endMillis = cur.getLong(COL_END);
				boolean allDay = 1 == cur.getInt(COL_ALL_DAY);

				if (!allDay && endMillis <= System.currentTimeMillis())
					continue;

				long startMillis = cur.getLong(COL_START);
				String timezone = cur.getString(COL_TIMEZONE);
				if (null == timezone)
					timezone = Time.getCurrentTimezone();

				Time startTime = new Time(timezone);
				Time endTime = new Time(timezone);
				;

				if (allDay) {
					startTime.setJulianDay(cur.getInt(COL_START_DAY));
					endTime.setJulianDay(cur.getInt(COL_END_DAY));
				} else {
					startTime.set(startMillis);
					endTime.set(endMillis);
				}

				String title = cur.getString(COL_TITLE);
				if (title == null)
					title = "";
				String bdayTitle = getBirthday(title);

				CharSequence time = formatTime(startTime, startMillis, endTime,
						endMillis, allDay);

				if (prefs.getBoolean(widgetId + "bDayRecognition", true)
						&& bdayTitle != null) {
					if (!prefs.getBoolean(widgetId + "bDayDisplay", true))
						continue;

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

					String location = cur.getString(COL_LOCATION);
					if (location == null)
						location = "";
					int commaFlag = location.trim() == "" ? View.GONE
							: View.VISIBLE;
					event.setViewVisibility(R.id.event_comma, commaFlag);

					event.setTextViewText(R.id.event_location,
							cur.getString(COL_LOCATION));
					event.setTextViewText(R.id.event_time, time);
					if (1 != cur.getInt(COL_ALL_DAY)
							&& cur.getLong(COL_END) < nextUpdate)
						nextUpdate = cur.getLong(COL_END);
					event.setTextColor(R.id.event_color, cur.getInt(COL_COLOR));

					int alarmFlag = cur.getInt(COL_HAS_ALARM) == 1 ? View.VISIBLE
							: View.GONE;
					event.setViewVisibility(R.id.event_alarm, alarmFlag);
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
			if (cur != null)
				cur.close();
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
		if (dayEnd < System.currentTimeMillis())
			return;

		Time dayStartTime = new Time();
		dayStartTime.setToNow();
		dayStartTime.hour = dayStartTime.minute = dayStartTime.second = 0;
		dayStartTime.normalize(false);
		dayStart = dayStartTime.toMillis(false);

		Time dayEndTime = new Time(dayStartTime);
		dayEndTime.hour = 23;
		dayEndTime.minute = dayEndTime.second = 59;
		dayEndTime.normalize(false);
		dayEnd = dayEndTime.toMillis(false);

		Time oneWeekFromNowTime = new Time(dayEndTime);
		oneWeekFromNowTime.monthDay += 7;
		oneWeekFromNowTime.normalize(false);
		oneWeekFromNow = oneWeekFromNowTime.toMillis(false);

		Time yearEndTime = new Time(dayEndTime);
		yearEndTime.month = 11;
		yearEndTime.monthDay = 31;
		yearEndTime.normalize(false);
		yearEnd = yearEndTime.toMillis(false);

		Time yearStartTime = new Time(dayStartTime);
		yearStartTime.month = 0;
		yearStartTime.monthDay = 1;
		yearStartTime.normalize(false);
		yearStart = yearStartTime.toMillis(false);
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

	private CharSequence formatTime(Time start, long startMillis, Time end,
			long endMillis, boolean allDay) {
		String startDay;
		String endDay;

		boolean isStartToday = (dayStart <= startMillis && startMillis <= dayEnd);
		boolean isEndToday = (dayStart <= endMillis && endMillis <= dayEnd);

		if (isStartToday && isEndToday)
			startDay = endDay = "";
		else {
			startDay = formatDay(start, startMillis);

			Time endOfStartDay = new Time(start);
			endOfStartDay.hour = 23;
			endOfStartDay.minute = endOfStartDay.second = 59;

			if (Time.compare(end, endOfStartDay) <= 0)
				endDay = "";
			else
				endDay = formatDay(end, endMillis);
		}

		if (allDay) {
			if (endDay == "")
				return startDay;
			else
				return startDay + "-" + endDay;
		}

		boolean format_24hours = getResources().getBoolean(
				R.bool.format_24hours);

		String startHour = start.format(format_24hours ? "%-H:%M" : "%-I:%M%P");
		if (startMillis == endMillis) {
			if (format_24hours)
				return startDay + " " + startHour;
			else {
				SpannableStringBuilder result = new SpannableStringBuilder(
						startDay + " " + startHour);
				int len = result.length();
				result.setSpan(new RelativeSizeSpan(0.7f), len - 2, len, 0);
				return result;
			}

		}

		String endHour = end.format(format_24hours ? "%-H:%M" : "%-I:%M%P");

		StringBuilder result = new StringBuilder(startDay);
		result.append(' ');
		result.append(startHour);
		int pos1 = result.length();
		result.append('-');
		if (endDay != "") {
			result.append(endDay);
			result.append(' ');
		}
		result.append(endHour);
		int pos2 = result.length();

		if (format_24hours)
			return result;
		else {
			SpannableStringBuilder spannable = new SpannableStringBuilder(
					result);
			spannable.setSpan(new RelativeSizeSpan(0.7f), pos1 - 2, pos1, 0);
			spannable.setSpan(new RelativeSizeSpan(0.7f), pos2 - 2, pos2, 0);
			return spannable;
		}
	}

	private String formatDay(Time day, long dayMillis) {
		if (dayStart <= dayMillis && dayMillis <= dayEnd)
			return day.format(getResources().getString(R.string.format_today));
		else if (dayStart <= dayMillis && dayMillis <= oneWeekFromNow)
			return getResources().getStringArray(R.array.format_day_of_week)[day.weekDay];
		else if (yearStart <= dayMillis && dayMillis <= yearEnd)
			return day.format(getResources().getString(
					R.string.format_this_year));
		else
			return day.format(getResources().getString(R.string.format_other));
	}

}
