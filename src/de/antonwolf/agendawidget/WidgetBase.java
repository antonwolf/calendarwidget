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

import java.util.Arrays;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

/**
 * @author Anton Wolf
 * 
 *         Base class for each widget
 */
public abstract class WidgetBase extends AppWidgetProvider {
	private ContentObserver calendarInstancesObserver;
	static final String TAG = "AgendaWidget";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
				&& intent.getAction() == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
			ComponentName name = new ComponentName(context, this.getClass());
			AppWidgetManager m = AppWidgetManager.getInstance(context);
			int[] ids = m.getAppWidgetIds(name);
			onUpdate(context, m, ids);
		} else
			super.onReceive(context, intent);
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

		for (int appWidgetId : ids) {
			Intent intent = new Intent("update", Uri.parse("widget://"
					+ appWidgetId), context, WidgetService.class);
			Log.d(TAG, "sending " + intent);
			context.startService(intent);
		}
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
		String uriString = "content://com.android.calendar/instances";
		Uri instancesUri = Uri.parse(uriString);
		context.getContentResolver().registerContentObserver(instancesUri,
				true, calendarInstancesObserver);
	}
}
