package com.gzplanet.xposed.xperiaphonevibrator;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.Vibrator;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XperiaPhoneVibrator implements IXposedHookZygoteInit, IXposedHookLoadPackage {
	final static String PKGNAME_SETTINGS = "com.android.phone";

	final long[] patternConnected = new long[] { 0, 100, 0, 0 };
	final long[] patternHangup = new long[] { 0, 50, 100, 50 };
	final long[] patternCallWaiting = new long[] { 0, 200, 300, 500 };
	final long[] pattern45Sec = new long[] { 0, 70, 0, 0 };

	// potential conflict if 200 is already used
	private static final int VIBRATE_45_SEC = 200;

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

		// hook onPhoneStateChanged for outgoing calls
		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "onPhoneStateChanged",
				AsyncResult.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						pref.reload();
						CallManager mCM = (CallManager) getObjectField(param.thisObject, "mCM");

						Object state = XposedHelpers.callMethod(mCM, "getState");
						if (state.toString().equals("OFFHOOK")) {
							Phone fgPhone = mCM.getFgPhone();
							Call call = getCurrentCall(fgPhone);
							Connection c = getConnection(fgPhone, call);
							if (c != null) {
								Call.State cstate = call.getState();

//								XposedBridge.log(String.format("cstate:%d isIncoming:%b durationMillis:%d",
//										cstate.ordinal(), c.isIncoming(), c.getDurationMillis()));

								if (cstate == Call.State.ACTIVE) {
									if (!c.isIncoming()) {
										if (pref.getBoolean("pref_vibrate_outgoing", true)
												&& c.getDurationMillis() < 200)
											vibratePhone(param.thisObject, patternConnected);

										if (pref.getBoolean("pref_vibrate_45_sec", false))
											start45SecondVibration(param.thisObject, c.getDurationMillis() % 60000);
									} else if (pref.getBoolean("pref_vibrate_incoming", true) && c.isIncoming())
										vibratePhone(param.thisObject, patternConnected);
								}
							}
						}
					}
				});

		// hook onDisconnect for disconnected calls
		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "onDisconnect",
				AsyncResult.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						pref.reload();
						if (pref.getBoolean("pref_vibrate_hangup", true)) {
							Connection c = (Connection) ((AsyncResult) param.args[0]).result;
							if (c != null && c.getDurationMillis() > 0)
								vibratePhone(param.thisObject, patternHangup);
						}
						
						// Stop 45-second vibration
						XposedHelpers.callMethod(param.thisObject, "removeMessages", VIBRATE_45_SEC);
					}
				});

		// hook onNewRingingConnection for new call waiting
		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader,
				"onNewRingingConnection", AsyncResult.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						pref.reload();
						if (pref.getBoolean("pref_vibrate_call_waiting", true)) {
							CallManager cm = (CallManager) getObjectField(param.thisObject, "mCM");
							Connection c = (Connection) ((AsyncResult) param.args[0]).result;
							Call.State state = c.getState();

							if (!isRealIncomingCall(state, cm))
								vibratePhone(param.thisObject, patternCallWaiting);
						}
					}
				});

		// hook handleMessage to handle periodic vibration (45 second mark every minute)
		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "handleMessage",
				Message.class, new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
						int what = ((Message) param.args[0]).what;
						if (what == VIBRATE_45_SEC) {
							vibratePhone(param.thisObject, pattern45Sec);
							XposedHelpers
									.callMethod(param.thisObject, "sendEmptyMessageDelayed", VIBRATE_45_SEC, 60000);
							param.setResult(null);
						}
					}
				});

	}

	void vibratePhone(Object thisObject, long[] pattern) {
		Context app = (Context) getObjectField(thisObject, "mApplication");
		Vibrator vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
		vibrator.vibrate(pattern, -1);
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

	static void start45SecondVibration(Object thisObject, long callDurationMsec) {
		XposedHelpers.callMethod(thisObject, "removeMessages", VIBRATE_45_SEC);

		long timer;
		if (callDurationMsec > 45000) {
			// Schedule the alarm at the next minute + 45 secs
			timer = 45000 + 60000 - callDurationMsec;
		} else {
			// Schedule the alarm at the first 45 second mark
			timer = 45000 - callDurationMsec;
		}

		XposedHelpers.callMethod(thisObject, "sendEmptyMessageDelayed", VIBRATE_45_SEC, timer);
	}
}
