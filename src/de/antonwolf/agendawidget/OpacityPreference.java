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

import de.antonwolf.agendawidget.R;
import de.antonwolf.agendawidget.WidgetInfo;
import android.content.Context;
import android.preference.DialogPreference;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public final class OpacityPreference extends DialogPreference implements
		OnSeekBarChangeListener {
	private TextView text;
	private SeekBar bar;
	private ImageView checkerboard;
	private static final float step = 1f / 20f;
	private final float defaultValue;

	public OpacityPreference(Context context, WidgetInfo info) {
		super(context, null);
		setDialogLayoutResource(R.layout.preference_opacity);
		setTitle(R.string.settings_display_opacity);
		setDialogTitle(R.string.settings_display_opacity);
		setKey(info.opacityKey);
		defaultValue = info.opacityDefault;
		setDefaultValue(defaultValue);
		final int opacityPercent = (int) (100 * info.opacity);
		setSummary(getContext().getResources().getString(
				R.string.settings_display_opacity_summary, opacityPercent));
	}

	private void displayProgress(float value) {
		final int valuePercent = (int) (value * 100);
		text.setText(getContext().getResources().getString(
				R.string.settings_display_opacity_dialog, valuePercent));
		checkerboard.setImageLevel(valuePercent);
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);

		float value = getPersistedFloat(defaultValue);
		text = (TextView) view.findViewById(R.id.value);
		bar = (SeekBar) view.findViewById(R.id.bar);
		bar.setMax((int) (1 / step));
		bar.setProgress((int) (value / step));
		bar.setOnSeekBarChangeListener(this);
		checkerboard = (ImageView) view.findViewById(R.id.checkerboard);
		displayProgress(value);
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (positiveResult) {
			persistFloat(bar.getProgress() * step);
			final int opacityPercent = (int) (100 * bar.getProgress() * step);
			setSummary(getContext().getResources().getString(
					R.string.settings_display_opacity_summary, opacityPercent));
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		displayProgress(progress * step);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}
}