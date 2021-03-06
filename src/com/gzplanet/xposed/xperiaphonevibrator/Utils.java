package com.gzplanet.xposed.xperiaphonevibrator;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Vibrator;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Utils {
    protected final static String PREF_AUTHORITY = "com.gzplanet.app.xperiaphonevibrator_preferences";
    protected final static String PREF_NAME = "com.gzplanet.xposed.xperiaphonevibrator_preferences";

    // default vibration patterns
    public final static long[] patternConnected = new long[]{0, 100, 0, 0};
    public final static long[] patternHangup = new long[]{0, 50, 100, 50};
    public final static long[] patternCallWaiting = new long[]{0, 200, 300, 500};
    public final static long[] patternEveryMinute = new long[]{0, 70, 0, 0};
    public final static long[] patternFixedTime = new long[]{0, 140, 0, 0};

    // convert number of seconds to human friendly text
    public static String secondToText(Context context, int seconds) {
        String result = "";

        if (seconds == 0) {
            result = context.getResources().getString(R.string.text_disabled);
        } else {
            int sec = seconds % 60;
            if (sec > 0) {
                result = context.getResources().getQuantityString(R.plurals.numberOfSeconds, sec, sec);
                seconds = seconds - sec;
            }

            int min = seconds % 3600;
            if (min > 0) {
                result = context.getResources().getQuantityString(R.plurals.numberOfMinutes, min / 60, min / 60) + " "
                        + result;
                seconds = seconds - min;
            }

            int hour = seconds % 86400;
            if (hour > 0) {
                result = context.getResources().getQuantityString(R.plurals.numberOfHours, hour / 3600, hour / 3600)
                        + " " + result;
                seconds = seconds - hour;
            }

            if (seconds > 0) {
                result = context.getResources().getQuantityString(R.plurals.numberOfDays, seconds / 86400,
                        seconds / 86400)
                        + " " + result;
            }
        }

        return result;
    }

    // handle phone vibration with selected intensity
    static void vibratePhone(Context context, long[] pattern, int intensity) {
        long[] newPattern = new long[pattern.length];

        // adjust vibration intensity according to preference
        for (int i = 0; i < pattern.length; i++) {
            newPattern[i] = pattern[i];
            if (i == 1 || i == 3)
                newPattern[i] = Math.round((float) newPattern[i] * (float) intensity / 2f);
        }
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(newPattern, -1);
    }

    static void dumpPrefs(String section, SharedPreferences pref) {
        XposedBridge.log(String.format("(%s) pref_connected_vibrate_intensity:%d, pref_hangup_vibrate_intensity:%d, " +
                "pref_vibrate_outgoing:%b, pref_vibrate_fixed_time:%b, " +
                "pref_vibrate_fixed_time_second:%s, pref_vibrate_every_minute:%b, " +
                "pref_vibrate_every_minute_second:%s, pref_vibrate_incoming:%b, " +
                "pref_vibrate_hangup:%b, pref_call_waiting_vibrate_intensity:%d, pref_vibrate_call_waiting:%b",
                section,
                pref.getInt("pref_connected_vibrate_intensity", -1),
                pref.getInt("pref_hangup_vibrate_intensity", -1),
                pref.getBoolean("pref_vibrate_outgoing", false),
                pref.getBoolean("pref_vibrate_fixed_time", false),
                pref.getString("pref_vibrate_fixed_time_second", "-1"),
                pref.getBoolean("pref_vibrate_every_minute", false),
                pref.getString("pref_vibrate_every_minute_second", "-1"),
                pref.getBoolean("pref_vibrate_incoming", false),
                pref.getBoolean("pref_vibrate_hangup", false),
                pref.getInt("pref_call_waiting_vibrate_intensity", -1),
                pref.getBoolean("pref_vibrate_call_waiting", false)
        ));
    }
}
