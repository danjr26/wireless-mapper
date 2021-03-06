package com.wiprof.wirelessprofiler;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telecom.Call;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class PinMap extends AppCompatActivity
        implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnInfoWindowCloseListener,
        GestureDetector.OnGestureListener {

    private static PinMap INSTANCE;

    private GoogleMap map;

    private PinListEntryAdapter pinListEntryAdapter;
    private ArrayList<Pin> pins;
    private int activePinIndex;

    private enum PinListSortMethod {
        STRENGTH_STRONG_FIRST,
        STRENGTH_STRONG_LAST,
        TIMESTAMP_RECENT_FIRST,
        TIMESTAMP_RECENT_LAST
    };

    private PinListSortMethod pinListSortMethod;

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
    public CellularRefresher cellularRefresher;
    public PinSnippetRefresher pinSnippetRefresher;

    private ArrayList<WifiAccessPoint> wifiAccessPoints;
    private ArrayList<CellularAccessPoint> cellularAccessPoints;

    private WifiFilterListEntryAdapter wifiFilterListEntryAdapter;
    private ArrayList<WifiAccessPoint> wifiFilterListLiveAccessPoints;
    private ArrayList<WifiAccessPoint> wifiFilterListDeadAccessPoints;
    private ArrayList<WifiAccessPoint> wifiFilterListAccessPoints;

    private CellularFilterListEntryAdapter cellularFilterListEntryAdapter;
    private ArrayList<CellularAccessPoint> cellularFilterListLiveAccessPoints;
    private ArrayList<CellularAccessPoint> cellularFilterListDeadAccessPoints;
    private ArrayList<CellularAccessPoint> cellularFilterListAccessPoints;

    private HashMap<View, View> tabToContentPairing;

    private boolean hasPinVisibilityChanged;

    private Marker cellTowerMarker;
    private Handler cellTowerMarkerShowHandler;
    private Runnable cellTowerMarkerShowRunnable;

    private int activeTabId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_map);

        INSTANCE = this;

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        openLoadingScreen();

        gestureDetector = new GestureDetector(this, this);

        setupLocationProvider();
        setupInfoBox();

        Intent intent = getIntent();

        pinSnippetRefresher = new PinSnippetRefresher((int)TimeUnit.MINUTES.toMillis(1));
        wifiAccessPoints = new ArrayList<>();
        cellularAccessPoints = new ArrayList<>();
        wifiRefresher = new WifiRefresher(5000);
        cellularRefresher = new CellularRefresher(this, 5000);
        pins = new ArrayList<>();
        setFilter((FilterMode)intent.getSerializableExtra("filterMode"), intent.getStringExtra("filter"));

        ListView filterWifiList = findViewById(R.id.FilterWifiList);
        wifiFilterListEntryAdapter = new WifiFilterListEntryAdapter(this, new ArrayList<WifiAccessPoint>());
        filterWifiList.setAdapter(wifiFilterListEntryAdapter);

        ListView filterCellularList = findViewById(R.id.FilterCellularList);
        cellularFilterListEntryAdapter = new CellularFilterListEntryAdapter(this, new ArrayList<CellularAccessPoint>());
        filterCellularList.setAdapter(cellularFilterListEntryAdapter);

        loadPins();

        ListView pinList = findViewById(R.id.PinList);
        pinListEntryAdapter = new PinListEntryAdapter(this, new ArrayList<Pin>());
        pinList.setAdapter(pinListEntryAdapter);

        setupWifiFilterAccessPoints();
        setupCellularFilterAccessPoints();

        activePinIndex = -1;

        tabToContentPairing = new HashMap<>();
        tabToContentPairing.put(findViewById(R.id.WifiTab), findViewById(R.id.FilterListWifiContent));
        tabToContentPairing.put(findViewById(R.id.CellularTab), findViewById(R.id.FilterListCellularContent));

        switch(filterMode) {
            case FILTER_WIFI:
                setActiveTab(findViewById(R.id.WifiTab));
                break;
            case FILTER_CELLULAR:
                setActiveTab(findViewById(R.id.CellularTab));
                break;
        }

        cellTowerMarker = null;
        cellTowerMarkerShowHandler = null;

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

        // have to wait until Display() is called b/c only then calculates color
        pinListEntryAdapter.addAll(pins);
        setPinListSortMethod(PinListSortMethod.STRENGTH_STRONG_FIRST);

        cellTowerMarkerShowHandler = new Handler();
        if(filterMode == FilterMode.FILTER_CELLULAR) {
            persistentShowCellTowerMarker();
        }

        Intent intent = getIntent();

        LatLng location = new LatLng(intent.getDoubleExtra("lastLatitude", 0.0), intent.getDoubleExtra("lastLongitude", 0.0));

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 18));

        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.MY_PERMISSIONS_FINE_LOCATION);
        }
        map.setMyLocationEnabled(true);

        closeLoadingScreen();
    }

    @Override
    public void onBackPressed() {
        openLoadingScreen();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                PinMap pinMap = PinMap.getInstance();
                Intent intent = new Intent(pinMap, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            }
        }, 2000);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        item.getTitle();
        switch(item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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
    public void onShowPress(MotionEvent motionEvent) {}

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) { return true; }

    @Override
    public void onLongPress(MotionEvent motionEvent) {}

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
                ConstraintLayout accessPointView = findViewById(R.id.WifiAccessPoint);
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
            if(pins.get(i).getMarker() != null && pins.get(i).getMarker().equals(marker)) {
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

    private void persistentShowCellTowerMarker() {
        cellTowerMarkerShowRunnable = new Runnable() {
            @Override
            public void run() {
                showCellTowerMarker();
                if (cellTowerMarker == null) {
                    cellTowerMarkerShowHandler.postDelayed(this, 1000);
                }
            }
        };
        cellTowerMarkerShowHandler.post(cellTowerMarkerShowRunnable);
    }

    private void cancelShowCellTowerMarker() {
        cellTowerMarkerShowHandler.removeCallbacks(cellTowerMarkerShowRunnable);
    }

    private void showCellTowerMarker() {
        CellularAccessPoint accessPoint = null;
        for (CellularAccessPoint thisAccessPoint : cellularAccessPoints) {
            if(thisAccessPoint.isRegistered()) {
                accessPoint = thisAccessPoint;
                break;
            }
        }

        if(accessPoint == null || !accessPoint.isLocationValid()) {
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeResource(MainActivity.getInstance().getResources(), R.drawable.cell_tower);

        cellTowerMarker = map.addMarker(
                new MarkerOptions()
                        .position(accessPoint.getLocation())
                        .icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(bitmap, 100, 100, false)))
        );
    }

    private void hideCellTowerMarker() {
        if(cellTowerMarker == null) {
            return;
        }

        cellTowerMarker.remove();
        cellTowerMarker = null;
    }

    private boolean isLoadingScreenOpen() {
        View loadingScreen = findViewById(R.id.LoadingScreen);
        return loadingScreen.getVisibility() == View.VISIBLE;
    }

    private void openLoadingScreen() {
        View loadingScreen = findViewById(R.id.LoadingScreen);
        loadingScreen.setVisibility(View.VISIBLE);
        loadingScreen.invalidate();
        loadingScreen.requestLayout();
        ((View)loadingScreen.getParent()).invalidate();
        ((View)loadingScreen.getParent()).requestLayout();
    }

    private void closeLoadingScreen() {
        View loadingScreen = findViewById(R.id.LoadingScreen);
        loadingScreen.setVisibility(View.GONE);
        loadingScreen.invalidate();
        loadingScreen.requestLayout();
        ((View)loadingScreen.getParent()).invalidate();
        ((View)loadingScreen.getParent()).requestLayout();
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

    private void openCancelNewPinButton() {
        View newPinCancelButton = findViewById(R.id.CancelNewPinButton);
        newPinCancelButton.setVisibility(View.VISIBLE);
    }

    private void closeCancelNewPinButton() {
        View newPinCancelButton = findViewById(R.id.CancelNewPinButton);
        newPinCancelButton.setVisibility(View.GONE);
    }

    private boolean isCancelNewPinButtonOpen() {
        View newPinCancelButton = findViewById(R.id.CancelNewPinButton);
        return newPinCancelButton.getVisibility() == View.VISIBLE;
    }

    private boolean isNewPinOverlayOpen() {
        return isPinImageOverlayOpen();
    }

    private void openNewPinOverlay() {
        openPinImageOverlay();
        openConfirmNewPinButton();
        openCancelNewPinButton();
    }

    private void closeNewPinOverlay() {
        closeConfirmNewPinButton();
        closePinImageOverlay();
        closeCancelNewPinButton();
    }

    private void openThreeDotsMenu() {
        View threeDotsMenu = findViewById(R.id.ThreeDotsMenu);
        threeDotsMenu.setVisibility(View.VISIBLE);
    }

    private void closeThreeDotsMenu() {
        View threeDotsMenu = findViewById(R.id.ThreeDotsMenu);
        threeDotsMenu.setVisibility(View.GONE);
    }

    private boolean isThreeDotsMenuOpen() {
        View threeDotsMenu = findViewById(R.id.ThreeDotsMenu);
        return threeDotsMenu.getVisibility() == View.VISIBLE;
    }

    private LatLng getPinOverlayLatLng() {
        return map.getCameraPosition().target;
    }

    public void onConfirmNewPinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        closeNewPinOverlay();

        Pin pin = new Pin(getPinOverlayLatLng(), wifiAccessPoints, cellularAccessPoints, wifiRefresher.lastRefreshTime);
        addPin(pin);

        Toast toast = Toast.makeText(this, "Pin added", Toast.LENGTH_SHORT);
        toast.show();
    }

    public void onCancelNewPinButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        closeNewPinOverlay();
    }

    public void onPinListTabClick(View tab) {
        tab.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        setActiveTab(tab);
    }

    public void setActiveTab(View tab) {
        activeTabId = tab.getId();
        LinearLayout tabParent = findViewById(R.id.FilterListTabBar);
        final int nTabs = tabParent.getChildCount();
        for(int i = 0; i < nTabs; i++) {
            tabParent.getChildAt(i).setBackgroundColor(getResources().getColor(R.color.colorTab));
            ((TextView)tabParent.getChildAt(i)).setTextColor(getResources().getColor(R.color.colorTabText));
        }
        tab.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
        ((TextView)tab).setTextColor(getResources().getColor(R.color.colorContent));

        View tabContent = tabToContentPairing.get(tab);

        tabContent.bringToFront();
        ((View)tabContent.getParent()).invalidate();
        ((View)tabContent.getParent()).requestLayout();
    }

    public int getActiveTabId() {
        return activeTabId;
    }

    public void onPinListEntryClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        Pin pin = (Pin) view.getTag(R.id.PinTag);

        if(!pin.isSelected()) {
            return;
        }

        Marker marker = pin.getMarker();

        closePinList();
        marker.showInfoWindow();
        onMarkerClick(marker);
        map.animateCamera(CameraUpdateFactory.newLatLng(pin.getLocation()));
    }

    public void onPinListEntryCheckBoxClick(View view) {
        Pin pin = (Pin) ((View) view.getParent()).getTag(R.id.PinTag);
        String toastText;
        if(pin.isSelected()) {
            pin.unselect();
            toastText = "Pin made invisible";
        } else {
            pin.select();
            toastText = "Pin made visible";
        }
        final Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_SHORT);
        toast.show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel();
            }
        }, 200);
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
        View pinMapBar = findViewById(R.id.PinMapBar);
        pinListView.setVisibility(View.VISIBLE);
        backgroundTinter.setVisibility(View.VISIBLE);
        pinMapBar.setVisibility(View.GONE);
    }

    private void closePinList() {
        View pinListView = findViewById(R.id.PinListContainer);
        ImageView backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        View pinMapBar = findViewById(R.id.PinMapBar);
        pinListView.setVisibility(View.GONE);
        backgroundTinter.setVisibility(View.GONE);
        pinMapBar.setVisibility(View.VISIBLE);
    }

    private boolean isPinListOpen() {
        View pinListView = findViewById(R.id.PinListContainer);
        return pinListView.getVisibility() == View.VISIBLE;
    }

    private void openFilterList() {
        updateWifiFilterListAccessPoints();
        wifiFilterListEntryAdapter.clear();
        wifiFilterListEntryAdapter.addAll(wifiFilterListAccessPoints);

        updateCellularFilterListAccessPoints();
        cellularFilterListEntryAdapter.clear();
        cellularFilterListEntryAdapter.addAll(cellularFilterListAccessPoints);

        closeInfoBox();
        closePinList();

        View filterListView = findViewById(R.id.FilterListContainer);
        View backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        View pinMapBar = findViewById(R.id.PinMapBar);
        filterListView.setVisibility(View.VISIBLE);
        backgroundTinter.setVisibility(View.VISIBLE);
        pinMapBar.setVisibility(View.GONE);
    }

    private void closeFilterList() {
        View filterListView = findViewById(R.id.FilterListContainer);
        ImageView backgroundTinter = findViewById(R.id.MenuBackgroundTinter);
        View pinMapBar = findViewById(R.id.PinMapBar);
        filterListView.setVisibility(View.GONE);
        backgroundTinter.setVisibility(View.GONE);
        pinMapBar.setVisibility(View.VISIBLE);
    }

    private boolean isFilterListOpen() {
        View filterListView = findViewById(R.id.FilterListContainer);
        int visibility = filterListView.getVisibility();
        return visibility == View.VISIBLE;
    }

    public void addPin(Pin pin) {
        pins.add(pin);
        pinListEntryAdapter.add(pin);
        setPinListSortMethod(pinListSortMethod);
        for(WifiAccessPoint accessPoint : pin.getWifiInfo()) {
            addWifiFilterPinAccessPoint(accessPoint);
        }
        pin.Display(map, filterMode, filter);

        savePins();
    }

    public void removePin(Pin pin) {
        pins.remove(pin);
        pinListEntryAdapter.remove(pin);
        setPinListSortMethod(pinListSortMethod);
        for(WifiAccessPoint accessPoint : pin.getWifiInfo()) {
            for(int i = 0; i < wifiFilterListDeadAccessPoints.size(); i++) {
                if(wifiFilterListDeadAccessPoints.get(i).getName().equals(accessPoint.getName())) {
                    removeWifiFilterPinAccessPoint(i);
                    break;
                }
            }
            for(int i = 0; i < cellularFilterListDeadAccessPoints.size(); i++) {
                if(cellularFilterListDeadAccessPoints.get(i).getName().equals(accessPoint.getName())) {
                    removeCellularFilterPinAccessPoint(i);
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

    public boolean addCellularFilterLiveAccessPoint(CellularAccessPoint accessPoint) {
        if(!accessPoint.isIDValid()) {
            return false;
        }
        for(int i = 0; i < cellularFilterListLiveAccessPoints.size(); i++) {
            if(cellularFilterListLiveAccessPoints.get(i).getStrengthDbm() < accessPoint.getStrengthDbm()) {
                cellularFilterListLiveAccessPoints.add(i, accessPoint);
                return true;
            }
        }

        cellularFilterListLiveAccessPoints.add(accessPoint);
        return true;
    }

    public void clearWifiFilterLiveAccessPoints() {
        wifiFilterListLiveAccessPoints.clear();
    }

    public void clearCellularFilterLiveAccessPoints() { cellularFilterListLiveAccessPoints.clear(); }

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

    public boolean addCellularFilterPinAccessPoint(CellularAccessPoint accessPoint) {
        if(!accessPoint.isIDValid()) {
            return false;
        }
        for(CellularAccessPoint listedAccessPoint : cellularFilterListDeadAccessPoints) {
            if((accessPoint.getName()).equals(listedAccessPoint.getName())) {
                return false;
            }
        }

        CellularAccessPoint accessPointCopy = new CellularAccessPoint(accessPoint);
        accessPointCopy.Drain();

        for(int i = 0; i < cellularFilterListDeadAccessPoints.size(); i++) {
            if(cellularFilterListDeadAccessPoints.get(i).getName().compareToIgnoreCase(accessPointCopy.getName()) > 0) {
                cellularFilterListDeadAccessPoints.add(i, accessPointCopy);
                return true;
            }
        }

        cellularFilterListDeadAccessPoints.add(accessPointCopy);
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

    public boolean removeCellularFilterPinAccessPoint(int index) {
        CellularAccessPoint accessPoint = cellularFilterListDeadAccessPoints.get(index);
        for(Pin pin : pins) {
            for(CellularAccessPoint pinAccessPoint : pin.getCellularInfo()) {
                if(accessPoint.getName().equals(pinAccessPoint.getName())) {
                    return false;
                }
            }
        }

        cellularFilterListDeadAccessPoints.remove(index);
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

    public void setupCellularFilterAccessPoints() {
        int size = 0;
        for(Pin pin : pins) {
            size += pin.getCellularInfo().size();
        }

        cellularFilterListLiveAccessPoints = new ArrayList<>(4);
        cellularFilterListDeadAccessPoints = new ArrayList<>(size);
        cellularFilterListAccessPoints = new ArrayList<>(size + 4);

        for(Pin pin : pins) {
            for(CellularAccessPoint accessPoint : pin.getCellularInfo()) {
                addCellularFilterPinAccessPoint(accessPoint);
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

    public void updateCellularFilterListAccessPoints() {
        cellularFilterListAccessPoints.clear();
        clearCellularFilterLiveAccessPoints();
        for(CellularAccessPoint accessPoint : cellularAccessPoints) {
            addCellularFilterLiveAccessPoint(accessPoint);
        }

        cellularFilterListAccessPoints.ensureCapacity(cellularFilterListDeadAccessPoints.size() + cellularFilterListLiveAccessPoints.size());
        cellularFilterListAccessPoints.addAll(cellularFilterListLiveAccessPoints);
        for(CellularAccessPoint deadAccessPoint : cellularFilterListDeadAccessPoints) {
            boolean isNew = true;
            for(CellularAccessPoint liveAccessPoint : cellularFilterListLiveAccessPoints) {
                if(deadAccessPoint.getName().equals(liveAccessPoint.getName())) {
                    isNew = false;
                    break;
                }
            }
            if(isNew) {
                cellularFilterListAccessPoints.add(deadAccessPoint);
            }
        }
    }

    public void onWifiFilterListEntryClick(View view) {
        if(!isFilterListOpen())
            return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        int index = (int)view.getTag();
        WifiAccessPoint accessPoint = wifiFilterListAccessPoints.get(index);

        setFilter(FilterMode.FILTER_WIFI, accessPoint.getName());

        cancelShowCellTowerMarker();

        for(Pin pin : pins) {
            if(pin != null && pin.isValid()) {
                pin.Hide();
                pin.Display(map, filterMode, filter);
            }
        }

        pinListEntryAdapter.clear();
        pinListEntryAdapter.addAll(pins);

        closeFilterList();
    }

    public void onCellularFilterListEntryClick(View view) {
        if(!isFilterListOpen())
            return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        int index = (int)view.getTag();
        CellularAccessPoint accessPoint = cellularFilterListAccessPoints.get(index);

        setFilter(FilterMode.FILTER_CELLULAR, accessPoint.getName());

        persistentShowCellTowerMarker();

        for(Pin pin : pins) {
            if(pin != null && pin.isValid()) {
                pin.Hide();
                pin.Display(map, filterMode, filter);
            }
        }

        pinListEntryAdapter.clear();
        pinListEntryAdapter.addAll(pins);

        closeFilterList();
    }

    public void onMenuBackgroundTinterClick(View view) {
        closePinList();
        closeFilterList();
        closeInfoBox();
    }

    private void setPinListSortMethod(PinListSortMethod sortMethod) {
        this.pinListSortMethod = sortMethod;

        TextView strengthHeader = findViewById(R.id.PinListStrengthHeader);
        TextView timestampHeader = findViewById(R.id.PinListTimestampHeader);

        switch (sortMethod) {
            case STRENGTH_STRONG_FIRST:
            case STRENGTH_STRONG_LAST: {
                final int multiplier;
                final String arrow;
                if (sortMethod == PinListSortMethod.STRENGTH_STRONG_FIRST) {
                    multiplier = 1;
                    arrow = " ▼";
                } else {
                    multiplier = -1;
                    arrow = " ▲";
                }
                strengthHeader.setText(getResources().getText(R.string.wifi_strength_label_text) + arrow);
                timestampHeader.setText(getResources().getText(R.string.pin_timestamp_label_text));
                pinListEntryAdapter.sort(new Comparator<Pin>() {
                    @Override
                    public int compare(Pin pin1, Pin pin2) {
                        int result = 0;
                        switch(filterMode) {
                            case FILTER_WIFI: {
                                WifiAccessPoint accessPoint1 = pin1.getWifiFilterResult(filter);
                                WifiAccessPoint accessPoint2 = pin2.getWifiFilterResult(filter);
                                if (accessPoint1 == null) {
                                    if (accessPoint2 == null) {
                                        result = 0;
                                    } else {
                                        result = 1;
                                    }
                                } else {
                                    if (accessPoint2 == null) {
                                        result = -1;
                                    } else {
                                        result = Integer.compare(accessPoint1.getStrengthDbm(), accessPoint2.getStrengthDbm()) * -1;
                                    }
                                }
                            }
                            break;
                            case FILTER_CELLULAR: {
                                CellularAccessPoint accessPoint1 = pin1.getCellularFilterResult(filter);
                                CellularAccessPoint accessPoint2 = pin2.getCellularFilterResult(filter);
                                if (accessPoint1 == null) {
                                    if (accessPoint2 == null) {
                                        result = 0;
                                    } else {
                                        result = 1;
                                    }
                                } else {
                                    if (accessPoint2 == null) {
                                        result = -1;
                                    } else {
                                        result = Integer.compare(accessPoint1.getStrengthDbm(), accessPoint2.getStrengthDbm()) * -1;
                                    }
                                }

                            }
                            break;
                        }
                        return result * multiplier;
                    }
                });
                break;
            }
            case TIMESTAMP_RECENT_FIRST:
            case TIMESTAMP_RECENT_LAST: {
                final int multiplier;
                String arrow;
                if (sortMethod == PinListSortMethod.TIMESTAMP_RECENT_FIRST) {
                    multiplier = -1;
                    arrow = " ▼";
                } else {
                    multiplier = 1;
                    arrow = " ▲";
                }
                strengthHeader.setText(getResources().getText(R.string.wifi_strength_label_text));
                timestampHeader.setText(getResources().getText(R.string.pin_timestamp_label_text) + arrow);
                pinListEntryAdapter.sort(new Comparator<Pin>() {
                    @Override
                    public int compare(Pin pin1, Pin pin2) {
                        return Long.compare(pin1.getTime(), pin2.getTime()) * multiplier;
                    }
                });
                break;
            }
        }
    }

    public void onPinListStrengthHeaderClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(pinListSortMethod == PinListSortMethod.STRENGTH_STRONG_FIRST) {
            setPinListSortMethod(PinListSortMethod.STRENGTH_STRONG_LAST);
        } else {
            setPinListSortMethod(PinListSortMethod.STRENGTH_STRONG_FIRST);
        }
    }

    public void onPinListTimestampHeaderClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(pinListSortMethod == PinListSortMethod.TIMESTAMP_RECENT_FIRST) {
            setPinListSortMethod(PinListSortMethod.TIMESTAMP_RECENT_LAST);
        } else {
            setPinListSortMethod(PinListSortMethod.TIMESTAMP_RECENT_FIRST);
        }
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

        Toast toast = Toast.makeText(this, "Pin deleted", Toast.LENGTH_SHORT);
        toast.show();
    }

    public void onPinListButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        openPinList();
        setPinListSortMethod(pinListSortMethod);
    }

    public void onFilterListButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        openFilterList();
    }

    public void onThreeDotsMenuButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(isThreeDotsMenuOpen()) {
            closeThreeDotsMenu();
        } else {
            openThreeDotsMenu();
        }
    }

    public void onDeleteVisibleButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        closeThreeDotsMenu();

        new AlertDialog.Builder(this)
                .setTitle("Confirmation")
                .setMessage("Do you really want to delete all visible pins?")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int index) {
                        int count = 0;
                        for(int i = 0; i < pins.size(); i++) {
                            if(pins.get(i).isSelected()) {
                                pinListEntryAdapter.remove(pins.get(i));
                                pins.get(i).Hide();
                                pins.remove(i);
                                count++;
                                i--;
                            }
                        }
                        savePins();

                        Toast toast = Toast.makeText(
                                PinMap.getInstance(),
                                (count == 1) ? "1 pin removed" : String.format(Locale.US, "%d pins removed", count),
                                Toast.LENGTH_SHORT
                        );
                        toast.show();
                    }
                })
                .setNegativeButton("NO", null)
                .show();
    }

    public void onDeleteInvisibleButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        closeThreeDotsMenu();
        closeThreeDotsMenu();

        new AlertDialog.Builder(this)
                .setTitle("Confirmation")
                .setMessage("Do you really want to delete all invisible pins?")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int index) {
                        int count = 0;
                        for(int i = 0; i < pins.size(); i++) {
                            if(!pins.get(i).isSelected()) {
                                pinListEntryAdapter.remove(pins.get(i));
                                pins.remove(i);
                                count++;
                                i--;
                            }
                        }
                        savePins();

                        Toast toast = Toast.makeText(
                                PinMap.getInstance(),
                                (count == 1) ? "1 pin removed" : String.format(Locale.US, "%d pins removed", count),
                                Toast.LENGTH_SHORT
                        );
                        toast.show();
                    }
                })
                .setNegativeButton("NO", null)
                .show();
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

    protected GoogleMap getMap() {
        return map;
    }

    protected void setFilter(FilterMode filterMode, String filter) {
        this.filterMode = filterMode;
        this.filter = filter;
        ((TextView)findViewById(R.id.TrackingLabel)).setText("Tracking " + filter);
    }

    protected String getFilter() {
        return this.filter;
    }

    protected FilterMode getFilterMode() {
        return this.filterMode;
    }

    public static PinMap getInstance() {
        return INSTANCE;
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

    private class CellularRefresher implements Runnable {
        private int delay;
        private Handler handler;
        private boolean isPaused;
        PinMap activity;

        private CellularRefresher(PinMap activity, int delay) {
            this.delay = delay;
            this.handler = new Handler();
            handler.post(this);
            this.isPaused = false;
            this.activity = activity;
        }

        @Override
        public void run() {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, MainActivity.MY_PERMISSIONS_READ_PHONE_STATE);
            }
            TelephonyManager cellularManager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
            List<CellInfo> cellularInfos = cellularManager.getAllCellInfo();

            activity.cellularAccessPoints.clear();
            for(CellInfo cellularInfo : cellularInfos) {
                activity.cellularAccessPoints.add(new CellularAccessPoint(activity, cellularInfo));
            }

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
