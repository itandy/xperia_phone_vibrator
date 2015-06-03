package com.gzplanet.xposed.xperiaphonevibrator;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

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
	final static String TAG = "XperiaPhoneVibrator";

	final static String PKGNAME_PHONE = "com.android.phone";
	final static String PKGNAME_TELECOM = "com.android.server.telecom";
	final static String CLASSNAME_CALLSTATE = "android.telecom.CallState";

	private static final int OUTGOING_CALL_STATE_CHANGE_THRESHOLD = 200;

	// potential conflict if 200 or above message ID is already used
	private static final int VIBRATE_EVERY_MIN = 200;
	private static final int VIBRATE_FIXED_TIME = VIBRATE_EVERY_MIN + 1;

	// android.telecom.CallState, for Lollipop
	static final int NEW = 0;
	static final int CONNECTING = 1;
	static final int PRE_DIAL_WAIT = 2;
	static final int DIALING = 3;
	static final int RINGING = 4;
	static final int ACTIVE = 5;
	static final int ON_HOLD = 6;
	static final int DISCONNECTED = 7;
	static final int ABORTED = 8;
	static final int DISCONNECTING = 9;

	private static XSharedPreferences pref;

	static Class<?> mPhoneStateBroadcaster = null;
	static Class<?> mCall = null;
	static Class<?> mCallState = null;

	static Handler mHandler;
	static WakeLock mWakeLock;
	static Runnable mFixedTimeHandler;
	static Runnable mPeriodicHandler;
	static Context mContext;
	static int mEverySecond;
	static int mPrevState = NEW;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		pref = new XSharedPreferences(XperiaPhoneVibrator.class.getPackage().getName());
		// just in case the preference file permission is reset by
		// recovery/script
		pref.makeWorldReadable();
	}

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals(PKGNAME_PHONE) && !lpparam.packageName.equals(PKGNAME_TELECOM))
			return;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { /*
																	 * post
																	 * Lollipop
																	 * implementation
																	 */
			if (lpparam.packageName.equals(PKGNAME_TELECOM)) {
				boolean onCallStateChanged = false;
				boolean onCallAdded = false;

				try {
					mPhoneStateBroadcaster = XposedHelpers.findClass(PKGNAME_TELECOM + ".PhoneStateBroadcaster", lpparam.classLoader);
					mCall = XposedHelpers.findClass(PKGNAME_TELECOM + ".Call", lpparam.classLoader);
					mCallState = XposedHelpers.findClass(CLASSNAME_CALLSTATE, lpparam.classLoader);
					onCallStateChanged = XposedHelpers.findMethodExact(mPhoneStateBroadcaster, "onCallStateChanged", mCall, int.class, int.class) == null ? false
							: true;
				} catch (Exception e) {
				}

				try {
					mPhoneStateBroadcaster = XposedHelpers.findClass(PKGNAME_TELECOM + ".PhoneStateBroadcaster", lpparam.classLoader);
					mCall = XposedHelpers.findClass(PKGNAME_TELECOM + ".Call", lpparam.classLoader);
					mCallState = XposedHelpers.findClass(CLASSNAME_CALLSTATE, lpparam.classLoader);
					onCallAdded = XposedHelpers.findMethodExact(mPhoneStateBroadcaster, "onCallAdded", mCall) == null ? false : true;
				} catch (Exception e) {
				}

				pref.reload();
				if (pref.getBoolean("pref_debug", false))
					XposedBridge.log(String.format("onCallStateChanged:%b, onCallAdded:%b", onCallStateChanged, onCallAdded));

				XposedHelpers.findAndHookMethod(mPhoneStateBroadcaster, "onCallStateChanged", mCall, int.class, int.class, new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						try {
							// get preferences
							pref.reload();
							int connectedIntensity = pref.getInt("pref_connected_vibrate_intensity", 2);
							int hangupIntensity = pref.getInt("pref_hangup_vibrate_intensity", 2);
							boolean debug = pref.getBoolean("pref_debug", false);

							Object call = param.args[0];
							int oldState = (Integer) param.args[1];
							int newState = (Integer) param.args[2];
							if (mContext == null)
								mContext = (Context) XposedHelpers.callMethod(call, "getContext");

							if (debug)
								XposedBridge.log(String.format("onCallStateChanged: oldState:%s newState:%s context:%b",
										XposedHelpers.callStaticMethod(mCallState, "toString", oldState).toString(),
										XposedHelpers.callStaticMethod(mCallState, "toString", newState).toString(), mContext == null));

							if (oldState == DIALING && newState == ACTIVE) {
								// connected outgoing call
								if (pref.getBoolean("pref_vibrate_outgoing", true))
									Utils.vibratePhone(mContext, Utils.patternConnected, connectedIntensity);

								// fixed time on connected outgoing call
								if (pref.getBoolean("pref_vibrate_fixed_time", false)) {
									final int time = Integer.valueOf(pref.getString("pref_vibrate_fixed_time_second", "0"));
									startFixedTimeVibration(time * 1000);
								}

								// periodically on connected outgoing
								// call
								if (pref.getBoolean("pref_vibrate_every_minute", false)) {
									mEverySecond = Integer.valueOf(pref.getString("pref_vibrate_every_minute_second", "45"));
									startEveryMinuteVibration();

								}
							} else if (oldState == RINGING && newState == ACTIVE) {
								// connected incoming call
								if (pref.getBoolean("pref_vibrate_incoming", true))
									Utils.vibratePhone(mContext, Utils.patternConnected, connectedIntensity);
							} else if (newState == DISCONNECTED) {
								// hang up
								if (pref.getBoolean("pref_vibrate_hangup", true))
									Utils.vibratePhone(mContext, Utils.patternHangup, hangupIntensity);

								// release partial wake lock
								if (mWakeLock != null)
									if (mWakeLock.isHeld())
										mWakeLock.release();

								// remove any pending fixed time or
								// periodic handlers
								if (mHandler != null) {
									if (mPeriodicHandler != null)
										mHandler.removeCallbacks(mPeriodicHandler);
									if (mFixedTimeHandler != null)
										mHandler.removeCallbacks(mFixedTimeHandler);
								}
							}
							mPrevState = newState;
						} catch (Exception e) {
						}
					}
				});

				XposedHelpers.findAndHookMethod(mPhoneStateBroadcaster, "onCallAdded", mCall, new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						try {
							// get preferences
							pref.reload();
							int callWaitingIntensity = pref.getInt("pref_call_waiting_vibrate_intensity", 2);
							boolean debug = pref.getBoolean("pref_debug", false);

							if (pref.getBoolean("pref_vibrate_call_waiting", true)) {
								Object call = param.args[0];
								if (mContext == null)
									mContext = (Context) XposedHelpers.callMethod(call, "getContext");

								int state = (Integer) XposedHelpers.callMethod(call, "getState");
								if (debug)
									XposedBridge.log(String.format("onCallAdded: state:%s", XposedHelpers.callStaticMethod(mCallState, "toString", state)
											.toString()));

								if (mPrevState == ACTIVE && state == RINGING)
									Utils.vibratePhone(mContext, Utils.patternCallWaiting, callWaitingIntensity);
							}

						} catch (Exception e) {
						}
					}
				});
			}
		} else if (lpparam.packageName.equals(PKGNAME_PHONE)) { /*
																 * pre Lollipop
																 * implementation
																 */
			// sanity check for methods to hook
			boolean onPhoneStateChangedExists = false;
			boolean handleMessageExists = false;
			boolean onDisconnectExists = false;
			boolean onNewRingingConnectionExists = false;
			try {
				onPhoneStateChangedExists = XposedHelpers.findMethodExact(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "onPhoneStateChanged",
						AsyncResult.class) == null ? false : true;
			} catch (Exception e) {
			}

			try {
				handleMessageExists = XposedHelpers.findMethodExact(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "handleMessage", Message.class) == null ? false
						: true;
			} catch (Exception e) {
			}

			try {
				onDisconnectExists = XposedHelpers.findMethodExact(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "onDisconnect", AsyncResult.class) == null ? false
						: true;
			} catch (Exception e) {
			}

			try {
				onNewRingingConnectionExists = XposedHelpers.findMethodExact(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "onNewRingingConnection",
						AsyncResult.class) == null ? false : true;
			} catch (Exception e) {
			}

			pref.reload();
			if (pref.getBoolean("pref_debug", false))
				XposedBridge.log(String.format(
						"onPhoneStateChangedExists: %b\nhandleMessageExists: %b\nonDisconnectExists: %b\nonNewRingingConnectionExists: %b",
						onPhoneStateChangedExists, handleMessageExists, onDisconnectExists, onNewRingingConnectionExists));

			// hook onPhoneStateChanged for outgoing calls
			if (onPhoneStateChangedExists)
				XposedHelpers.findAndHookMethod(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "onPhoneStateChanged", AsyncResult.class,
						new XC_MethodHook() {
							@Override
							protected void afterHookedMethod(MethodHookParam param) throws Throwable {
								Context context = (Context) getObjectField(param.thisObject, "mApplication");
								pref.reload();

								// get preferences
								int connectedIntensity = pref.getInt("pref_connected_vibrate_intensity", 2);
								int stateChangeThreshold = pref.getInt("pref_outgoing_state_change_threshold", OUTGOING_CALL_STATE_CHANGE_THRESHOLD);
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
											XposedBridge.log(String.format("cstate:%s isIncoming:%b durationMillis:%d", cstate.toString(), c.isIncoming(),
													c.getDurationMillis()));

										if (cstate == Call.State.ACTIVE) {
											if (!c.isIncoming()) {
												if (c.getDurationMillis() < stateChangeThreshold) {
													// vibrate on connected
													// outgoing
													// call
													if (pref.getBoolean("pref_vibrate_outgoing", true))
														Utils.vibratePhone(context, Utils.patternConnected, connectedIntensity);

													// vibrate at fixed time on
													// connected outgoing call
													if (pref.getBoolean("pref_vibrate_fixed_time", false)) {
														final int time = Integer.valueOf(pref.getString("pref_vibrate_fixed_time_second", "0"));
														startFixedTimeVibration(param.thisObject, time * 1000 - c.getDurationMillis());
													}
												}

												// vibrate every minute on
												// outgoing
												// call
												if (pref.getBoolean("pref_vibrate_every_minute", false)) {
													final int second = Integer.valueOf(pref.getString("pref_vibrate_every_minute_second", "45"));
													startEveryMinuteVibration(param.thisObject, second * 1000, c.getDurationMillis() % 60000);
												}

											} else if (pref.getBoolean("pref_vibrate_incoming", true) && c.isIncoming())
												// vibrate on connected incoming
												// call
												Utils.vibratePhone(context, Utils.patternConnected, connectedIntensity);
										}
									}
								}
							}
						});

			// hook handleMessage to handle periodic/fixed time vibration new
			// messages
			if (handleMessageExists)
				XposedHelpers.findAndHookMethod(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "handleMessage", Message.class, new XC_MethodHook() {
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
							XposedHelpers.callMethod(param.thisObject, "sendEmptyMessageDelayed", VIBRATE_EVERY_MIN, 60000);
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
				XposedHelpers.findAndHookMethod(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "onDisconnect", AsyncResult.class, new XC_MethodHook() {
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
				XposedHelpers.findAndHookMethod(PKGNAME_PHONE + ".CallNotifier", lpparam.classLoader, "onNewRingingConnection", AsyncResult.class,
						new XC_MethodHook() {
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

	// Lollipop version
	static void startEveryMinuteVibration() {
		if (mWakeLock == null) {
			PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		}

		if (mHandler == null)
			mHandler = new Handler();

		if (mPeriodicHandler == null)
			mPeriodicHandler = new Runnable() {
				@Override
				public void run() {
					// extend partial wake lock
					if (mWakeLock != null) {
						if (mWakeLock.isHeld()) {
							mWakeLock.release();
						}
						mWakeLock.acquire(mEverySecond * 1000 + 1000);
					}

					pref.reload();

					// get intensity preferences
					int periodicIntensity = pref.getInt("pref_every_minute_vibrate_intensity", 2);

					Utils.vibratePhone(mContext, Utils.patternEveryMinute, periodicIntensity);
					mHandler.postDelayed(mPeriodicHandler, mEverySecond * 1000);
				}

			};

		mHandler.removeCallbacks(mPeriodicHandler);

		// acquire partial wake lock
		if (mEverySecond > 0) {
			if (mWakeLock != null) {
				if (mWakeLock.isHeld()) {
					mWakeLock.release();
				}
				mWakeLock.acquire(mEverySecond * 1000 + 1000);
			}

			mHandler.postDelayed(mPeriodicHandler, mEverySecond * 1000);
		}
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

	// Lollipop version
	static void startFixedTimeVibration(long millisBeforeVibration) {
		if (mHandler == null)
			mHandler = new Handler();

		if (mFixedTimeHandler == null)
			mFixedTimeHandler = new Runnable() {
				@Override
				public void run() {
					pref.reload();

					// get intensity preferences
					int fixedTimeIntensity = pref.getInt("pref_fixed_time_vibrate_intensity", 2);

					Utils.vibratePhone(mContext, Utils.patternFixedTime, fixedTimeIntensity);
					mHandler.removeCallbacks(mFixedTimeHandler);
				}

			};

		mHandler.removeCallbacks(mFixedTimeHandler);

		if (millisBeforeVibration > 0)
			mHandler.postDelayed(mFixedTimeHandler, millisBeforeVibration);
	}

	// pre Lollipop version
	static void startFixedTimeVibration(Object thisObject, long MillisBeforeVibration) {
		XposedHelpers.callMethod(thisObject, "removeMessages", VIBRATE_FIXED_TIME);

		if (MillisBeforeVibration > 0)
			XposedHelpers.callMethod(thisObject, "sendEmptyMessageDelayed", VIBRATE_FIXED_TIME, MillisBeforeVibration);
	}
}
