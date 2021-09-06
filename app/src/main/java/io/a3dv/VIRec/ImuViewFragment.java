package io.a3dv.VIRec;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.a3dv.VIRec.ImuViewContent.SingleAxis;


public class ImuViewFragment extends Fragment implements SensorEventListener {
    private static final String ARG_COLUMN_COUNT = "column-count";
    private int mColumnCount = 1;
    private OnListFragmentInteractionListener mListener;
    ImuRecyclerViewAdapter mAdapter;
    private SensorManager mSensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mMag;
    private static class SensorPacket {
        long timestamp;
        float[] values;

        SensorPacket(long time, float[] vals) {
            timestamp = time;
            values = vals;
        }
    }
    private HandlerThread mSensorThread;


    public ImuViewFragment() {
    }

    @SuppressWarnings("unused")
    public static ImuViewFragment newInstance(int columnCount) {
        ImuViewFragment fragment = new ImuViewFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLUMN_COUNT, columnCount);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mColumnCount = getArguments().getInt(ARG_COLUMN_COUNT);
        }

        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER); // warn: mAccel can be null.
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE); // warn: mGyro can be null.
        mMag = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.imu_list_fragment, container, false);
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;
            if (mColumnCount <= 1) {
                recyclerView.setLayoutManager(new LinearLayoutManager(context));
            } else {
                recyclerView.setLayoutManager(new GridLayoutManager(context, mColumnCount));
            }
            mAdapter = new ImuRecyclerViewAdapter(ImuViewContent.ITEMS, mListener);
            recyclerView.setAdapter(mAdapter);
        }
        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        registerImu();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterImu();
    }


    public interface OnListFragmentInteractionListener {
        void onListFragmentInteraction(SingleAxis item);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            SensorPacket sp = new SensorPacket(event.timestamp, event.values);
            for (int i = 0; i < 3; ++i) {
                mAdapter.updateListItem(i, sp.values[i]);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            SensorPacket sp = new SensorPacket(event.timestamp, event.values);
            for (int i = 0; i < 3; ++i) {
                mAdapter.updateListItem(i + 3, sp.values[i]);
            }
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            SensorPacket sp = new SensorPacket(event.timestamp, event.values);
            for (int i = 0; i < 3; ++i) {
                mAdapter.updateListItem(i + 6, sp.values[i]);
            }
        }

        getActivity().runOnUiThread(() -> mAdapter.notifyDataSetChanged());
    }

    /**
     * This will register all IMU listeners
     */
    public void registerImu() {
        mSensorThread = new HandlerThread("Sensor thread",
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mSensorThread.start();
        Handler sensorHandler = new Handler(mSensorThread.getLooper());

        int mSensorRate = SensorManager.SENSOR_DELAY_UI;

        mSensorManager.registerListener(this, mAccel, mSensorRate, sensorHandler);
        mSensorManager.registerListener(this, mGyro, mSensorRate, sensorHandler);
        mSensorManager.registerListener(this, mMag, mSensorRate, sensorHandler);
    }

    /**
     * This will unregister all IMU listeners
     */
    public void unregisterImu() {
        mSensorManager.unregisterListener(this, mAccel);
        mSensorManager.unregisterListener(this, mGyro);
        mSensorManager.unregisterListener(this, mMag);
        mSensorManager.unregisterListener(this);
        mSensorThread.quitSafely();
    }
}
