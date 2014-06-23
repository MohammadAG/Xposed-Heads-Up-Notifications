package com.mohammadag.headsupenabler;

import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.MotionEvent;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage, IXposedHookInitPackageResources {
	private static SettingsHelper mSettingsHelper;
	private BroadcastReceiver mBroadcastReceiver;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.android.systemui"))
			return;

		if (mSettingsHelper == null) {
			mSettingsHelper = new SettingsHelper();
		}

		Class<?> BaseStatusBar = XposedHelpers.findClass("com.android.systemui.statusbar.BaseStatusBar", lpparam.classLoader);
		XposedHelpers.findAndHookMethod(BaseStatusBar, "shouldInterrupt", StatusBarNotification.class,
				new XC_MethodReplacement() {
					@Override
					protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
						StatusBarNotification n = (StatusBarNotification) param.args[0];
						mSettingsHelper.reload();
						PowerManager powerManager = (PowerManager) getObjectField(param.thisObject, "mPowerManager");
						if (n.isOngoing() && !mSettingsHelper.isEnabledForOngoingNotifications())
							return false;
						return powerManager.isScreenOn() && !mSettingsHelper.isListed(n.getPackageName());
					}
				}
		);

		Class<?> HeadsUpNotificationView = XposedHelpers.findClass("com.android.systemui.statusbar.policy.HeadsUpNotificationView",
				lpparam.classLoader);
		XposedHelpers.findAndHookMethod(HeadsUpNotificationView, "onAttachedToWindow", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				setAdditionalInstanceField(getObjectField(param.thisObject, "mExpandHelper"), "headsUp", true);
			}
		});

		Class<?> ExpandHelper = XposedHelpers.findClass("com.android.systemui.ExpandHelper", lpparam.classLoader);
		XposedHelpers.findAndHookMethod(ExpandHelper, "onInterceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				int action = ((MotionEvent) param.args[0]).getAction();
				Object headsUp = getAdditionalInstanceField(param.thisObject, "headsUp");
				if (headsUp != null && (action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
					setObjectField(param.thisObject, "mWatchingForPull", true);
				}
			}
		});

		/* Users won't use adb or terminal, force it upon that */
		XposedHelpers.findAndHookMethod(BaseStatusBar, "start", new XC_MethodHook() {
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Context mContext = (Context) getObjectField(param.thisObject, "mContext");
				mBroadcastReceiver = new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
							return;
						}

						// User is uninstalling us, NOOOOOOOOOOOOOOOOOOO
						Log.d("Xpsoed", "Cleaning up after Heads Up uninstallation");
						Settings.Global.putInt(context.getContentResolver(), "heads_up_enabled", 0);
					}
				};

				mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(Intent.ACTION_PACKAGE_REMOVED));
				Settings.Global.putInt(mContext.getContentResolver(), "heads_up_enabled", 1);
			}
		});

		XposedHelpers.findAndHookMethod(BaseStatusBar, "destroy", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Context mContext = (Context) getObjectField(param.thisObject, "mContext");
				mContext.unregisterReceiver(mBroadcastReceiver);
			}
		});
	}

	@Override
	public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals("com.android.systemui"))
			return;

		if (mSettingsHelper == null) {
			mSettingsHelper = new SettingsHelper();
		}

		resparam.res.setReplacement("com.android.systemui", "integer", "heads_up_notification_decay",
				mSettingsHelper.getHeadsUpNotificationDecay());
	}
}
