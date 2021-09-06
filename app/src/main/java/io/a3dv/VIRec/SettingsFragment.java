package io.a3dv.VIRec;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Range;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Arrays;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * Checks that a preference is a valid numerical value
     */
    Preference.OnPreferenceChangeListener checkISOListener = (preference, newValue) -> {
        //Check that the string is an integer.
        return checkIso(newValue);
    };

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

        PreferenceManager.getDefaultSharedPreferences(
                getActivity()).registerOnSharedPreferenceChangeListener(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                getActivity());

        ListPreference cameraList = getPreferenceManager().findPreference("prefCamera");
        ListPreference cameraList2 = getPreferenceManager().findPreference("prefCamera2");
        ListPreference cameraRez = getPreferenceManager().findPreference("prefSizeRaw");
//        ListPreference cameraFocus = (ListPreference)
//                getPreferenceManager().findPreference("prefFocusDistance");

        EditTextPreference prefISO = getPreferenceScreen().findPreference("prefISO");
        EditTextPreference prefExposureTime = getPreferenceScreen().findPreference("prefExposureTime");

        assert prefISO != null;
        prefISO.setOnPreferenceChangeListener(checkISOListener);

        try {
            Activity activity = getActivity();
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            int cameraSize = manager.getCameraIdList().length;
            CharSequence[] entries = new CharSequence[cameraSize];
            CharSequence[] entriesValues = new CharSequence[cameraSize];
            for (int i = 0; i < cameraSize; i++) {
                String cameraId = manager.getCameraIdList()[i];
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                try {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraMetadata.LENS_FACING_BACK) {
                        entries[i] = cameraId + " - Lens Facing Back";
                    } else if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraMetadata.LENS_FACING_FRONT) {
                        entries[i] = cameraId + " - Lens Facing Front";
                    } else {
                        entries[i] = cameraId + " - Lens External";
                    }
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    entries[i] = cameraId + " - Lens Facing Unknown";
                }
                entriesValues[i] = cameraId;
            }

            // Update our settings entry
            assert cameraList != null;
            cameraList.setEntries(entries);
            cameraList.setEntryValues(entriesValues);
            cameraList.setDefaultValue(entriesValues[0]);

            if (sharedPreferences.getString("prefCamera", "None").equals("None")) {
                cameraList.setValue((String) entriesValues[0]);
            }

            assert cameraList2 != null;
            if (cameraSize > 1) {
                cameraList2.setEntries(entries);
                cameraList2.setEntryValues(entriesValues);
                cameraList2.setDefaultValue(entriesValues[1]);

                if (sharedPreferences.getString("prefCamera2", "None").equals("None")) {
                    cameraList2.setValue((String) entriesValues[1]);
                }
            } else {
                cameraList2.setEnabled(false);
            }


            // Do not call "cameraList.setValueIndex(0)" which will invoke onSharedPreferenceChanged
            // if the previous camera is not 0, and cause null pointer exception.

            // Right now we have selected the first camera, so lets populate the resolution list
            // We should just use the default if there is not a shared setting yet
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(
                    sharedPreferences.getString("prefCamera", entriesValues[0].toString()));
            StreamConfigurationMap streamConfigurationMap = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

            int rezSize = sizes.length;
            CharSequence[] rez = new CharSequence[rezSize];
            CharSequence[] rezValues = new CharSequence[rezSize];
            int defaultIndex = 0;
            for (int i = 0; i < sizes.length; i++) {
                rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                if (sizes[i].getWidth() + sizes[i].getHeight() ==
                        DesiredCameraSetting.mDesiredFrameWidth +
                                DesiredCameraSetting.mDesiredFrameHeight) {
                    defaultIndex = i;
                }
            }

            assert cameraRez != null;
            cameraRez.setEntries(rez);
            cameraRez.setEntryValues(rezValues);
            cameraRez.setDefaultValue(rezValues[defaultIndex]);

            Range<Integer> isoRange = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (isoRange != null) {
                String rangeStr = "[" + isoRange.getLower() + "," + isoRange.getUpper() + "] (1)";
                prefISO.setDialogTitle("Adjust ISO in range " + rangeStr);
            }

            Range<Long> exposureTimeRangeNs = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (exposureTimeRangeNs != null) {
                Range<Float> exposureTimeRangeMs = new Range<>(
                        (float) (exposureTimeRangeNs.getLower().floatValue() / 1e6),
                        (float) (exposureTimeRangeNs.getUpper().floatValue() / 1e6));
                String rangeStr = "[" + exposureTimeRangeMs.getLower() + "," +
                        exposureTimeRangeMs.getUpper() + "] (ms)";
                assert prefExposureTime != null;
                prefExposureTime.setDialogTitle("Adjust exposure time in range " + rangeStr);
            }

            // Get the possible focus lengths, on non-optical devices this only has one value
            // https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics.html#LENS_INFO_AVAILABLE_FOCAL_LENGTHS
