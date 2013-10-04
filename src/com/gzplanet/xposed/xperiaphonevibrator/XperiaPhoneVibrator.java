package com.gzplanet.xposed.xperiaphonevibrator;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Vibrator;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XperiaPhoneVibrator implements IXposedHookLoadPackage {
	final static String PKGNAME_SETTINGS = "com.android.phone";
	final long[] patternConnected = new long[] { 0, 100, 0, 0 };
	final long[] patternEnded = new long[] { 0, 50, 100, 50 };

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals(PKGNAME_SETTINGS))
			return;

		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "onPhoneStateChanged",
				AsyncResult.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						CallManager mCM = (CallManager) getObjectField(param.thisObject, "mCM");

						Object state = XposedHelpers.callMethod(mCM, "getState");
//						XposedBridge.log("state: " + state.toString());
						if (state.toString().equals("OFFHOOK")) {
							Phone fgPhone = mCM.getFgPhone();
							Call call = getCurrentCall(fgPhone);
							Connection c = getConnection(fgPhone, call);
							if (c != null) {
								Call.State cstate = call.getState();
/*								XposedBridge.log(String.format("cstate:%d isIncoming:%b durationMillis:%d",
										cstate.ordinal(), c.isIncoming(), c.getDurationMillis()));
*/
								if (cstate == Call.State.ACTIVE && !c.isIncoming() && c.getDurationMillis() < 200)
									vibratePhone(param.thisObject, patternConnected);
							}
						}
					}
				});

		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".CallNotifier", lpparam.classLoader, "onDisconnect",
				AsyncResult.class, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Connection c = (Connection) ((AsyncResult) param.args[0]).result;
						if (c != null && c.getDurationMillis() > 0)
							vibratePhone(param.thisObject, patternEnded);
					}
				});
	}

	void vibratePhone(Object thisObject, long[] pattern) {
		Context app = (Context) getObjectField(thisObject, "mApplication");
		Vibrator vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
		vibrator.vibrate(pattern, -1);
	}

	// static helper functions from CM
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
}
