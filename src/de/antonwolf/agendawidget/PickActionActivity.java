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
 * THE SOFTWARE
 */
package de.antonwolf.agendawidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/**
 * The activity that launches when the user clicks on the widget
 * 
 * @author Anton Wolf
 * 
 */
public final class PickActionActivity extends Activity {
	/**
	 * An OnClickListener that starts an Activity described by an Intent
	 * 
	 * @author Anton Wolf
	 * 
	 */
	private final static class StartActivityOnClick implements
			View.OnClickListener {
		final Intent intent;

		StartActivityOnClick(Intent intent) {
			this.intent = intent;
		}

		@Override
		public void onClick(View v) {
			v.getContext().startActivity(intent);
		}
	}

	/**
	 * The key under which the Intent's AppWidget ID is stored
	 */
	public final static String EXTRA_WIDGET_ID = "widgetId";

	/**
	 * Tag for Log messages
	 */
	private final static String TAG = "AgendaWidget";

	/**
	 * Sets up the Activity's GUI
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final int widgetId = getIntent().getIntExtra(EXTRA_WIDGET_ID, -1);
		Log.d(TAG, "PickActionActivity.onCreate(" + widgetId + ")");
		if (widgetId == -1)
			return;

		setContentView(R.layout.pick_action);

		final Intent calendar = new Intent(Intent.ACTION_VIEW,
				Uri.parse("content://com.android.calendar/time"));
		findViewById(R.id.open_calendar).setOnClickListener(new StartActivityOnClick(calendar));

		final Intent settings = new Intent(this, SettingsActivity.class);
		settings.putExtra(SettingsActivity.EXTRA_WIDGET_ID, widgetId);
		findViewById(R.id.open_settings).setOnClickListener(new StartActivityOnClick(settings));
	}
}
