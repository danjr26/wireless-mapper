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
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;

public class MainActivity extends AppCompatActivity {
    public static final int MY_PERMISSIONS_FINE_LOCATION = 0;
    public static final int MY_PERMISSIONS_READ_PHONE_STATE = 1;
    private static MainActivity INSTANCE;

    private FusedLocationProviderClient locationProvider;
    private Location lastLocation;

    private TelephonyManager cellularManager;

    private SharedPreferences wifiByteToNamePairing;
    private SharedPreferences wifiSSIDToIndexPairing;

    private SharedPreferences cellularIDToNamePairing;
    private SharedPreferences cellularTypeToIndexPairing;

    private WifiRefresher wifiRefresher;
    private CellularRefresher cellularRefresher;
    private HashMap<View, View> tabToContentPairing;

    private int activeTabId;
    private int activeTabContentId;

    private ArrayList<WifiAccessPoint> wifiAccessPoints;
    private WifiAccessPointAdapter wifiAccessPointAdapter;

    private ArrayList<CellularAccessPoint> cellularAccessPoints;
    private CellularAccessPointAdapter cellularAccessPointAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        openLoadingScreen();

        INSTANCE = this;

        setTitle(Html.fromHtml(getResources().getString(R.string.app_name)));

        locationProvider = LocationServices.getFusedLocationProviderClient(this);
        lastLocation = null;

        cellularManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                lastLocation = result.getLastLocation();
            }
        };

        if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.MY_PERMISSIONS_FINE_LOCATION);
        }

        locationProvider.requestLocationUpdates(locationRequest, locationCallback, null);

        getWindow().setExitTransition(new Explode());

        wifiByteToNamePairing = getSharedPreferences(getString(R.string.wifi_byte_to_ssid_pref_key), MODE_PRIVATE);
        wifiSSIDToIndexPairing = getSharedPreferences(getString(R.string.wifi_ssid_to_index_pref_key), MODE_PRIVATE);

        cellularIDToNamePairing = getSharedPreferences(getString(R.string.cellular_id_to_name_pref_key), MODE_PRIVATE);
        cellularTypeToIndexPairing = getSharedPreferences(getString(R.string.cellular_type_to_index_pref_key), MODE_PRIVATE);

        /*
        SharedPreferences.Editor byteToNameEditor = wifiByteToNamePairing.edit();
        SharedPreferences.Editor ssidToIndexEditor = wifiSSIDToIndexPairing.edit();
        byteToNameEditor.clear();
        ssidToIndexEditor.clear();
        byteToNameEditor.commit();
        ssidToIndexEditor.commit();
        */

        wifiAccessPoints = new ArrayList<>();
        wifiAccessPointAdapter = new WifiAccessPointAdapter(this, wifiAccessPoints);
        ((ListView)findViewById(R.id.WifiList)).setAdapter(wifiAccessPointAdapter);

        cellularAccessPoints = new ArrayList<>();
        cellularAccessPointAdapter = new CellularAccessPointAdapter(this, cellularAccessPoints);
        ((ListView)findViewById(R.id.CellularList)).setAdapter(cellularAccessPointAdapter);

        wifiRefresher = new WifiRefresher(this, 1000);
        cellularRefresher = new CellularRefresher(this, 1000);

        tabToContentPairing = new HashMap<>();
        tabToContentPairing.put(findViewById(R.id.WifiTab), findViewById(R.id.WifiTabContent));
        tabToContentPairing.put(findViewById(R.id.CellularTab), findViewById(R.id.CellularTabContent));
        tabToContentPairing.put(findViewById(R.id.BluetoothTab), findViewById(R.id.BluetoothTabContent));

        setActiveTab(findViewById(R.id.WifiTab));

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

        View tabContent = tabToContentPairing.get(tab);

        tabContent.bringToFront();
        ((View)tabContent.getParent()).invalidate();
        ((View)tabContent.getParent()).requestLayout();
    }

    public int getActiveTabId() { return activeTabId; }

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
            switch(getActiveTabId()) {
                case R.id.WifiTab:
                    wifiRefresher.Resume();
                    break;
                case R.id.CellularTab:
                    cellularRefresher.Resume();
                    break;
            }
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
            switch(getActiveTabId()) {
                case R.id.WifiTab:
                    wifiRefresher.Pause();
                    break;
                case R.id.CellularTab:
                    cellularRefresher.Pause();
                    break;
            }
        }
        view.invalidate();
        view.requestLayout();
    }

    public void onInfoButtonClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        int index = (int)((View)view.getParent()).getTag();
        WifiAccessPoint accessPoint = wifiAccessPointAdapter.getItem(index);
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

                Intent mapIntent = new Intent(getApplicationContext(), PinMap.class);

                switch(getActiveTabId()) {
                    case R.id.WifiTab: {
                        WifiAccessPoint accessPoint = wifiAccessPointAdapter.getItem(index);
                        mapIntent.putExtra("filterMode", PinMap.FilterMode.FILTER_WIFI);
                        mapIntent.putExtra("filter", accessPoint.getName());
                        break;
                    }
                    case R.id.CellularTab: {
                        CellularAccessPoint accessPoint = cellularAccessPointAdapter.getItem(index);
                        mapIntent.putExtra("filterMode", PinMap.FilterMode.FILTER_CELLULAR);
                        mapIntent.putExtra("filter", accessPoint.getName());
                        break;
                    }
                }

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
        mapIntent.putExtra("filter", wifiAccessPoints.get(0).getName());
        startActivity(mapIntent);
    }

    public String tagWifiAccessPointName(@NonNull String ssid, @NonNull String bssid) {
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

    public String tagCellularAccessPointName(CellularAccessPoint.GeneralNetworkType generalNetworkType, String uniqueCellString) {
        String generalNetworkTypeString = CellularAccessPoint.generalNetworkTypeToString(generalNetworkType);
        String name = cellularIDToNamePairing.getString(uniqueCellString, "");

        if(name == "") {
            SharedPreferences.Editor idToNameEditor = cellularIDToNamePairing.edit();
            SharedPreferences.Editor typeToIndexEditor = cellularTypeToIndexPairing.edit();

            int index = cellularTypeToIndexPairing.getInt(generalNetworkTypeString, 1);
            if(index > 1) {
                name = generalNetworkTypeString + " Network [" + Integer.toString(index) + "]";
            } else {
                name = generalNetworkTypeString + " Network";
            }

            idToNameEditor.putString(uniqueCellString, name);
            typeToIndexEditor.putInt(generalNetworkTypeString, index + 1);

            idToNameEditor.commit();
            typeToIndexEditor.commit();
        }
        return name;
    }

    public static MainActivity getInstance() {
        return INSTANCE;
    }

    private class WifiRefresher implements Runnable {
        private int delay;
        private MainActivity activity;
        private List<ScanResult> lastScanResults;
        private Handler handler;
        private long lastRefreshTime;
        private boolean isPaused;

        private WifiRefresher(MainActivity activity, int delay) {
            this.activity = activity;
            this.delay = delay;
            this.handler = new Handler();
            handler.post(this);
            this.lastRefreshTime = 0L;
            this.isPaused = false;
        }

        @Override
        public void run() {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> scanResults = wifiManager.getScanResults();
            lastRefreshTime = System.currentTimeMillis();

            Collections.sort(scanResults, new ScanResultStrengthComparator());
            WifiAccessPointAdapter adapter = activity.wifiAccessPointAdapter;

            if (!isPaused) {
                adapter.clear();
                for (ScanResult result : scanResults) {
                    if (result != null) {
                        adapter.add(new WifiAccessPoint(result));
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

        private class ScanResultStrengthComparator implements Comparator<ScanResult> {
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

    private class CellularRefresher implements Runnable {
        private int delay;
        private Handler handler;
        private boolean isPaused;
        MainActivity activity;

        private CellularRefresher(MainActivity activity, int delay) {
            this.delay = delay;
            this.handler = new Handler();
            handler.post(this);
            this.isPaused = false;
            this.activity = activity;
        }

        @Override
        public void run() {
            if(Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, MainActivity.MY_PERMISSIONS_READ_PHONE_STATE);
            }
            List<CellInfo> cellInfos = cellularManager.getAllCellInfo();
            CellularAccessPointAdapter adapter = activity.cellularAccessPointAdapter;

            if(!isPaused) {
                adapter.clear();
                for (CellInfo cellInfo : cellInfos) {
                    adapter.add(new CellularAccessPoint(activity, cellInfo));
                }
            }

            handler.postDelayed(this, delay);
        }

        private void Pause() {
            isPaused = true;
        }

        private void Resume() {
            isPaused = false;
        }
    }
}
