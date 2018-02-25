package com.wiprof.wirelessprofiler;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PinMap extends AppCompatActivity
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowCloseListener,
        GestureDetector.OnGestureListener {

    private GoogleMap map;
    private ArrayList<Pin> pins;
    private int activePinIndex;

    private FusedLocationProviderClient locationProvider;
    private Location lastLocation;
    private GestureDetector gestureDetector;

    public enum FilterMode {
        FILTER_WIFI,
        FILTER_CELLULAR,
        FILTER_BLUETOOTH
    }

    private FilterMode filterMode;
    private String filter;

    public WifiRefresher wifiRefresher;
    public PinSnippetRefresher pinSnippetRefresher;
    private ArrayList<WifiAccessPoint> wifiAccessPoints;

    private WifiFilterListEntryAdapter wifiFilterListEntryAdapter;
    private ArrayList<WifiAccessPoint> wifiFilterListLiveAccessPoints;
    private ArrayList<WifiAccessPoint> wifiFilterListDeadAccessPoints;
    private ArrayList<WifiAccessPoint> wifiFilterListAccessPoints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_map);

        gestureDetector = new GestureDetector(this, this);

        setupLocationProvider();
        setupInfoBox();

        Intent intent = getIntent();

        pinSnippetRefresher = new PinSnippetRefresher((int)TimeUnit.MINUTES.toMillis(1));
        wifiAccessPoints = new ArrayList<>();
        wifiRefresher = new WifiRefresher(5000);
        pins = new ArrayList<>();
        setFilterMode((FilterMode)intent.getSerializableExtra("filterMode"));
        setFilter(intent.getStringExtra("filter"));

        ListView filterWifiList = findViewById(R.id.FilterWifiList);
        wifiFilterListEntryAdapter = new WifiFilterListEntryAdapter(this, new ArrayList<WifiAccessPoint>());
        filterWifiList.setAdapter(wifiFilterListEntryAdapter);

        loadPins();

        setupWifiFilterAccessPoints();

        activePinIndex = -1;

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        super.dispatchTouchEvent(motionEvent );
        return gestureDetector.onTouchEvent(motionEvent);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setCompassEnabled(false);
        map.getUiSettings().setIndoorLevelPickerEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.setOnMarkerClickListener(this);
        map.setOnInfoWindowCloseListener(this);

        for(Pin pin : pins) {
            if(pin != null && pin.isValid()) {
                pin.Display(map, filterMode, filter);
            }
        }

        Intent intent = getIntent();

        LatLng location = new LatLng(intent.getDoubleExtra("lastLatitude", 0.0), intent.getDoubleExtra("lastLongitude", 0.0));

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 18));

        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.MY_PERMISSIONS_FINE_LOCATION);
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

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float xVelocity, float yVelocity) {
        if(isPinListOpen() && xVelocity < -300.0f && Math.abs(yVelocity / xVelocity) < 0.5f) {
            closePinList();
            return true;
        }
        if(isFilterListOpen() && xVelocity > 300.0f && Math.abs(yVelocity / xVelocity) < 0.5f) {
            closeFilterList();
            return true;
        }
        return true;
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        findViewById(R.id.map).performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        activePinIndex = getPinIndexFromMarker(marker);
        ((ImageView)findViewById(R.id.RemovePinButton)).setColorFilter(getResources().getColor(R.color.colorContent));

        Pin pin = pins.get(activePinIndex);
        switch (filterMode) {
            case FILTER_WIFI:
                ConstraintLayout accessPointView = findViewById(R.id.AccessPoint);
                ConstraintLayout latitudeAttributeView = findViewById(R.id.LatitudeAttribute);
                ConstraintLayout longitudeAttributeView = findViewById(R.id.LongitudeAttribute);
                ConstraintLayout timestampAttributeView = findViewById(R.id.TimestampAttribute);

                WifiAccessPoint accessPoint = pin.getWifiFilterResult(filter);
                AttributeEntry latitudeAttribute = new AttributeEntry(
                    "Latitude", Double.toString(pin.getLocation().latitude), ""
                );
                AttributeEntry longitudeAttribute = new AttributeEntry(
                        "Longitude", Double.toString(pin.getLocation().longitude), ""
                );
                AttributeEntry timestampAttribute = new AttributeEntry(
                        "Snapshot taken", pin.getReadableTimestamp(), "");

                if(accessPoint != null) {
                    accessPoint.fillView(accessPointView);
                    latitudeAttribute.fillView(latitudeAttributeView);
                    longitudeAttribute.fillView(longitudeAttributeView);
                    timestampAttribute.fillView(timestampAttributeView);
                    openInfoBox(pin);
                }
        }
        return false;
    }

    @Override
    public void onInfoWindowClose(Marker marker) {
        ((ImageView)findViewById(R.id.RemovePinButton)).setColorFilter(getResources().getColor(R.color.colorContentDisabled));
        closeInfoBox();
        activePinIndex = -1;
    }

    public int getPinIndexFromMarker(Marker marker) {
        for(int i = 0; i < pins.size(); i++) {
            if(pins.get(i).getMarker().equals(marker)) {
                return i;
            }
        }

        return -1;
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

        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.MY_PERMISSIONS_FINE_LOCATION);
        }

        locationProvider.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private boolean isPinImageOverlayOpen() {
        View pinImageOverlay = findViewById(R.id.PinOverlay);
        return pinImageOverlay.getVisibility() == View.VISIBLE;
    }

    private void openPinImageOverlay() {
        View pinImageOverlay = findViewById(R.id.PinOverlay);
        pinImageOverlay.setVisibility(View.VISIBLE);
    }

    private void closePinImageOverlay() {
        View pinImageOverlay = findViewById(R.id.PinOverlay);
        pinImageOverlay.setVisibility(View.GONE);
    }

    private boolean isConfirmNewPinButtonOpen() {
        View newPinConfirmButton = findViewById(R.id.ConfirmNewPinButton);
        return newPinConfirmButton.getVisibility() == View.VISIBLE;
    }

    private void openConfirmNewPinButton() {
        View newPinConfirmButton = findViewById(R.id.ConfirmNewPinButton);
        newPinConfirmButton.setVisibility(View.VISIBLE);
    }

    private void closeConfirmNewPinButton() {
        View newPinConfirmButton = findViewById(R.id.ConfirmNewPinButton);
        newPinConfirmButton.setVisibility(View.GONE);
    }

    private boolean isNewPinOverlayOpen() {
        if(isPinImageOverlayOpen()) {
            if(!isConfirmNewPinButtonOpen()) {
                openConfirmNewPinButton();
            }
            return true;
        } else {
            if(isConfirmNewPinButtonOpen()) {
                openPinImageOverlay();
                return true;
            } else {
                return false;
            }
        }
    }

    private void openNewPinOverlay() {
        openPinImageOverlay();
        openConfirmNewPinButton();
    }

    private void closeNewPinOverlay() {
        closeConfirmNewPinButton();
        closePinImageOverlay();
    }

    private LatLng getPinOverlayLatLng() {
        return map.getCameraPosition().target;
    }

    public void onConfirmNewPinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        closeNewPinOverlay();

        Pin pin = new Pin(getPinOverlayLatLng(), wifiAccessPoints, wifiRefresher.lastRefreshTime);
        addPin(pin);

        Toast toast = Toast.makeText(this, "Pin added", Toast.LENGTH_SHORT);
        toast.show();
    }

    private void setupInfoBox() {
        ConstraintLayout rootView = findViewById(R.id.PinMapInfoBox);

        View accessPointView = getLayoutInflater().inflate(R.layout.item_wifi_access_point, rootView, false);
        View latitudeView = getLayoutInflater().inflate(R.layout.item_attribute_entry, rootView, false);
        View longitudeView = getLayoutInflater().inflate(R.layout.item_attribute_entry, rootView, false);
        View timestampView = getLayoutInflater().inflate(R.layout.item_attribute_entry, rootView, false);

        latitudeView.setId(R.id.LatitudeAttribute);
        longitudeView.setId(R.id.LongitudeAttribute);
        timestampView.setId(R.id.TimestampAttribute);

        accessPointView.setOnClickListener(null);
        latitudeView.setOnClickListener(null);
        longitudeView.setOnClickListener(null);
        timestampView.setOnClickListener(null);

        rootView.addView(accessPointView);
        rootView.addView(timestampView);
        rootView.addView(latitudeView);
        rootView.addView(longitudeView);

        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(rootView);
        constraintSet.connect(accessPointView.getId(), ConstraintSet.TOP, rootView.getId(), ConstraintSet.TOP, 0);
        constraintSet.connect(latitudeView.getId(), ConstraintSet.TOP, accessPointView.getId(), ConstraintSet.BOTTOM, 0);
        constraintSet.connect(longitudeView.getId(), ConstraintSet.TOP, latitudeView.getId(), ConstraintSet.BOTTOM, 0);
        constraintSet.connect(timestampView.getId(), ConstraintSet.TOP, longitudeView.getId(), ConstraintSet.BOTTOM, 0);
        constraintSet.applyTo(rootView);

        rootView.invalidate();
        rootView.requestLayout();
    }

    private void openInfoBox(Pin pin) {
        closeFilterList();
        closePinList();
        View infoBox = findViewById(R.id.PinMapInfoBox);
        infoBox.setVisibility(View.VISIBLE);
    }

    private void closeInfoBox() {
        View infoBox = findViewById(R.id.PinMapInfoBox);
        infoBox.setVisibility(View.GONE);
    }

    private boolean isInfoBoxOpen() {
        View infoBox = findViewById(R.id.PinMapInfoBox);
        return infoBox.getVisibility() == View.VISIBLE;
    }

    private void openPinList() {
        closeInfoBox();
        closeFilterList();
        View pinListView = findViewById(R.id.PinListContainer);
        View backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        pinListView.setVisibility(View.VISIBLE);
        backgroundTinter.setVisibility(View.VISIBLE);
    }

    private void closePinList() {
        View pinListView = findViewById(R.id.PinListContainer);
        ImageView backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        pinListView.setVisibility(View.GONE);
        backgroundTinter.setVisibility(View.GONE);
    }

    private boolean isPinListOpen() {
        View pinListView = findViewById(R.id.PinListContainer);
        return pinListView.getVisibility() == View.VISIBLE;
    }

    private void openFilterList() {
        updateWifiFilterListAccessPoints();
        wifiFilterListEntryAdapter.clear();
        wifiFilterListEntryAdapter.addAll(wifiFilterListAccessPoints);

        closeInfoBox();
        closePinList();

        View filterListView = findViewById(R.id.FilterListContainer);
        View backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        filterListView.setVisibility(View.VISIBLE);
        backgroundTinter.setVisibility(View.VISIBLE);
    }

    private void closeFilterList() {
        View filterListView = findViewById(R.id.FilterListContainer);
        ImageView backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        filterListView.setVisibility(View.GONE);
        backgroundTinter.setVisibility(View.GONE);
    }

    private boolean isFilterListOpen() {
        View filterListView = findViewById(R.id.FilterListContainer);
        int visibility = filterListView.getVisibility();
        return visibility == View.VISIBLE;
    }

    public void addPin(Pin pin) {
        pins.add(pin);
        for(WifiAccessPoint accessPoint : pin.getWifiInfo()) {
            addWifiFilterPinAccessPoint(accessPoint);
        }
        pin.Display(map, filterMode, filter);

        savePins();
    }

    public void removePin(Pin pin) {
        pins.remove(pin);
        for(WifiAccessPoint accessPoint : pin.getWifiInfo()) {
            for(int i = 0; i < wifiFilterListDeadAccessPoints.size(); i++) {
                if(wifiFilterListDeadAccessPoints.get(i).getName() == accessPoint.getName()) {
                    removeWifiFilterPinAccessPoint(i);
                    break;
                }
            }
        }
        pin.Hide();

        savePins();
    }

    public boolean addWifiFilterLiveAccessPoint(WifiAccessPoint accessPoint) {
        for(int i = 0; i < wifiFilterListLiveAccessPoints.size(); i++) {
            if(wifiFilterListLiveAccessPoints.get(i).getStrengthDbm() < accessPoint.getStrengthDbm()) {
                wifiFilterListLiveAccessPoints.add(i, accessPoint);
                return true;
            }
        }

        wifiFilterListLiveAccessPoints.add(accessPoint);
        return true;
    }

    public boolean removeWifiFilterLiveAccessPoint(int index) {
        wifiFilterListLiveAccessPoints.remove(index);
        return true;
    }

    public void clearWifiFilterLiveAccessPoints() {
        wifiFilterListLiveAccessPoints.clear();
    }

    public boolean addWifiFilterPinAccessPoint(WifiAccessPoint accessPoint) {
        for(WifiAccessPoint listedAccessPoint : wifiFilterListDeadAccessPoints) {
            if((accessPoint.getName()).equals(listedAccessPoint.getName())) {
                return false;
            }
        }

        WifiAccessPoint accessPointCopy = new WifiAccessPoint(accessPoint);
        accessPointCopy.Drain();

        for(int i = 0; i < wifiFilterListDeadAccessPoints.size(); i++) {
            if(wifiFilterListDeadAccessPoints.get(i).getName().compareToIgnoreCase(accessPointCopy.getName()) > 0) {
                wifiFilterListDeadAccessPoints.add(i, accessPointCopy);
                return true;
            }
        }

        wifiFilterListDeadAccessPoints.add(accessPointCopy);
        return true;
    }

    public boolean removeWifiFilterPinAccessPoint(int index) {
        WifiAccessPoint accessPoint = wifiFilterListDeadAccessPoints.get(index);
        for(Pin pin : pins) {
            for(WifiAccessPoint pinAccessPoint : pin.getWifiInfo()) {
                if(accessPoint.getName().equals(pinAccessPoint.getName())) {
                    return false;
                }
            }
        }

        wifiFilterListDeadAccessPoints.remove(index);
        return true;
    }

    public void clearWifiFilterDeadAccessPoints() {
        wifiFilterListDeadAccessPoints.clear();
    }

    public void setupWifiFilterAccessPoints() {
        int size = 0;
        for(Pin pin : pins) {
            size += pin.getWifiInfo().size();
        }

        wifiFilterListLiveAccessPoints = new ArrayList<>(20);
        wifiFilterListDeadAccessPoints = new ArrayList<>(size);
        wifiFilterListAccessPoints = new ArrayList<>(size + 20);

        for(Pin pin : pins) {
            for(WifiAccessPoint accessPoint : pin.getWifiInfo()) {
                addWifiFilterPinAccessPoint(accessPoint);
            }
        }
    }

    public void updateWifiFilterListAccessPoints() {
        wifiFilterListAccessPoints.clear();
        clearWifiFilterLiveAccessPoints();
        for(WifiAccessPoint accessPoint : wifiAccessPoints) {
            addWifiFilterLiveAccessPoint(accessPoint);
        }

        wifiFilterListAccessPoints.ensureCapacity(wifiFilterListDeadAccessPoints.size() + wifiFilterListLiveAccessPoints.size());
        wifiFilterListAccessPoints.addAll(wifiFilterListLiveAccessPoints);
        for(WifiAccessPoint deadAccessPoint : wifiFilterListDeadAccessPoints) {
            boolean isNew = true;
            for(WifiAccessPoint liveAccessPoint : wifiFilterListLiveAccessPoints) {
                if(deadAccessPoint.getName().equals(liveAccessPoint.getName())) {
                    isNew = false;
                    break;
                }
            }
            if(isNew) {
                wifiFilterListAccessPoints.add(deadAccessPoint);
            }
        }
    }

    public void onWifiFilterListEntryClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        int index = (int)view.getTag();
        WifiAccessPoint accessPoint = wifiFilterListAccessPoints.get(index);

        setFilterMode(FilterMode.FILTER_WIFI);
        setFilter(accessPoint.getName());

        for(Pin pin : pins) {
            if(pin != null && pin.isValid()) {
                pin.Hide();
                pin.Display(map, filterMode, filter);
            }
        }

        closeFilterList();
    }

    public void onNewPinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(lastLocation == null || wifiRefresher.lastRefreshTime == 0L) {
            return;
        }

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 20.0f));

        openNewPinOverlay();
    }

    public void onRemovePinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(activePinIndex < 0) {
            return;
        }
        removePin(pins.get(activePinIndex));
        activePinIndex = -1;

        Toast toast = Toast.makeText(this, "Pin removed", Toast.LENGTH_SHORT);
        toast.show();
    }

    public void onPinListButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        openPinList();
    }

    public void onFilterListButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        openFilterList();
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

    private class PinSnippetRefresher implements Runnable {
        public int delay;
        private Handler handler;

        PinSnippetRefresher(int delay) {
            this.delay = delay;
            handler = new Handler();
            handler.post(this);
        }

        @Override
        public void run() {
            for(Pin pin : pins) {
                pin.updateSnippet();
            }
            handler.postDelayed(this, delay);
        }
    }
}
