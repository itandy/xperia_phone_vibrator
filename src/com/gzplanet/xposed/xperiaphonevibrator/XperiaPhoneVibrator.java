package com.gzplanet.xposed.xperiaphonevibrator;

import static de.robv.android.xposed.XposedHelpers.getObjectField;
import android.content.Context;
import android.os.Vibrator;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XperiaPhoneVibrator implements IXposedHookLoadPackage {
	final static String PKGNAME_SETTINGS = "com.android.phone";

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals(PKGNAME_SETTINGS))
			return;

		XposedHelpers.findAndHookMethod(PKGNAME_SETTINGS + ".LargeCallView", lpparam.classLoader,
				"hideCallingProgress", new XC_MethodHook() {
					@Override
					protected void afterHookedMethod(MethodHookParam param) throws Throwable {
						Context context = (Context) getObjectField(param.thisObject, "mContext");

						Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
						vibrator.vibrate(150);
					}
				});
	}

}
