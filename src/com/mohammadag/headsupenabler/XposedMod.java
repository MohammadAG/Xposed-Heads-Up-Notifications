package com.mohammadag.headsupenabler;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;


import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.res.XModuleResources;
import android.graphics.PixelFormat;
import android.os.PowerManager;
import android.app.KeyguardManager;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {
	private static SettingsHelper mSettingsHelper;
	private static String MODULE_PATH;
	private int mStatusBarVisibility;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.android.systemui")) {
			return;
		}

		if (mSettingsHelper == null) {
			mSettingsHelper = new SettingsHelper();
		}

		/*
		* Determine when to show a Heads Up notification.
		*/
		Class<?> BaseStatusBar = findClass("com.android.systemui.statusbar.BaseStatusBar", lpparam.classLoader);
		findAndHookMethod(BaseStatusBar, "shouldInterrupt", StatusBarNotification.class,
				new XC_MethodReplacement() {
					@Override
					protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
						StatusBarNotification n = (StatusBarNotification) param.args[0];
						mSettingsHelper.reload();
						PowerManager powerManager = (PowerManager) getObjectField(param.thisObject, "mPowerManager");
						// mKeyguardManager missing in OmniRom
						//KeyguardManager keyguardManager = (KeyguardManager) getObjectField(param.thisObject, "mKeyguardManager");
						Context ctx = (Context) getObjectField(param.thisObject, "mContext");
						KeyguardManager keyguardManager = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
						// Ignore if the notification is ongoing and we haven't enabled that in the settings
						return !(n.isOngoing() && !mSettingsHelper.isEnabledForOngoingNotifications())
								// Ignore if we're not in a fullscreen app and the "only when fullscreen" setting is
								// enabled
								&& !(!((mStatusBarVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == View.SYSTEM_UI_FLAG_FULLSCREEN)
									&& mSettingsHelper.isEnabledOnlyWhenFullscreen())
								// Ignore if phone is locked and the "only when unlocked" setting is enabled
								&& !(keyguardManager.isKeyguardLocked() && mSettingsHelper.isEnabledOnlyWhenUnlocked())
								// Screen must be on
								&& powerManager.isScreenOn()
								// Check if low priority  
								&& !(mSettingsHelper.isDisabledForLowPriority() 
										&& !(n.getNotification().priority > Notification.PRIORITY_LOW))
								// Ignore blacklisted/non whitelisted packages
								&& !mSettingsHelper.shouldIgnore(n.getPackageName());
					}
				}
		);

		/*
		 * Stop breaking the normal notification ordering.
		 * The current AOSP implementation considers the interruption state (= has it been shown to the user using a
		 * Heads Up?) more important than the notification's score when comparing notifications (to set the order).
		 * This breaks the normal order of notifications, so we're nuking the method that sets the interruption state.
		 */
		Class<?> NotificationDataEntry = findClass("com.android.systemui.statusbar.NotificationData$Entry",
				lpparam.classLoader);
		findAndHookMethod(NotificationDataEntry, "setInterruption", XC_MethodReplacement.DO_NOTHING);

		/*
		 * Enable one handed expansion for the Heads Up view.
		 */
		Class<?> HeadsUpNotificationView = findClass("com.android.systemui.statusbar.policy.HeadsUpNotificationView",
				lpparam.classLoader);
		findAndHookMethod(HeadsUpNotificationView, "onAttachedToWindow", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				setAdditionalInstanceField(getObjectField(param.thisObject, "mExpandHelper"), "headsUp", true);
			}
		});

		Class<?> ExpandHelper = findClass("com.android.systemui.ExpandHelper", lpparam.classLoader);
		findAndHookMethod(ExpandHelper, "onInterceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				int action = ((MotionEvent) param.args[0]).getAction();
				Object headsUp = getAdditionalInstanceField(param.thisObject, "headsUp");
				if (headsUp != null && (action & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
					setObjectField(param.thisObject, "mWatchingForPull", true);
				}
			}
		});

		/*
		* Monitor status bar visibility changes.
		*/
		Class<?> PhoneStatusBar = findClass("com.android.systemui.statusbar.phone.PhoneStatusBar", lpparam.classLoader);
		findAndHookMethod(PhoneStatusBar, "setSystemUiVisibility", int.class, int.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				mStatusBarVisibility = (Integer) param.args[0];
			}
		});

		/*
		* Make the Heads Up notification show on top of the status bar by setting the y position to 0.
		* It could allow for other changes (e.g. change the gravity) in the future as well.
		*/
		findAndHookMethod(PhoneStatusBar, "addHeadsUpView", new XC_MethodReplacement() {
			@Override
			protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
				Context context = (Context) getObjectField(param.thisObject, "mContext");
				WindowManager windowManager = (WindowManager) getObjectField(param.thisObject, "mWindowManager");
				View headsUpNotificationView = (View) getObjectField(param.thisObject, "mHeadsUpNotificationView");
				int animation = context.getResources().getIdentifier("Animation_StatusBar_HeadsUp", "style",
						"com.android.systemui");

				WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
						WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL, // above the status bar!
						WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
								| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
								| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
								| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
								| WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
								| WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
						PixelFormat.TRANSLUCENT
				);
				lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
				lp.gravity = Gravity.TOP;
				if (mSettingsHelper.shouldRemovePadding())
					lp.y = 0;
				else
					lp.y = (Integer) callMethod(param.thisObject, "getStatusBarHeight");
				lp.setTitle("Heads Up");
				lp.packageName = context.getPackageName();
				lp.windowAnimations = animation;

				windowManager.addView(headsUpNotificationView, lp);
				return null;
			}
		});

		/*
		* Enable Heads Up on startup.
		*/
		findAndHookMethod(PhoneStatusBar, "start", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!getBooleanField(param.thisObject, "mUseHeadsUp")) {
					setBooleanField(param.thisObject, "mUseHeadsUp", true);
					callMethod(param.thisObject, "addHeadsUpView");
				}
			}
		});

	}

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		MODULE_PATH = startupParam.modulePath;
	}

	@Override
	public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
		if (!resparam.packageName.equals("com.android.systemui"))
			return;

		if (mSettingsHelper == null) {
			mSettingsHelper = new SettingsHelper();
		}


		// Set the delay before the Heads Up notification is hidden.
		resparam.res.setReplacement("com.android.systemui", "integer", "heads_up_notification_decay",
				mSettingsHelper.getHeadsUpNotificationDecay());

		// Replace the Heads Up notification's background to remove the padding.
		if (mSettingsHelper.shouldRemovePadding()) {
			XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, resparam.res);
			resparam.res.setReplacement("com.android.systemui", "drawable", "heads_up_window_bg",
					modRes.fwd(R.drawable.heads_up_window_bg));
		}
	}

}
