package com.gzplanet.xposed.xperiaphonevibrator;

import android.app.Activity;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.text.InputType;
import android.widget.Toast;

public class XposedSettings extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name);
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		if (savedInstanceState == null)
			getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefMainFragment()).commit();
	}

	public static class PrefMainFragment extends PreferenceFragment {
		int mVibrateEveryMinuteSecond;
		int mVibrateFixedTimeSecond;
		EditTextPreference mPrefEveryMinuteSecond;
		CheckBoxPreference mPrefEveryMinute;
		EditTextPreference mPrefFixedTimeSecond;
		CheckBoxPreference mPrefFixedTime;
		Preference mPrefVibrateIntensity;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// this is important because although the handler classes that read these settings
			// are in the same package, they are executed in the context of the hooked package
			getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
			addPreferencesFromResource(R.xml.preferences);

			// configure vibrate every minute preferences
			mVibrateEveryMinuteSecond = Integer.valueOf(getPreferenceManager().getSharedPreferences().getString(
					"pref_vibrate_every_minute_second", "45"));

			mPrefEveryMinute = (CheckBoxPreference) findPreference("pref_vibrate_every_minute");
			mPrefEveryMinute.setSummary(String.format(
					getActivity().getResources().getString(R.string.summ_vibrate_every_minute),
					mVibrateEveryMinuteSecond));

			mPrefEveryMinuteSecond = (EditTextPreference) findPreference("pref_vibrate_every_minute_second");
			mPrefEveryMinuteSecond.getEditText().setInputType(
					InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
			mPrefEveryMinuteSecond.getEditText().setRawInputType(InputType.TYPE_CLASS_PHONE);
			mPrefEveryMinuteSecond.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					try {
						if (Integer.valueOf(newValue.toString()) <= 0 || Integer.valueOf(newValue.toString()) >= 60) {
							Toast.makeText(getActivity(),
									getActivity().getResources().getString(R.string.text_invalid_value),
									Toast.LENGTH_LONG).show();
							return false;
						}
					} catch (Exception e) {
						Toast.makeText(getActivity(),
								getActivity().getResources().getString(R.string.text_invalid_value), Toast.LENGTH_LONG)
								.show();
						return false;
					}
					mVibrateEveryMinuteSecond = Integer.valueOf(newValue.toString());
					mPrefEveryMinute.setSummary(String.format(
							getActivity().getResources().getString(R.string.summ_vibrate_every_minute),
							mVibrateEveryMinuteSecond));
					return true;
				}
			});

			// configure vibrate fixed time preferences
			mVibrateFixedTimeSecond = Integer.valueOf(getPreferenceManager().getSharedPreferences().getString(
					"pref_vibrate_fixed_time_second", "0"));

			mPrefFixedTime = (CheckBoxPreference) findPreference("pref_vibrate_fixed_time");

			mPrefFixedTimeSecond = (EditTextPreference) findPreference("pref_vibrate_fixed_time_second");
			mPrefFixedTimeSecond.getEditText().setInputType(
					InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
			mPrefFixedTimeSecond.getEditText().setRawInputType(InputType.TYPE_CLASS_PHONE);
			mPrefFixedTimeSecond.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					try {
						if (Integer.valueOf(newValue.toString()) <= 0) {
							Toast.makeText(getActivity(),
									getActivity().getResources().getString(R.string.text_invalid_value),
									Toast.LENGTH_LONG).show();
							return false;
						}
					} catch (Exception e) {
						Toast.makeText(getActivity(),
								getActivity().getResources().getString(R.string.text_invalid_value), Toast.LENGTH_LONG)
								.show();
						return false;
					}
					mVibrateFixedTimeSecond = Integer.valueOf(newValue.toString());
					mPrefFixedTime.setSummary(String.format(
							getActivity().getResources().getString(R.string.summ_vibrate_fixed_time),
							Utils.secondToText(getActivity(), mVibrateFixedTimeSecond)));
					return true;
				}
			});

			if (mVibrateFixedTimeSecond > 0)
				mPrefFixedTime.setSummary(String.format(
						getActivity().getResources().getString(R.string.summ_vibrate_fixed_time),
						Utils.secondToText(getActivity(), mVibrateFixedTimeSecond)));
			else
				mPrefFixedTime.setSummary(getActivity().getResources().getString(
						R.string.summ_vibrate_fixed_time_undefine));

			// configure vibration intensity preference
			mPrefVibrateIntensity = (Preference) findPreference("pref_vibrate_intensity");
			mPrefVibrateIntensity.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					PrefVibrationIntensityFragment fragVibrationIntensity = new PrefVibrationIntensityFragment();
					getActivity().getFragmentManager().beginTransaction().addToBackStack(null)
							.replace(android.R.id.content, fragVibrationIntensity).commit();
					return true;
				}
			});

		}
	}

	public static class PrefVibrationIntensityFragment extends PreferenceFragment {
		// used to avoid vibration when fragment is first created
		boolean onCreate1 = true;
		boolean onCreate2 = true;
		boolean onCreate3 = true;
		boolean onCreate4 = true;
		boolean onCreate5 = true;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);

			// this is important because although the handler classes that read these settings
			// are in the same package, they are executed in the context of the hooked package
			getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
			addPreferencesFromResource(R.xml.preference_vibration_intensity);

			findPreference("pref_connected_vibrate_intensity").setOnPreferenceChangeListener(
					new OnPreferenceChangeListener() {

						@Override
						public boolean onPreferenceChange(Preference preference, Object newValue) {
							if (!onCreate1)
								Utils.vibratePhone(getActivity(), Utils.patternConnected, (Integer) newValue);
							onCreate1 = false;
							return true;
						}

					});

			findPreference("pref_hangup_vibrate_intensity").setOnPreferenceChangeListener(
					new OnPreferenceChangeListener() {

						@Override
						public boolean onPreferenceChange(Preference preference, Object newValue) {
							if (!onCreate2)
								Utils.vibratePhone(getActivity(), Utils.patternHangup, (Integer) newValue);
							onCreate2 = false;
							return true;
						}

					});

			findPreference("pref_call_waiting_vibrate_intensity").setOnPreferenceChangeListener(
					new OnPreferenceChangeListener() {

						@Override
						public boolean onPreferenceChange(Preference preference, Object newValue) {
							if (!onCreate3)
								Utils.vibratePhone(getActivity(), Utils.patternCallWaiting, (Integer) newValue);
							onCreate3 = false;
							return true;
						}

					});

			findPreference("pref_every_minute_vibrate_intensity").setOnPreferenceChangeListener(
					new OnPreferenceChangeListener() {

						@Override
						public boolean onPreferenceChange(Preference preference, Object newValue) {
							if (!onCreate4)
								Utils.vibratePhone(getActivity(), Utils.patternEveryMinute, (Integer) newValue);
							onCreate4 = false;
							return true;
						}

					});

			findPreference("pref_fixed_time_vibrate_intensity").setOnPreferenceChangeListener(
					new OnPreferenceChangeListener() {

						@Override
						public boolean onPreferenceChange(Preference preference, Object newValue) {
							if (!onCreate5)
								Utils.vibratePhone(getActivity(), Utils.patternFixedTime, (Integer) newValue);
							onCreate5 = false;
							return true;
						}

					});
		}
	}

}