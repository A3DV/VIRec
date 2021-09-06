package io.a3dv.VIRec;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import timber.log.Timber;

public class GPSManager implements LocationListener {
    public static String GpsHeader = "Timestamp[nanosecond],latitude[degrees],longitude[degrees],altitude[meters],speed[meters/second],Unix time[nanosecond]\n";

    private final Activity activity;

    private TextView mGpsStatusText;

    private static class LocationPacket {
        long timestamp; // nanoseconds
        long unixTime; // milliseconds
        double latitude;
        double longitude;
        double altitude;
        float speed;

        LocationPacket(long time, long unixTimeMillis, double lat, double lng, double alt, float spd) {
            timestamp = time;
            unixTime = unixTimeMillis;
            latitude = lat;
            longitude = lng;
            altitude = alt;
            speed = spd;
        }

        @Override
        public @NotNull String toString() {
            String delimiter = ",";
            return timestamp +
                    delimiter + latitude +
                    delimiter + longitude +
                    delimiter + altitude +
                    delimiter + speed +
                    delimiter + unixTime + "000000";
        }
    }

    private final LocationManager mGpsManager;

    private volatile boolean mRecordingLocationData = false;
    private BufferedWriter mDataWriter = null;

//    private final Deque<LocationPacket> mLocationData = new ArrayDeque<>();

    public GPSManager(Activity activity) {
        this.activity = activity;

        mGpsManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        mGpsStatusText = (TextView) activity.findViewById(R.id.gps_status);
        if (mGpsManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mGpsStatusText.setText(R.string.gpsLooking);
        } else {
            mGpsStatusText.setText(R.string.gpsStatusDisabled);
        }
    }

    @SuppressLint("MissingPermission")
    public void startRecording(String captureResultFile) {
        mGpsManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

        try {
            mDataWriter = new BufferedWriter(new FileWriter(captureResultFile, false));
            mDataWriter.write(GpsHeader);
            mRecordingLocationData = true;
        } catch (IOException err) {
            Timber.e(err, "IOException in opening location data writer at %s",
                    captureResultFile);
        }
    }

    public void stopRecording() {
        if (mRecordingLocationData) {
            mRecordingLocationData = false;
            try {
                mDataWriter.flush();
                mDataWriter.close();
            } catch (IOException err) {
                Timber.e(err, "IOException in closing location data writer");
            }
            mDataWriter = null;
        }
    }

    @Deprecated
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Timber.d("GPS status changed | provider: %s, status: %i", provider, status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Timber.d("GPS provider enabled | provider: %s", provider);

        mGpsStatusText = (TextView) activity.findViewById(R.id.gps_status);
        mGpsStatusText.setText(R.string.gpsLooking);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Timber.d("GPS provider disabled | provider: %s", provider);

        mGpsStatusText = (TextView) activity.findViewById(R.id.gps_status);
        mGpsStatusText.setText(R.string.gpsStatusDisabled);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public final void onLocationChanged(Location location) {
        long unixTime = System.currentTimeMillis();

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        float speed = location.getSpeed();

        LocationPacket lp = new LocationPacket(
                location.getElapsedRealtimeNanos(),
                unixTime,
                latitude,
                longitude,
                altitude,
                speed
        );

//        mLocationData.add(lp);

        if (mRecordingLocationData) {
            try {
                mDataWriter.write(lp.toString() + "\n");
            } catch (IOException ioe) {
                Timber.e(ioe);
            }
        }

        Date date = new Date(location.getTime());

        mGpsStatusText = (TextView) activity.findViewById(R.id.gps_status);
        mGpsStatusText.setText("Latitude: " + latitude + "\nLongitude: " + longitude
                + "\nTime: " + DateFormat.format("yyyy-MM-dd HH:mm:ss", date)
                + "\nAccuracy: " + location.getAccuracy() + "m");
    }

    @SuppressLint("MissingPermission")
    public void register() {
        mGpsManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    public void unregister() {
        mGpsManager.removeUpdates(this);
    }
}
