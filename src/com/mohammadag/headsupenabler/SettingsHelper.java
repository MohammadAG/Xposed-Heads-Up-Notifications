package com.mohammadag.headsupenabler;

import java.util.HashSet;

import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

public class SettingsHelper {
	private static final String PACKAGE_NAME = "com.mohammadag.headsupenabler";
	private static final String PREFS = PACKAGE_NAME + "_preferences";
	private static final String BLACKLIST = "blacklist";
	private XSharedPreferences mXSharedPreferences = null;
	private SharedPreferences mSharedPreferences = null;
	private Context mContext = null;
	private HashSet<String> mListItems;

	// Called from module's classes.
	public SettingsHelper() {
		mXSharedPreferences = new XSharedPreferences(PACKAGE_NAME, PREFS);
		mXSharedPreferences.makeWorldReadable();
	}

	// Called from activities.
	@SuppressWarnings("deprecation")
	public SettingsHelper(Context context) {
		mSharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
		mContext = context;
	}

	public void reload() {
		mXSharedPreferences.reload();
		mListItems = getListItems();
	}

	public void addListItem(String listItem) {
		mListItems.add(listItem);
		SharedPreferences.Editor prefEditor = mSharedPreferences.edit();
		prefEditor.putStringSet(BLACKLIST, mListItems);
		prefEditor.apply();
	}

	public void removeListItem(String listItem) {
		SharedPreferences.Editor prefEditor = mSharedPreferences.edit();
		mListItems.remove(listItem);
		prefEditor.putStringSet(BLACKLIST, mListItems);
		prefEditor.apply();
	}

	public boolean isListed(String s) {
		if (mListItems == null)
			mListItems = getListItems();
		return mListItems.contains(s);
	}

	public HashSet<String> getListItems() {
		HashSet<String> set = new HashSet<String>();
		if (mSharedPreferences != null)
			set.addAll(mSharedPreferences.getStringSet(BLACKLIST, set));
		else if (mXSharedPreferences != null)
			set.addAll(mXSharedPreferences.getStringSet(BLACKLIST, set));
		return set;
	}

	public int getHeadsUpNotificationDecay() {
		return Integer.parseInt(mXSharedPreferences.getString("heads_up_notification_decay", "3700"));
	}

	public boolean isEnabledForOngoingNotifications() {
		return mXSharedPreferences.getBoolean("enabled_for_ongoing_notifications", false);
	}
}
