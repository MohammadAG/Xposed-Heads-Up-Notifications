package com.mohammadag.headsupenabler;

import android.content.Context;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;

public class XposedMod implements IXposedHookLoadPackage {
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.android.systemui"))
			return;

        Class<?> BaseStatusBar = XposedHelpers.findClass("com.android.systemui.statusbar.BaseStatusBar", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(BaseStatusBar, "shouldInterrupt", StatusBarNotification.class, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // don't interrupt if the notification is not clearable
                param.setResult(callMethod(param.args[0], "isClearable"));
            }
        });

		/* Users won't use adb or terminal, force it upon that */
		XposedHelpers.findAndHookMethod(BaseStatusBar, "start", new XC_MethodHook() {
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				Settings.Global.putInt(mContext.getContentResolver(), "heads_up_enabled", 1);
			}
		});
	}
}
