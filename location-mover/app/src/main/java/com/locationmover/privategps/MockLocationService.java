package com.locationmover.privategps;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.*;
import java.util.*;

/** Keeps one fixed, explicitly mock-labelled location until stopped by the user. */
public final class MockLocationService extends Service {
    static final String ACTION_START = "com.locationmover.privategps.START";
    static final String ACTION_STOP = "com.locationmover.privategps.STOP";
    static final String ACTION_STATUS = "com.locationmover.privategps.STATUS";
    static final String EXTRA_LAT = "latitude", EXTRA_LON = "longitude",
                        EXTRA_ERROR = "error", EXTRA_RUNNING = "running";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> installed = new ArrayList<>();
    private LocationManager manager;
    private double latitude, longitude;
    private boolean running = false, errorSent = false;

    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { inject(); handler.postDelayed(this, 1500); }
            catch (Exception ex) { fail(ex); }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        manager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        double newLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN);
        double newLon = intent.getDoubleExtra(EXTRA_LON, Double.NaN);
        if (!Double.isFinite(newLat) || !Double.isFinite(newLon) ||
            Math.abs(newLat) > 90 || Math.abs(newLon) > 180) {
            publish(false, "Invalid coordinates");
            errorSent = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        latitude = newLat; longitude = newLon;
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                "location_mover_active", "Mock location running", NotificationManager.IMPORTANCE_LOW));
            Intent stop = new Intent(this, MockLocationService.class).setAction(ACTION_STOP);
            PendingIntent stopIntent = PendingIntent.getService(
                this, 100, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = new Notification.Builder(this, "location_mover_active")
                .setContentTitle("Location Mover is running")
                .setContentText(String.format(Locale.US, "%.5f, %.5f", latitude, longitude))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stopIntent).build())
                .build();
            startForeground(4001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            running = false;
            errorSent = false;
            handler.removeCallbacks(update);
            clearProviders();
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    manager.addTestProvider(provider, new ProviderProperties.Builder()
                        .setAccuracy(provider.equals(LocationManager.GPS_PROVIDER) ?
                            ProviderProperties.ACCURACY_FINE : ProviderProperties.ACCURACY_COARSE)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .build());
                    manager.setTestProviderEnabled(provider, true);
                    installed.add(provider);
                } catch (SecurityException ex) {
                    throw ex;
                } catch (IllegalArgumentException ex) {
                    if (provider.equals(LocationManager.GPS_PROVIDER)) throw ex;
                }
            }
            if (!installed.contains(LocationManager.GPS_PROVIDER)) {
                throw new IllegalStateException("GPS provider unavailable");
            }
            running = true;
            inject();
            handler.postDelayed(update, 1500);
            publish(true, "");
        } catch (Exception ex) {
            fail(ex);
        }
        return START_NOT_STICKY;
    }

    private void inject() {
        for (String provider : installed) {
            Location location = new Location(provider);
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setAccuracy(3f);
            location.setAltitude(0);
            location.setSpeed(0f);
            location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            manager.setTestProviderLocation(provider, location);
        }
    }
    private void fail(Exception ex) {
        running = false;
        errorSent = true;
        String message = ex instanceof SecurityException
            ? "Open Developer options, choose Location Mover under Select mock location app, then retry."
            : ex.getClass().getSimpleName() + ": " + ex.getMessage();
        publish(false, message);
        stopSelf();
    }
    private void publish(boolean active, String error) {
        getSharedPreferences("state", MODE_PRIVATE).edit().putBoolean("running", active).apply();
        Intent intent = new Intent(ACTION_STATUS).setPackage(getPackageName());
        intent.putExtra(EXTRA_RUNNING, active).putExtra(EXTRA_ERROR, error);
        sendBroadcast(intent);
    }
    private void clearProviders() {
        for (String provider : installed) {
            try { manager.removeTestProvider(provider); } catch (Exception ignored) {}
        }
        installed.clear();
    }
    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(update);
        clearProviders();
        if (!errorSent) publish(false, "");
        super.onDestroy();
    }
}
