package com.mohammadag.headsupenabler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {
	private static final String GRAVITY_TOP = "48";
	private static final int BACKGROUND_COLOR_REQUEST = 0;
	private static final int FOREGROUND_COLOR_REQUEST = 1;
	private SettingsHelper mSettingsHelper;
	private Preference mBackgroundColorPref;
	private Preference mForegroundColorPref;

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
		addPreferencesFromResource(R.xml.preferences);
		mSettingsHelper = new SettingsHelper(this);

		mBackgroundColorPref = findPreference("background_color");
		mBackgroundColorPref.setSummary(ColorPickerDialog.colorIntToRGB(mSettingsHelper.getBackgroundColor()));
		mBackgroundColorPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(SettingsActivity.this, ColorPickerDialog.class);
				intent.putExtra("color", mSettingsHelper.getBackgroundColor());
				intent.putExtra("title", R.string.pref_background_color_title);
				startActivityForResult(intent, BACKGROUND_COLOR_REQUEST);
				return true;
			}
		});

		mForegroundColorPref = findPreference("foreground_color");
		mForegroundColorPref.setSummary(ColorPickerDialog.colorIntToRGB(mSettingsHelper.getForegroundColor()));
		mForegroundColorPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(SettingsActivity.this, ColorPickerDialog.class);
				intent.putExtra("color", mSettingsHelper.getForegroundColor());
				intent.putExtra("title", R.string.pref_foreground_color_title);
				startActivityForResult(intent, FOREGROUND_COLOR_REQUEST);
				return true;
			}
		});

		final ListPreference notificationFilterTypePref = (ListPreference) findPreference("notification_filter_type");
		final Preference notificationList = findPreference("notification_list");

		if (notificationFilterTypePref.getValue().equals("blacklist")) {
			notificationFilterTypePref.setSummary(R.string.pref_blacklist_title);
			notificationList.setTitle(R.string.pref_blacklist_title);
			notificationList.setSummary(R.string.pref_blacklist_summary);
		} else {
			notificationFilterTypePref.setSummary(R.string.pref_whitelist_title);
			notificationList.setTitle(R.string.pref_whitelist_title);
			notificationList.setSummary(R.string.pref_whitelist_summary);
		}

		notificationFilterTypePref.setOnPreferenceChangeListener(new ListPreference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (newValue.equals("blacklist")) {
					notificationFilterTypePref.setSummary(R.string.pref_blacklist_title);
					notificationList.setTitle(R.string.pref_blacklist_title);
					notificationList.setSummary(R.string.pref_blacklist_summary);
				} else {
					notificationFilterTypePref.setSummary(R.string.pref_whitelist_title);
					notificationList.setTitle(R.string.pref_whitelist_title);
					notificationList.setSummary(R.string.pref_whitelist_summary);
				}
				return true;
			}
		});

		final ListPreference notificationGravityTypePreference = (ListPreference) findPreference("heads_up_gravity");
		if (notificationGravityTypePreference.getValue().equals(GRAVITY_TOP)) {
			notificationGravityTypePreference.setSummary(getString(R.string.reboot_required,
					getString(R.string.pref_gravity_top_title)));
		} else {
			notificationGravityTypePreference.setSummary(getString(R.string.reboot_required,
					getString(R.string.pref_gravity_bottom_title)));
		}

		notificationGravityTypePreference.setOnPreferenceChangeListener(new ListPreference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (newValue.equals(GRAVITY_TOP)) {
					notificationGravityTypePreference.setSummary(getString(R.string.reboot_required,
							getString(R.string.pref_gravity_top_title)));
				} else {
					notificationGravityTypePreference.setSummary(getString(R.string.reboot_required,
							getString(R.string.pref_gravity_bottom_title)));
				}
				return true;
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == Activity.RESULT_CANCELED)
			return;

		if (requestCode == BACKGROUND_COLOR_REQUEST) {
			int color = data.getIntExtra("color", Color.BLACK);
			mSettingsHelper.setBackgroundColor(color);
			mBackgroundColorPref.setSummary(ColorPickerDialog.colorIntToRGB(color));
		} else if (requestCode == FOREGROUND_COLOR_REQUEST) {
			int color = data.getIntExtra("color", Color.WHITE);
			mSettingsHelper.setForegroundColor(color);
			mForegroundColorPref.setSummary(ColorPickerDialog.colorIntToRGB(color));
		}
	}
}
