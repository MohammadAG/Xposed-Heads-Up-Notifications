package com.mohammadag.headsupenabler;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findConstructorExact;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getLongField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import java.lang.reflect.Constructor;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.PixelFormat;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {
	private static SettingsHelper mSettingsHelper;
	private static String MODULE_PATH;
	private int mStatusBarVisibility;
	protected Object mHtcExpandHelper;

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
		final Class<?> BaseStatusBar = findClass("com.android.systemui.statusbar.BaseStatusBar", lpparam.classLoader);
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
		final Class<?> HeadsUpNotificationView = findClass("com.android.systemui.statusbar.policy.HeadsUpNotificationView",
				lpparam.classLoader);
		final Class<?> ExpandHelper = findClass("com.android.systemui.ExpandHelper", lpparam.classLoader);
		final Class<?> ExpandHelperCallback = findClass("com.android.systemui.ExpandHelper$Callback", lpparam.classLoader);
		findAndHookMethod(HeadsUpNotificationView, "onAttachedToWindow", new XC_MethodHook() {
			private Unhook mHtcUnhook;

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Object expandHelper = null;
				try {
					 expandHelper = getObjectField(param.thisObject, "mExpandHelper");
				} catch (NoSuchFieldError e) {
				}
				if (android.os.Build.MANUFACTURER.equalsIgnoreCase("htc")
						&& expandHelper == null) {
					Context mContext = (Context) getObjectField(param.thisObject, "mContext");
					Resources res = mContext.getResources();
					int minHeight = res.getDimensionPixelSize(
							res.getIdentifier("notification_row_min_height",
									"dimen", "com.android.systemui"));
					int maxHeight = res.getDimensionPixelSize(
							res.getIdentifier("notification_row_max_height",
									"dimen", "com.android.systemui"));
					Constructor<?> constructor = findConstructorExact(ExpandHelper, Context.class,
							ExpandHelperCallback, int.class, int.class, BaseStatusBar);
					expandHelper = constructor.newInstance(mContext, param.thisObject, minHeight,
							maxHeight, null);

					mHtcExpandHelper = expandHelper;

					if (mHtcUnhook == null) {
						mHtcUnhook = findAndHookMethod(HeadsUpNotificationView,
								"onInterceptTouchEvent", MotionEvent.class, new XC_MethodReplacement() {
							protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
								long mStartTouchTime = getLongField(param.thisObject, "mStartTouchTime");
								if (System.currentTimeMillis() < mStartTouchTime) {
									return true;
								}

								boolean result = (Boolean) XposedBridge.invokeOriginalMethod(param.method,
										param.thisObject, param.args);
								return result || (Boolean) callMethod(mHtcExpandHelper,
										"onInterceptTouchEvent", param.args[0]);
							}
						});

						findAndHookMethod(HeadsUpNotificationView,
								"onTouchEvent", MotionEvent.class, new XC_MethodReplacement() {
							protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
								long mStartTouchTime = getLongField(param.thisObject, "mStartTouchTime");
								if (System.currentTimeMillis() < mStartTouchTime) {
									return false;
								}

								callMethod(getObjectField(param.thisObject, "mBar"), "resetHeadsUpDecayTimer");
								boolean result = (Boolean) XposedBridge.invokeOriginalMethod(param.method,
										param.thisObject, param.args);

								return result || (Boolean) callMethod(mHtcExpandHelper,
										"onTouchEvent", param.args[0]);
							}
						});
					}
				}

				setAdditionalInstanceField(expandHelper, "headsUp", true);
			}
		});

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
		*/
		findAndHookMethod(PhoneStatusBar, "addHeadsUpView", new XC_MethodReplacement() {
			@Override
			protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
				Context context = (Context) getObjectField(param.thisObject, "mContext");
				WindowManager windowManager = (WindowManager) getObjectField(param.thisObject, "mWindowManager");
				View headsUpNotificationView = (View) getObjectField(param.thisObject, "mHeadsUpNotificationView");
				int animation = context.getResources().getIdentifier("Animation.StatusBar.HeadsUp", "style",
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
				lp.gravity = mSettingsHelper.getGravity();
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
        * Always expanded
        */
        findAndHookMethod(HeadsUpNotificationView, "setNotification", NotificationDataEntry, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object headsUp = getObjectField(param.thisObject, "mHeadsUp");
                FrameLayout row = (FrameLayout) getObjectField(headsUp, "row");
                callMethod(row, "setExpanded", mSettingsHelper.isAlwaysExpanded());
            }
        });

		/*
		* Halo
		*/
		findAndHookMethod(BaseStatusBar, "inflateViews", NotificationDataEntry, ViewGroup.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				mSettingsHelper.reload();
				if (!mSettingsHelper.isHaloEnabled())
					return;
				ViewGroup parent = (ViewGroup) param.args[1];
				if (!parent.getClass().getName().equals("android.widget.FrameLayout"))
					return;
				Object entry = param.args[0];
				final Context context = (Context) getObjectField(param.thisObject, "mContext");
				View row = (View) getObjectField(entry, "row");
				View content = row.findViewById(context.getResources().getIdentifier("content", "id",
						"com.android.systemui"));
				Object sbn = getObjectField(entry, "notification");
				Notification notification = (Notification) getObjectField(sbn, "notification");
				final PendingIntent contentIntent = notification.contentIntent;
				content.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (contentIntent == null)
							return;
						Intent intent = new Intent().addFlags(0x00002000);
						try {
							contentIntent.send(context, 0, intent);
						} catch (PendingIntent.CanceledException ignored) {
						}
					}
				});
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
