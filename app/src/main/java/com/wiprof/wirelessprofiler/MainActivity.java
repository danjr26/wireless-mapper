package com.wiprof.wirelessprofiler;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.transition.Explode;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;

public class MainActivity extends AppCompatActivity {
    public static final int MY_PERMISSIONS_FINE_LOCATION = 0;
    private static MainActivity INSTANCE;

    private FusedLocationProviderClient locationProvider;
    private Location lastLocation;

    private SharedPreferences wifiByteToNamePairing;
    private SharedPreferences wifiSSIDToIndexPairing;

    private WifiRefresher wifiRefresher;
    private HashMap<TextView, View> tabToContentPairing;

    private int activeTabId;
    private int activeTabContentId;

    private ArrayList<WifiAccessPoint> accessPoints;
    private WifiAccessPointAdapter accessPointAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openLoadingScreen();

        INSTANCE = this;

        setTitle(Html.fromHtml(getResources().getString(R.string.app_name)));

        locationProvider = LocationServices.getFusedLocationProviderClient(this);
        lastLocation = null;

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location location = result.getLastLocation();
                String text = Double.toString(location.getLatitude()) + " lat\n" +
                        Double.toString(location.getLongitude()) + " long\nwithin " +
                        Float.toString(location.getAccuracy()) + " m";
                ((TextView)findViewById(R.id.textView)).setText(text);

