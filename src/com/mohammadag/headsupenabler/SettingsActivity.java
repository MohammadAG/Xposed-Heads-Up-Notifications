package com.mohammadag.headsupenabler;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
		addPreferencesFromResource(R.xml.preferences);

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
	}
}
