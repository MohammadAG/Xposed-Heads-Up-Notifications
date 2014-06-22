package com.mohammadag.headsupenabler;

import android.content.Context;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.android.systemui"))
			return;

		Class<?> BaseStatusBar = XposedHelpers.findClass("com.android.systemui.statusbar.BaseStatusBar", lpparam.classLoader);
		XposedHelpers.findAndHookMethod(BaseStatusBar, "shouldInterrupt", StatusBarNotification.class,
				XC_MethodReplacement.returnConstant(true));

		/* Users won't use adb or terminal, force it upon that */
		XposedHelpers.findAndHookMethod(BaseStatusBar, "start", new XC_MethodHook() {
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Context mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				Settings.Global.putInt(mContext.getContentResolver(), "heads_up_enabled", 1);
			};
		});
	}
}