                lastLocation = location;
            }
        };

        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.MY_PERMISSIONS_FINE_LOCATION);
        }

        locationProvider.requestLocationUpdates(locationRequest, locationCallback, null);

        setActiveTab(findViewById(R.id.WifiTab));
        setActiveTabContent(findViewById(R.id.WifiTabContent));

        getWindow().setExitTransition(new Explode());

        wifiByteToNamePairing = getSharedPreferences(getString(R.string.wifi_byte_to_ssid_pref_key), MODE_PRIVATE);
        wifiSSIDToIndexPairing = getSharedPreferences(getString(R.string.wifi_ssid_to_index_pref_key), MODE_PRIVATE);

        /*
        SharedPreferences.Editor byteToNameEditor = wifiByteToNamePairing.edit();
        SharedPreferences.Editor ssidToIndexEditor = wifiSSIDToIndexPairing.edit();
        byteToNameEditor.clear();
        ssidToIndexEditor.clear();
        byteToNameEditor.commit();
        ssidToIndexEditor.commit();
        */

        accessPoints = new ArrayList<>();
        accessPointAdapter = new WifiAccessPointAdapter(this, accessPoints);
        ListView accessPointListView = findViewById(R.id.WifiList);
        accessPointListView.setAdapter(accessPointAdapter);

        wifiRefresher = new WifiRefresher(accessPointAdapter, 5000);

        tabToContentPairing = new HashMap<>();
        tabToContentPairing.put((TextView) findViewById(R.id.WifiTab), findViewById(R.id.WifiTabContent));
        tabToContentPairing.put((TextView) findViewById(R.id.BluetoothTab), findViewById(R.id.BluetoothTabContent));

        getLayoutInflater().inflate(R.layout.item_wifi_access_point, (ViewGroup)findViewById(R.id.BluetoothTabContent), true);

        closeLoadingScreen();
    }

    @Override
    protected void onSaveInstanceState(Bundle outBundle) {
        super.onSaveInstanceState(outBundle);
    }

    public void onTabClick(View tab) {
        tab.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        View tabContent = tabToContentPairing.get(tab);
        setActiveTab(tab);
        setActiveTabContent(tabContent);
    }

    public void setActiveTab(View tab) {
        activeTabId = tab.getId();
        LinearLayout tabParent = findViewById(R.id.TabLayout);
        final int nTabs = tabParent.getChildCount();
        for(int i = 0; i < nTabs; i++) {
            tabParent.getChildAt(i).setBackgroundColor(getResources().getColor(R.color.colorTab));
            ((TextView)tabParent.getChildAt(i)).setTextColor(getResources().getColor(R.color.colorTabText));
        }
        tab.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
        ((TextView)tab).setTextColor(getResources().getColor(R.color.colorContent));
    }

    public int getActiveTabId() {
        return activeTabId;
    }

    public void setActiveTabContent(View tabContent) {
        activeTabContentId = tabContent.getId();
        tabContent.bringToFront();
        ((View)tabContent.getParent()).invalidate();
        ((View)tabContent.getParent()).requestLayout();
    }

    public int getActiveTabContentId() {
        return activeTabContentId;
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

    public void onAccessPointClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if(view.getLayoutParams().height == getResources().getDimensionPixelSize(R.dimen.wifiAccessPointExpandedHeight)) {
            view.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.wifiAccessPointHeight);
            view.setBackgroundColor(getResources().getColor(R.color.colorContent));
            view.findViewById(R.id.TrackButton).setVisibility(View.GONE);
            view.findViewById(R.id.InfoButton).setVisibility(View.GONE);
            wifiRefresher.Resume();
        } else {
            for(int i = 0; i < ((ViewGroup)view.getParent()).getChildCount(); i++) {
                View child = ((ViewGroup)view.getParent()).getChildAt(i);
                if(child.getLayoutParams().height == getResources().getDimensionPixelSize(R.dimen.wifiAccessPointExpandedHeight)) {
                    child.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.wifiAccessPointHeight);
                    child.findViewById(R.id.TrackButton).setVisibility(View.GONE);
                    child.findViewById(R.id.InfoButton).setVisibility(View.GONE);
                    child.invalidate();
                    child.requestLayout();
                }
            }
            view.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.wifiAccessPointExpandedHeight);
            view.setBackgroundColor(getResources().getColor(R.color.colorContentSelected));
            view.findViewById(R.id.TrackButton).setVisibility(View.VISIBLE);
            view.findViewById(R.id.InfoButton).setVisibility(View.VISIBLE);
            wifiRefresher.Pause();
        }
        view.invalidate();
        view.requestLayout();
    }

    public void onInfoButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        int index = (int)((View)view.getParent()).getTag();
        WifiAccessPoint accessPoint = wifiRefresher.accessPointAdapter.getItem(index);
        Intent infoIntent = new Intent(this, WifiInfoActivity.class);
        infoIntent.putExtra("accessPoint", accessPoint);
        startActivity(infoIntent);
    }

    public void onTrackButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        openLoadingScreen();

        final View finalView = view;

        Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                int index = (int)((View)finalView.getParent()).getTag();
                WifiAccessPoint accessPoint = wifiRefresher.accessPointAdapter.getItem(index);

                Intent mapIntent = new Intent(getApplicationContext(), PinMap.class);
                /*Pin pin = new Pin(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()),
                        new ArrayList<>(accessPoints), wifiRefresher.lastRefreshTime);
                ArrayList<Pin> pins = new ArrayList<>(1);
                pins.add(pin);
                Pin.putAllToIntent(pins, mapIntent);*/

                mapIntent.putExtra("filterMode", PinMap.FilterMode.FILTER_WIFI);
                mapIntent.putExtra("filter", accessPoint.getName());
                mapIntent.putExtra("lastLatitude", lastLocation.getLatitude());
                mapIntent.putExtra("lastLongitude", lastLocation.getLongitude());
                mapIntent.addFlags(FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(mapIntent);
            }
        });
    }

    public void onToMapButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        Intent mapIntent = new Intent(this, PinMap.class);
        mapIntent.putExtra("filterMode", PinMap.FilterMode.FILTER_WIFI);
        mapIntent.putExtra("filter", accessPoints.get(0).getName());
        startActivity(mapIntent);
    }

    public String tagSSID(@NonNull String ssid, @NonNull String bssid) {
        String name = wifiByteToNamePairing.
                getString(ssid + " " + bssid, "");
        if(name == "") {
            SharedPreferences.Editor byteToNameEditor = wifiByteToNamePairing.edit();
            SharedPreferences.Editor ssidToIndexEditor = wifiSSIDToIndexPairing.edit();
            int index = wifiSSIDToIndexPairing.getInt(ssid, 1);
            if(index > 1) {
                name = ssid + " [" + Integer.toString(index) + "]";
            } else {
                name = ssid;
            }
            byteToNameEditor.putString(ssid + " " + bssid, name);
            ssidToIndexEditor.putInt(ssid, index + 1);
            byteToNameEditor.commit();
            ssidToIndexEditor.commit();
        }
        return name;
    }

    public static MainActivity getInstance() {
        return INSTANCE;
    }

    private class WifiRefresher implements Runnable {
        private int delay;
        private WifiAccessPointAdapter accessPointAdapter;
        private List<ScanResult> lastScanResults;
        private Handler handler;
        private long lastRefreshTime;
        private boolean isPaused;

        public WifiRefresher(WifiAccessPointAdapter in_accessPointAdapter, int in_delay) {
            accessPointAdapter = in_accessPointAdapter;
            delay = in_delay;
            handler = new Handler();
            handler.post(this);
            lastRefreshTime = 0L;
            isPaused = false;
        }

        @Override
        public void run() {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> scanResults = wifiManager.getScanResults();
            lastRefreshTime = System.currentTimeMillis();

            Collections.sort(scanResults, new SacnResultStrengthComparator());

            if (!isPaused) {
                accessPointAdapter.clear();
                for (ScanResult result : scanResults) {
                    if (result != null) {
                        accessPointAdapter.add(new WifiAccessPoint(result));
                    }
                }
            }
            wifiManager.startScan();
            handler.postDelayed(this, (isPaused) ? 1000 : delay);
        }

        private void Pause() {
            isPaused = true;
        }

        private void Resume() {
            isPaused = false;
        }

        private class SacnResultStrengthComparator implements Comparator<ScanResult> {
            @Override
            public int compare(ScanResult a, ScanResult b) {
                if(a.level > b.level) {
                    return -1;
                }
                if(a.level < b.level) {
                    return 1;
                }
                return 0;
            }
        }
    }
}