//            float[] focus_lengths = characteristics.get(
//                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
//            CharSequence[] focuses = new CharSequence[focus_lengths.length];
//            for (int i = 0; i < focus_lengths.length; i++) {
//                focuses[i] = focus_lengths[i] + "";
//            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private boolean checkIso(Object newValue) {
        if (!newValue.toString().equals("") && newValue.toString().matches("\\d*")) {
            return true;
        } else {
            Toast.makeText(getActivity(),
                    newValue + " is not a valid number!", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void switchPrefCameraValues(String main, String SecondaryKey) {
        ListPreference list = getPreferenceManager().findPreference(SecondaryKey);
        assert list != null;
        CharSequence[] entryValues = list.getEntryValues();
        int index = Arrays.asList(entryValues).indexOf(main);

        if (index + 1 < entryValues.length) {
            list.setValue((String) entryValues[index + 1]);
        } else {
            list.setValue((String) entryValues[index - 1]);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("prefCamera")) {
            try {
                String cameraId = sharedPreferences.getString("prefCamera", "0");
                String camera2Id = sharedPreferences.getString("prefCamera2", "1");

                if (cameraId.equals(camera2Id)) {
                    switchPrefCameraValues(cameraId, "prefCamera2");
                }

                Activity activity = getActivity();
                CameraManager manager = (CameraManager)
                        activity.getSystemService(Context.CAMERA_SERVICE);

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap streamConfigurationMap = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);

                int rezSize = sizes.length;
                CharSequence[] rez = new CharSequence[rezSize];
                CharSequence[] rezValues = new CharSequence[rezSize];
                int defaultIndex = 0;
                for (int i = 0; i < sizes.length; i++) {
                    rez[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    rezValues[i] = sizes[i].getWidth() + "x" + sizes[i].getHeight();
                    if (sizes[i].getWidth() + sizes[i].getHeight() ==
                            DesiredCameraSetting.mDesiredFrameWidth +
                                    DesiredCameraSetting.mDesiredFrameHeight) {
                        defaultIndex = i;
                    }
                }

                ListPreference cameraRez = getPreferenceManager().findPreference("prefSizeRaw");

                assert cameraRez != null;
                cameraRez.setEntries(rez);
                cameraRez.setEntryValues(rezValues);
                cameraRez.setValueIndex(defaultIndex);

//                float[] focus_lengths = characteristics.get(
//                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
//                CharSequence[] focuses = new CharSequence[focus_lengths.length];
//                for (int i = 0; i < focus_lengths.length; i++) {
//                    focuses[i] = focus_lengths[i] + "";
//                }
            } catch (CameraAccessException | NullPointerException e) {
                e.printStackTrace();
            }
        } else if (key.equals("prefCamera2")) {
            String cameraId = sharedPreferences.getString("prefCamera", "0");
            String camera2Id = sharedPreferences.getString("prefCamera2", "1");

            if (cameraId.equals(camera2Id)) {
                switchPrefCameraValues(camera2Id, "prefCamera");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (!(context instanceof OnFragmentInteractionListener)) {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public interface OnFragmentInteractionListener {
    }
}
