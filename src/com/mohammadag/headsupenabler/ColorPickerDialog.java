package com.mohammadag.headsupenabler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

public class ColorPickerDialog extends Activity {
	private boolean mTextChanged = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.color_picker);
		setTitle(getIntent().getIntExtra("title", R.string.app_name));
		int color = getIntent().getIntExtra("color", Color.BLACK);

		final ColorPicker colorPicker = (ColorPicker) findViewById(R.id.picker);
		SaturationBar saturationBar = (SaturationBar) findViewById(R.id.saturationbar);
		final ValueBar valueBar = (ValueBar) findViewById(R.id.valuebar);
		final OpacityBar opacityBar = (OpacityBar) findViewById(R.id.opacitybar);
		final TextView valueTextView = (TextView) findViewById(R.id.value);
		Button doneButton = (Button) findViewById(R.id.done);
		Button cancelButton = (Button) findViewById(R.id.cancel);

		colorPicker.addSaturationBar(saturationBar);
		colorPicker.addValueBar(valueBar);
		colorPicker.addOpacityBar(opacityBar);

		colorPicker.setColor(color);
		valueTextView.setText(colorIntToRGB(color));

		colorPicker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
			@Override
			public void onColorChanged(int newColor) {
				mTextChanged = true;
				valueTextView.setText(colorIntToRGB(newColor));
			}
		});

		valueTextView.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence text, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence text, int start, int before, int count) {
				if (mTextChanged)
					mTextChanged = false;
				else if (text.length() == 8) {
					String value = "#" + text;
					try {
						int newColor = Color.parseColor(value);
						colorPicker.setColor(newColor);
					} catch (IllegalArgumentException e) {
						Toast.makeText(ColorPickerDialog.this, getString(R.string.invalid_color),
								Toast.LENGTH_SHORT).show();
					}
				}
			}

			@Override
			public void afterTextChanged(Editable text) {
			}
		});

		cancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				setResult(Activity.RESULT_CANCELED);
				finish();
			}
		});

		doneButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent();
				intent.putExtra("color", colorPicker.getColor());
				setResult(Activity.RESULT_OK, intent);
				finish();
			}
		});
	}

	@Override
	public void onBackPressed() {
		setResult(Activity.RESULT_CANCELED);
		super.onBackPressed();
	}

	public static String colorIntToRGB(int color) {
		return String.format("%08X", color);
	}
}
