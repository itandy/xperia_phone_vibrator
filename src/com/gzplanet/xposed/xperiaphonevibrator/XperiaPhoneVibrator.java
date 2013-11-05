package com.gzplanet.xposed.xperiaphonevibrator;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XperiaPhoneVibrator implements IXposedHookZygoteInit, IXposedHookLoadPackage {
	final static String PKGNAME_SETTINGS = "com.android.phone";

	private static final int OUTGOING_CALL_STATE_CHANGE_THRESHOLD = 200;

	// potential conflict if 200 or above message ID is already used
	private static final int VIBRATE_EVERY_MIN = 200;
	private static final int VIBRATE_FIXED_TIME = VIBRATE_EVERY_MIN + 1;

	private static XSharedPreferences pref;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		pref = new XSharedPreferences(XperiaPhoneVibrator.class.getPackage().getName());
		// just in case the preference file permission is reset by recovery/script
		pref.makeWorldReadable();
	}

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals(PKGNAME_SETTINGS))
			return;

		// sanity check for methods to hook
		final boolean onPhoneStateChangedExists = XposedHelpers.findMethodExact(PKGNAME_SETTINGS + ".CallNotifier",
				lpparam.classLoader, "onPhoneStateChanged", AsyncResult.class) == null ? false : true;

		final boolean handleMessageExists = XposedHelpers.findMethodExact(PKGNAME_SETTINGS + ".CallNotifier",
				lpparam.classLoader, "handleMessage", Message.class) == null ? false : true;

		final boolean onDisconnectExists = XposedHelpers.findMethodExact(PKGNAME_SETTINGS + ".CallNotifier",
				lpparam.classLoader, "onDisconnect", AsyncResult.class) == null ? false : true;

		final boolean onNewRingingConnectionExists = XposedHelpers.findMethodExact(PKGNAME_SETTINGS + ".CallNotifier",
				lpparam.classLoader, "onNewRingingConnection", AsyncResult.class) == null ? false : true;

		pref.reload();
		if (pref.getBoolean("pref_debug", false))
			XposedBridge
					.log(String
							.format("onPhoneStateChangedExists: %b\nhandleMessageExists: %b\nonDisconnectExists: %b\nonNewRingingConnectionExists: %b",
									onPhoneStateChangedExists, handleMessageExists, onDisconnectExists,
									onNewRingingConnectionExists));

		// hook onPhoneStateChanged for outgoing calls
		if (onPhoneStateChangedExists)
			XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader,
					"onPhoneStateChanged", AsyncResult.class, new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable {
							Context context = (Context) getObjectField(param.thisObject, "mApplication");
							pref.reload();

							// get preferences
							int connectedIntensity = pref.getInt("pref_connected_vibrate_intensity", 2);
							int stateChangeThreshold = pref.getInt("pref_outgoing_state_change_threshold",
									OUTGOING_CALL_STATE_CHANGE_THRESHOLD);
							boolean debug = pref.getBoolean("pref_debug", false);

							CallManager mCM = (CallManager) getObjectField(param.thisObject, "mCM");

							Object state = XposedHelpers.callMethod(mCM, "getState");
							if (state.toString().equals("OFFHOOK")) {
								Phone fgPhone = mCM.getFgPhone();
								Call call = getCurrentCall(fgPhone);
								Connection c = getConnection(fgPhone, call);
								if (c != null) {
									Call.State cstate = call.getState();

									if (debug)
										XposedBridge.log(String.format("cstate:%s isIncoming:%b durationMillis:%d",
												cstate.toString(), c.isIncoming(), c.getDurationMillis()));

									if (cstate == Call.State.ACTIVE) {
										if (!c.isIncoming()) {
											if (c.getDurationMillis() < stateChangeThreshold) {
												// vibrate on connected outgoing call
												if (pref.getBoolean("pref_vibrate_outgoing", true))
													Utils.vibratePhone(context, Utils.patternConnected,
															connectedIntensity);

												// vibrate at fixed time on connected outgoing call
												if (pref.getBoolean("pref_vibrate_fixed_time", false)) {
													final int time = Integer.valueOf(pref.getString(
															"pref_vibrate_fixed_time_second", "0"));
													startFixedTimeVibration(param.thisObject,
															time * 1000 - c.getDurationMillis());
												}
											}

											// vibrate every minute on outgoing call
											if (pref.getBoolean("pref_vibrate_every_minute", false)) {
												final int second = Integer.valueOf(pref.getString(
														"pref_vibrate_every_minute_second", "45"));
												startEveryMinuteVibration(param.thisObject, second * 1000,
														c.getDurationMillis() % 60000);
											}

										} else if (pref.getBoolean("pref_vibrate_incoming", true) && c.isIncoming())
											// vibrate on connected incoming call
											Utils.vibratePhone(context, Utils.patternConnected, connectedIntensity);
									}
								}
							}
						}
					});

		// hook handleMessage to handle periodic/fixed time vibration new messages
		if (handleMessageExists)
			XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "handleMessage",
					Message.class, new XC_MethodHook() {
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
							Context context = (Context) getObjectField(param.thisObject, "mApplication");
							int what = ((Message) param.args[0]).what;
							switch (what) {
							case VIBRATE_EVERY_MIN:
								pref.reload();

								// get intensity preferences
								int everyMinuteIntensity = pref.getInt("pref_every_minute_vibrate_intensity", 2);

								Utils.vibratePhone(context, Utils.patternEveryMinute, everyMinuteIntensity);
								XposedHelpers.callMethod(param.thisObject, "sendEmptyMessageDelayed",
										VIBRATE_EVERY_MIN, 60000);
								param.setResult(null);
								break;
							case VIBRATE_FIXED_TIME:
								pref.reload();

								// get intensity preferences
								int fixedTimeIntensity = pref.getInt("pref_fixed_time_vibrate_intensity", 2);

								Utils.vibratePhone(context, Utils.patternFixedTime, fixedTimeIntensity);
								XposedHelpers.callMethod(param.thisObject, "removeMessages", VIBRATE_FIXED_TIME);
								param.setResult(null);
								break;
							}
						}
					});

		// hook onDisconnect for disconnected calls
		if (onDisconnectExists)
			XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "onDisconnect",
					AsyncResult.class, new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable {
							Context context = (Context) getObjectField(param.thisObject, "mApplication");
							pref.reload();

							// get intensity preferences
							int hangupIntensity = pref.getInt("pref_hangup_vibrate_intensity", 2);

							if (pref.getBoolean("pref_vibrate_hangup", true)) {
								Connection c = (Connection) ((AsyncResult) param.args[0]).result;
								if (c != null && c.getDurationMillis() > 0)
									Utils.vibratePhone(context, Utils.patternHangup, hangupIntensity);
							}

							// Stop every minute vibration
							XposedHelpers.callMethod(param.thisObject, "removeMessages", VIBRATE_EVERY_MIN);

							// Stop fixed time vibration
							XposedHelpers.callMethod(param.thisObject, "removeMessages", VIBRATE_FIXED_TIME);
						}
					});

		// hook onNewRingingConnection for new call waiting
		if (onNewRingingConnectionExists)
			XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader,
					"onNewRingingConnection", AsyncResult.class, new XC_MethodHook() {
						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable {
							Context context = (Context) getObjectField(param.thisObject, "mApplication");
							pref.reload();

							// get intensity preferences
							int callWaitingIntensity = pref.getInt("pref_call_waiting_vibrate_intensity", 2);

							if (pref.getBoolean("pref_vibrate_call_waiting", true)) {
								CallManager cm = (CallManager) getObjectField(param.thisObject, "mCM");
								Connection c = (Connection) ((AsyncResult) param.args[0]).result;
								Call.State state = c.getState();

								if (!isRealIncomingCall(state, cm))
									Utils.vibratePhone(context, Utils.patternCallWaiting, callWaitingIntensity);
							}
						}
					});

	}

	// static helper functions from CM source code
	static Call getCurrentCall(Phone phone) {
		Call ringing = phone.getRingingCall();
		Call fg = phone.getForegroundCall();
		Call bg = phone.getBackgroundCall();
		if (!ringing.isIdle()) {
			return ringing;
		}
		if (!fg.isIdle()) {
			return fg;
		}
		if (!bg.isIdle()) {
			return bg;
		}
		return fg;
	}

	static Connection getConnection(Phone phone, Call call) {
		if (call == null) {
			return null;
		}
		if (phone.getPhoneType() == Phone.PHONE_TYPE_CDMA) {
			return call.getLatestConnection();
		}
		return call.getEarliestConnection();
	}

	static boolean isRealIncomingCall(Call.State state, CallManager cm) {
		return (state == Call.State.INCOMING && !cm.hasActiveFgCall());
	}

	static void startEveryMinuteVibration(Object thisObject, long second, long callDurationMsec) {
		XposedHelpers.callMethod(thisObject, "removeMessages", VIBRATE_EVERY_MIN);

		long timer;
		if (callDurationMsec > second) {
			// Schedule the alarm at the next minute + defined secs
			timer = second + 60000 - callDurationMsec;
		} else {
			// Schedule the alarm at the first defined second mark
			timer = second - callDurationMsec;
		}

		XposedHelpers.callMethod(thisObject, "sendEmptyMessageDelayed", VIBRATE_EVERY_MIN, timer);
	}

	static void startFixedTimeVibration(Object thisObject, long MillisBeforeVibration) {
		XposedHelpers.callMethod(thisObject, "removeMessages", VIBRATE_FIXED_TIME);

		if (MillisBeforeVibration > 0)
			XposedHelpers.callMethod(thisObject, "sendEmptyMessageDelayed", VIBRATE_FIXED_TIME, MillisBeforeVibration);
	}
}
