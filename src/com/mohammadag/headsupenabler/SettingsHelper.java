package com.mohammadag.headsupenabler;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XSharedPreferences;

public class SettingsHelper {
    private static final String PACKAGE_NAME = "com.mohammadag.headsupenabler";
    private static final String PREFS = PACKAGE_NAME + "_preferences";
    private static final String BLACKLIST = "blacklist";
    private XSharedPreferences mXSharedPreferences = null;
    private SharedPreferences mSharedPreferences = null;
    private Context mContext = null;
    private Set<String> mListItems;

    // Called from module's classes.
    public SettingsHelper() {
        mXSharedPreferences = new XSharedPreferences(PACKAGE_NAME, PREFS);
        mXSharedPreferences.makeWorldReadable();
    }

    // Called from activities.
    public SettingsHelper(Context context) {
        mSharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
        mContext = context;
    }

    public void reload() {
        if (mXSharedPreferences.hasFileChanged()) {
            mXSharedPreferences.reload();
            mListItems = getListItems();
        }
    }

    public boolean addListItem(String listItem) {
        mListItems.add(listItem);
        SharedPreferences.Editor prefEditor = mSharedPreferences.edit();
        prefEditor.putStringSet(BLACKLIST, mListItems);
        prefEditor.apply();
        return true;
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

    public Set<String> getListItems() {
        Set<String> set = new HashSet<String>();
        if (mSharedPreferences != null)
            return mSharedPreferences.getStringSet(BLACKLIST, set);
        else if (mXSharedPreferences != null)
            return mXSharedPreferences.getStringSet(BLACKLIST, set);
        return set;
    }

	public int getHeadsUpNotificationDecay() {
		return Integer.parseInt(mXSharedPreferences.getString("heads_up_notification_decay", "3700"));
	}
}
