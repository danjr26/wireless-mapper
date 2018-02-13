package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PinMap extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private ArrayList<Pin> pins;

    private FusedLocationProviderClient locationProvider;
    private Location lastLocation;

    public enum FilterMode {
        FILTER_WIFI,
        FILTER_BLUETOOTH
    }

    private FilterMode filterMode;
    private String filter;

    public WifiRefresher wifiRefresher;
    private ArrayList<WifiAccessPoint> wifiAccessPoints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_map);

        setupLocationProvider();

        wifiAccessPoints = new ArrayList<>();
        wifiRefresher = new WifiRefresher(5000);
        pins = new ArrayList<>();
        setFilterMode((FilterMode)getIntent().getSerializableExtra("filterMode"));
        setFilter(getIntent().getStringExtra("filter"));

        //clearSavedPins();
        loadPins();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setIndoorLevelPickerEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);

        for(Pin pin : pins) {
            if(pin != null && pin.isValid()) {
                pin.Display(map, filterMode, filter);
            } else {
                pin.isValid();
            }
        }
        if(pins.size() > 0) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(pins.get(0).getLocation(), 18));
        }
        map.setMyLocationEnabled(true);
    }

    @Override
    protected void onDestroy() {
        savePins();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        savePins();
        super.onPause();
    }

    @Override
    protected void onStop() {
        savePins();
        super.onStop();
    }

    private void setupLocationProvider() {
        locationProvider = LocationServices.getFusedLocationProviderClient(this);
        lastLocation = null;

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location location = result.getLastLocation();
                lastLocation = location;
            }
        };

        locationProvider.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    public void onNewPinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(lastLocation == null || wifiRefresher.lastRefreshTime == 0L) {
            return;
        }
        Pin pin = new Pin(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()),
                wifiAccessPoints, wifiRefresher.lastRefreshTime);
        pins.add(pin);
        pin.Display(map, filterMode, filter);
    }

    public void onRemovePinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public void onPinListButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public void onFilterListButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    protected void savePins() {
        Pin.putAllToFile(pins, getApplicationContext());
    }

    protected void loadPins() {
        pins.addAll(Pin.getAllFromIntent(getIntent()));
        pins.addAll(Pin.getAllFromFile(getApplicationContext()));
    }

    protected void clearLoadedPins() {
        pins.clear();
    }

    protected void clearSavedPins() {
        Pin.clearAllFromFile(getApplicationContext());
    }

    protected void clearAllPins() {
        clearLoadedPins();
        clearSavedPins();
    }

    protected void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }

    protected void setFilter(String filter) {
        this.filter = filter;
        ((TextView)findViewById(R.id.TrackingLabel)).setText("Tracking " + filter);
    }

    private class WifiRefresher implements Runnable {
        private int delay;
        private List<ScanResult> lastScanResults;
        private Handler handler;
        private long lastRefreshTime;

        public WifiRefresher(int in_delay) {
            delay = in_delay;
            handler = new Handler();
            handler.post(this);
            lastRefreshTime = 0L;
        }

        @Override
        public void run() {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            lastScanResults = wifiManager.getScanResults();
            lastRefreshTime = System.currentTimeMillis();

            wifiAccessPoints.clear();
            for(ScanResult result : lastScanResults) {
                wifiAccessPoints.add(new WifiAccessPoint(result));
            }

            wifiManager.startScan();
            handler.postDelayed(this, delay);
        }
    }
}
