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

		long nextUpdate = dayEnd;

		LinkedList<RemoteViews> events = new LinkedList<RemoteViews>();
		LinkedList<RemoteViews> birthdays = new LinkedList<RemoteViews>();

		boolean bdayLeft = true;

		int maxLines = prefs.getLines();

		Cursor cur = null;

		try {
			cur = getCursor();

			while (!bdayLeft || ((events.size() + birthdays.size()) < maxLines)) {
				// no next item? abort!
				if (!cur.moveToNext())
					break;

				// is this calendar enabled?
				if (!prefs.isCalendar(cur.getInt(COL_CALENDAR)))
					continue;

				long endMillis = cur.getLong(COL_END_MILLIS);
				boolean allDay = 1 == cur.getInt(COL_ALL_DAY);
				int endDay = cur.getInt(COL_END_DAY);

				// non-all-day event in the past? Don't display!
				if (!allDay && endMillis <= System.currentTimeMillis())
					continue;

				long startMillis = cur.getLong(COL_START_MILLIS);
				Time startTime = new Time();
				Time endTime = new Time();

				if (allDay) {
					endTime.setJulianDay(endDay);

					// allDay event in the past? Don't display!
					if (endTime.toMillis(false) < dayStart)
						continue;
					startTime.setJulianDay(cur.getInt(COL_START_DAY));
				} else {
					startTime.set(startMillis);
					endTime.set(endMillis);
				}

				String title = cur.getString(COL_TITLE);
				if (title == null)
					title = "";

				boolean isBirthday = false;
				if (!prefs.getBirthdays().equals(
						WidgetPreferences.BIRTHDAY_NORMAL))
					for (Pattern pattern : getBirthdayPatterns()) {
						Matcher matcher = pattern.matcher(title);
						if (!matcher.find())
							continue;
						title = matcher.group(1);
						isBirthday = true;
						break;
					}

				// Hide birthday events
				if (isBirthday
						&& prefs.getBirthdays().equals(
								WidgetPreferences.BIRTHDAY_HIDE))
					continue;

				if (!allDay && endMillis < nextUpdate)
					nextUpdate = endMillis;

				SpannableStringBuilder builder = new SpannableStringBuilder();

				formatTime(builder, startTime, startMillis, endTime, endMillis,
						allDay);
				builder.append(' ');
				int timeEndPos = builder.length();
				builder.setSpan(new ForegroundColorSpan(0xaaffffff), 0,
						timeEndPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				builder.append(title);
				int titleEndPos = builder.length();
				builder.setSpan(new ForegroundColorSpan(0xffffffff),
						timeEndPos, titleEndPos,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				String location = cur.getString(COL_LOCATION);
				if (location != null && !isEmpty.matcher(location).find()) {
					builder.append(", ");
					builder.append(location);
					builder.setSpan(new ForegroundColorSpan(0xaaffffff),
							titleEndPos, builder.length(),
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}

				if (isBirthday) {
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
					RemoteViews event = new RemoteViews(packageName,
							R.layout.event);
					events.addLast(event);

					event.setTextViewText(R.id.event_text, builder);

					event.setTextColor(R.id.event_color, cur.getInt(COL_COLOR));

					int alarmFlag = cur.getInt(COL_HAS_ALARM) == 0 ? View.GONE
							: View.VISIBLE;
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
		long start = dayStart - 1000 * 60 * 60 * 24;
		long end = start + SEARCH_DURATION;
		String uriString = String.format(CURSOR_FORMAT, start, end);
		return getContentResolver().query(Uri.parse(uriString),
				CURSOR_PROJECTION, null, null, CURSOR_SORT);
	}

	private void updateTimeRanges() {
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

	private void formatTime(SpannableStringBuilder builder, Time start,
			long startMillis, Time end, long endMillis, boolean allDay) {

		Formatter formatter = new Formatter(builder);

		boolean isStartToday = (dayStart <= startMillis && startMillis <= dayEnd);
		boolean isEndToday = (dayStart <= endMillis && endMillis <= dayEnd);
		boolean showDay = !isStartToday || !isEndToday;

		Time endOfStartDay = new Time(start);
		endOfStartDay.hour = 23;
		endOfStartDay.minute = endOfStartDay.second = 59;

		if (allDay) {
			if (showDay)
				appendDay(formatter, builder, startMillis, start);

			if (Time.compare(end, endOfStartDay) > 0) {
				builder.append('-');
				appendDay(formatter, builder, endMillis, end);
			}
			return;
		}

		if (startMillis == endMillis) {
			if (showDay) {
				appendDay(formatter, builder, startMillis, start);
				builder.append(' ');
			}
			appendHour(formatter, builder, startMillis);
			return;
		}

		if (showDay) {
			appendDay(formatter, builder, startMillis, start);
			builder.append(' ');
		}
		appendHour(formatter, builder, startMillis);
		builder.append('-');
		if (Time.compare(end, endOfStartDay) > 0) {
			appendDay(formatter, builder, endMillis, end);
			builder.append(' ');
		}
		appendHour(formatter, builder, endMillis);
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
		if (dayStart <= time && time <= dayEnd)
			builder.append(getResources().getString(R.string.format_today));
		else if (dayStart <= time && time <= oneWeekFromNow)
			builder.append(getResources().getStringArray(
					R.array.format_day_of_week)[day.weekDay]);
		else if (yearStart <= time && time <= yearEnd) {
			formatter.format(getResources()
					.getString(R.string.format_this_year), time);
		} else
			formatter.format(getResources().getString(R.string.format_other),
					time);
	}
}
