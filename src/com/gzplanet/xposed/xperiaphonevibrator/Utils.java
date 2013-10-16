package com.gzplanet.xposed.xperiaphonevibrator;

import android.content.Context;

public class Utils {
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
}
