package com.mohammadag.headsupenabler;

import java.util.HashSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import de.robv.android.xposed.XSharedPreferences;

public class SettingsHelper {
	private static final String PACKAGE_NAME = "com.mohammadag.headsupenabler";
	private static final String PREFS = PACKAGE_NAME + "_preferences";
	// called "blacklist" for compatibility with previous versions, but can be a whitelist too
	private static final String NOTIFICATION_FILTER_LIST = "blacklist";
	private XSharedPreferences mXSharedPreferences = null;
	private SharedPreferences mSharedPreferences = null;
	private Context mContext = null;
	private HashSet<String> mListItems;
	private String mListType;

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
		mListType = getListType();
	}

	public void addListItem(String listItem) {
		mListItems.add(listItem);
		SharedPreferences.Editor prefEditor = mSharedPreferences.edit();
		prefEditor.putStringSet(NOTIFICATION_FILTER_LIST, mListItems);
		prefEditor.apply();
	}

	public void removeListItem(String listItem) {
		SharedPreferences.Editor prefEditor = mSharedPreferences.edit();
		mListItems.remove(listItem);
		prefEditor.putStringSet(NOTIFICATION_FILTER_LIST, mListItems);
		prefEditor.apply();
	}

	public boolean isListed(String s) {
		if (mListItems == null)
			mListItems = getListItems();
		return mListItems.contains(s);
	}

	public boolean shouldIgnore(String s) {
		return mListType.equals("blacklist") ? isListed(s) : !isListed(s);
	}

	public HashSet<String> getListItems() {
		HashSet<String> set = new HashSet<String>();
		if (mSharedPreferences != null)
			set.addAll(mSharedPreferences.getStringSet(NOTIFICATION_FILTER_LIST, set));
		else if (mXSharedPreferences != null)
			set.addAll(mXSharedPreferences.getStringSet(NOTIFICATION_FILTER_LIST, set));
		return set;
	}

	public String getListType() {
		if (mSharedPreferences != null)
			return mSharedPreferences.getString("notification_filter_type", "blacklist");
		else if (mXSharedPreferences != null)
			return mXSharedPreferences.getString("notification_filter_type", "blacklist");
		return null;
	}

	public int getHeadsUpNotificationDecay() {
		return Integer.parseInt(mXSharedPreferences.getString("heads_up_notification_decay", "3700"));
	}

	public boolean isEnabledForOngoingNotifications() {
		return mXSharedPreferences.getBoolean("enabled_for_ongoing_notifications", false);
	}

	public boolean isEnabledOnlyWhenFullscreen() {
		return mXSharedPreferences.getBoolean("enabled_only_when_fullscreen", false);
	}

	public boolean isEnabledOnlyWhenUnlocked() {
		return mXSharedPreferences.getBoolean("enabled_only_when_unlocked", false);
	}

	public boolean isAlwaysExpanded() {
		return mXSharedPreferences.getBoolean("always_expanded", false);
	}

	public boolean shouldRemovePadding() {
		return mXSharedPreferences.getBoolean("remove_padding", false);
	}

	public boolean isDisabledForLowPriority() {
		return mXSharedPreferences.getBoolean("disabled_for_low_priority", false);
	}

	public int getGravity() {
		String gravitySettingValue = mXSharedPreferences.getString("heads_up_gravity", Integer.toString(Gravity.TOP));
		return Integer.parseInt(gravitySettingValue);
	}

	public boolean isHaloEnabled() {
		return mXSharedPreferences.getBoolean("halo_enabled", false);
	}

	public boolean areCustomColorsEnabled() {
		return mXSharedPreferences.getBoolean("custom_colors_enabled", false);
	}

	public int getBackgroundColor() {
		if (mSharedPreferences != null)
			return mSharedPreferences.getInt("background_color", Color.BLACK);
		else if (mXSharedPreferences != null)
			return mXSharedPreferences.getInt("background_color", Color.BLACK);
		return Color.BLACK;
	}

	public void setBackgroundColor(int color) {
		mSharedPreferences.edit().putInt("background_color", color).apply();
	}

	public int getForegroundColor() {
		if (mSharedPreferences != null)
			return mSharedPreferences.getInt("foreground_color", Color.WHITE);
		else if (mXSharedPreferences != null)
			return mXSharedPreferences.getInt("foreground_color", Color.WHITE);
		return Color.BLACK;
	}

	public void setForegroundColor(int color) {
		mSharedPreferences.edit().putInt("foreground_color", color).apply();
	}
}
