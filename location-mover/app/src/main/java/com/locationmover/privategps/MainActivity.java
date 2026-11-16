package com.locationmover.privategps;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Local-only control screen for the selected fixed Android mock location. */
public final class MainActivity extends Activity {
    private final int BACKGROUND = Color.rgb(15, 19, 27);
    private final int GREEN = Color.rgb(102, 242, 161);
    private final int MUTED = Color.rgb(183, 193, 204);
    private EditText latField, lonField, nameField;
    private TextView status;
    private Button toggle;
    private LinearLayout root, favorites;
    private boolean active = false;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateStatus(intent.getBooleanExtra(MockLocationService.EXTRA_RUNNING, false),
                intent.getStringExtra(MockLocationService.EXTRA_ERROR));
        }
    };
    private int px(int dp) { return (int)(dp * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }
    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setMinHeight(px(52));
        return button;
    }
    private EditText input(String placeholder, String initial, boolean numeric) {
        EditText field = new EditText(this);
        if (numeric) field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
            android.text.InputType.TYPE_NUMBER_FLAG_SIGNED |
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setSingleLine(true);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(MUTED);
        field.setTextSize(18);
        field.setHint(placeholder);
        field.setText(initial);
        return field;
    }
    private void space(int height) {
        root.addView(new View(this), new LinearLayout.LayoutParams(1, px(height)));
    }

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(22), px(24), px(22), px(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("Location Mover", 31, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);
        root.addView(text("One fixed place. No route or interval settings.", 14, MUTED));
        space(22);
        status = text("● Not active", 17, MUTED);
        root.addView(status);
        space(20);

        root.addView(text("Choose a place", 19, Color.WHITE));
        Spinner options = new Spinner(this);
        String[] places = {"Choose a preset…", "Auckland, New Zealand", "Wellington, New Zealand",
            "Christchurch, New Zealand", "Melbourne, Australia", "Sydney, Australia",
            "London, United Kingdom", "New York, USA", "Tokyo, Japan",
            "Paris, France", "Singapore"};
        double[][] coordinates = {{0,0}, {-36.8485,174.7633}, {-41.2865,174.7762},
            {-43.5321,172.6362}, {-37.8136,144.9631}, {-33.8688,151.2093},
            {51.5074,-0.1278}, {40.7128,-74.006}, {35.6762,139.6503},
            {48.8566,2.3522}, {1.3521,103.8198}};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, places);
        options.setAdapter(adapter);
        root.addView(options);
        nameField = input("Location name (optional)", "", false);
        latField = input("Latitude (-90 to 90)", "-36.8485", true);
        lonField = input("Longitude (-180 to 180)", "174.7633", true);
        root.addView(nameField); root.addView(latField); root.addView(lonField);
        options.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos > 0) {
                    latField.setText(String.valueOf(coordinates[pos][0]));
                    lonField.setText(String.valueOf(coordinates[pos][1]));
                    nameField.setText(places[pos]);
                }
            }
        });
        space(12);
        toggle = button("START LOCATION");
        toggle.setTextColor(Color.BLACK);
        toggle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        root.addView(toggle);
        toggle.setOnClickListener(v -> { if (active) stop(); else start(); });
        Button settings = button("Open mock location settings");
        root.addView(settings);
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
            catch (Exception ex) {
                new AlertDialog.Builder(this)
                    .setMessage("Open Settings → Developer options → Select mock location app → Location Mover.")
                    .setPositiveButton("OK", null).show();
            }
        });
        Button save = button("Save selected place");
        root.addView(save);
        save.setOnClickListener(v -> saveFavorite());
        space(16);
        root.addView(text("Saved places", 19, Color.WHITE));
        favorites = new LinearLayout(this);
        favorites.setOrientation(LinearLayout.VERTICAL);
        root.addView(favorites);
        showFavorites();
        space(22);
        root.addView(text("HOW TO USE", 16, GREEN));
        root.addView(text(
            "1. In Developer options, select Location Mover as your mock location app.\n" +
            "2. Give Location Mover precise location permission.\n" +
            "3. Choose a preset or enter coordinates anywhere on Earth.\n" +
            "4. Press START. Your fixed mock location remains active until stopped.\n" +
            "5. Press STOP or the Stop button in the notification to restore GPS.\n\n" +
            "This does not change your IP address. Some apps detect or reject mock locations.",
            14, MUTED));
        updateStatus(getSharedPreferences("state", MODE_PRIVATE)
            .getBoolean("running", false), "");
        registerReceiver(receiver, new IntentFilter(MockLocationService.ACTION_STATUS),
            Context.RECEIVER_NOT_EXPORTED);
    }

    private void updateStatus(boolean isActive, String error) {
        active = isActive;
        if (status == null) return;
        boolean failed = error != null && !error.isEmpty();
        status.setText(isActive ? "● Location active" : failed ? "● " + error : "● Not active");
        status.setTextColor(isActive ? GREEN : failed ? Color.rgb(255,167,135) : MUTED);
        toggle.setText(isActive ? "STOP LOCATION" : "START LOCATION");
    }
    private double[] coordinates() {
        try {
            double a = Double.parseDouble(latField.getText().toString().trim());
            double b = Double.parseDouble(lonField.getText().toString().trim());
            if (!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(a) > 90 || Math.abs(b) > 180)
                throw new IllegalArgumentException();
            return new double[]{a, b};
        } catch (Exception ignored) {
            Toast.makeText(this, "Enter valid latitude (-90 to 90) and longitude (-180 to 180)",
                Toast.LENGTH_LONG).show();
            return null;
        }
    }
    private void start() {
        double[] coords = coordinates();
        if (coords == null) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, 11);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
        }
        Intent intent = new Intent(this, MockLocationService.class)
            .setAction(MockLocationService.ACTION_START)
            .putExtra(MockLocationService.EXTRA_LAT, coords[0])
            .putExtra(MockLocationService.EXTRA_LON, coords[1]);
        try {
            startForegroundService(intent);
            status.setText("● Starting…");
        } catch (Exception ex) { updateStatus(false, ex.toString()); }
    }
    private void stop() {
        stopService(new Intent(this, MockLocationService.class));
        updateStatus(false, "");
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 11) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) start();
            else Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
        }
    }
    private void saveFavorite() {
        double[] coords = coordinates();
        if (coords == null) return;
        String name = nameField.getText().toString().trim().replace("|", " ");
        if (name.isEmpty()) name = String.format(Locale.US, "%.5f, %.5f", coords[0], coords[1]);
        String key = "place_" + System.currentTimeMillis();
        getSharedPreferences("favorites", MODE_PRIVATE).edit()
            .putString(key, name + "|" + coords[0] + "|" + coords[1]).apply();
        showFavorites();
        Toast.makeText(this, "Saved on this phone", Toast.LENGTH_SHORT).show();
    }
    private void showFavorites() {
        favorites.removeAllViews();
        android.content.SharedPreferences storage = getSharedPreferences("favorites", MODE_PRIVATE);
        Map<String, ?> entries = storage.getAll();
        if (entries.isEmpty()) {
            favorites.addView(text("No saved places.", 14, MUTED));
            return;
        }
        ArrayList<String> keys = new ArrayList<>(entries.keySet());
        Collections.sort(keys, Collections.reverseOrder());
        for (String key : keys) {
            String[] parts = storage.getString(key, "").split("\\|", 3);
            if (parts.length != 3) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button pick = button(parts[0]);
            pick.setTextSize(13);
            row.addView(pick, new LinearLayout.LayoutParams(0, px(56), 1));
            pick.setOnClickListener(v -> {
                nameField.setText(parts[0]);
                latField.setText(parts[1]);
                lonField.setText(parts[2]);
            });
            Button remove = button("×");
            remove.setContentDescription("Delete " + parts[0]);
            row.addView(remove, new LinearLayout.LayoutParams(px(55), px(56)));
            remove.setOnClickListener(v -> { storage.edit().remove(key).apply(); showFavorites(); });
            favorites.addView(row);
        }
    }
    @Override public void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
