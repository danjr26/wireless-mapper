package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class WifiInfoActivity extends AppCompatActivity {
    private WifiAccessPoint accessPoint;
    private AttributeEntryAdapter attributeEntryAdapter;
    private WifiRefresher wifiRefresher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_info);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        accessPoint = (WifiAccessPoint) getIntent().getSerializableExtra("accessPoint");

        View accessPointView = getLayoutInflater().inflate(R.layout.item_wifi_access_point, (ViewGroup) findViewById(R.id.RootContainer), false);
        accessPointView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onAccessPointClick(view);
            }
        });
        accessPointView.findViewById(R.id.WifiIcon).getLayoutParams().height =
                accessPoint.getIconSize(getResources().getDimensionPixelSize(R.dimen.wifiIconMaxHeight), -90, -50);
        ConstraintLayout rootView = findViewById(R.id.RootContainer);
        rootView.addView(accessPointView);
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(rootView);
        constraintSet.connect(accessPointView.getId(), ConstraintSet.TOP, rootView.getId(), ConstraintSet.TOP, 0);
        constraintSet.connect(findViewById(R.id.DividerLine1).getId(), ConstraintSet.TOP, accessPointView.getId(), ConstraintSet.BOTTOM, 0);
        constraintSet.applyTo(rootView);
        rootView.invalidate();
        rootView.requestLayout();

        ArrayList<AttributeEntry> attributeEntries = new ArrayList();
        attributeEntries.add(new AttributeEntry("SSID",         accessPoint.getSSID(),
                getResources().getString(R.string.wifi_ssid_attribute_info)));
        attributeEntries.add(new AttributeEntry("BSSID",        accessPoint.getBSSID(),
                getResources().getString(R.string.wifi_bssid_attribute_info)));
        attributeEntries.add(new AttributeEntry("Capabilities", accessPoint.getCapabilities(),
                getResources().getString(R.string.wifi_capabilities_attribute_info)));
        attributeEntries.add(new AttributeEntry("Frequency", Integer.toString(accessPoint.getFrequency()) + " Mhz",
                getResources().getString(R.string.wifi_frequency_attribute_info)));
        attributeEntries.add(new AttributeEntry("Channel",      Integer.toString(accessPoint.getChannel()),
                getResources().getString(R.string.wifi_channel_attribute_info)));

        attributeEntryAdapter = new AttributeEntryAdapter(this, attributeEntries);
        ((ListView)findViewById(R.id.AttributeList)).setAdapter(attributeEntryAdapter);

        ((TextView)findViewById(R.id.AccessPointName)).setText(accessPoint.getName());
        ((TextView)findViewById(R.id.AccessPointDbm)).setText(Integer.toString(accessPoint.getStrengthDbm()) + " Dbm");
        ((TextView)findViewById(R.id.AccessPointPw)).setText(Integer.toString(accessPoint.getStrengthPw()) + " pW");

        wifiRefresher = new WifiRefresher(5000);
    }

    public void onAttributeEntryClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        ListView parent = (ListView) view.getParent();
        int index = parent.indexOfChild(view);
        AttributeEntry attributeEntry = attributeEntryAdapter.getItem(index);
        ((TextView)findViewById(R.id.InfoBox)).setText(Html.fromHtml(attributeEntry.infoText));

        findViewById(R.id.AccessPoint).setBackgroundColor(getResources().getColor(R.color.colorContent));
        for(int i = 0; i < parent.getChildCount(); i++) {
            if(i == index) {
                view.setBackgroundColor(getResources().getColor(R.color.colorContentSelected));
            }
            else {
                View child = parent.getChildAt(i);
                child.setBackgroundColor(getResources().getColor(R.color.colorContent));
            }
        }
    }

    public void onAccessPointClick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        ((TextView)findViewById(R.id.InfoBox)).setText(Html.fromHtml(getResources().getText(R.string.wifi_access_point_attribute_info).toString()));
        ListView attributeList = findViewById(R.id.AttributeList);

        view.setBackgroundColor(getResources().getColor(R.color.colorContentSelected));
        for(int i = 0; i < attributeList.getChildCount(); i++) {
            View child = attributeList.getChildAt(i);
            child.setBackgroundColor(getResources().getColor(R.color.colorContent));
        }
    }

    private class WifiRefresher implements Runnable {
        private int delay;
        private Handler handler;
        private long lastRefreshTime;

        public WifiRefresher(int in_delay) {
            delay = in_delay;
            handler = new Handler();
            lastRefreshTime = 0L;
            handler.post(this);
        }

        @Override
        public void run() {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> scanResults = wifiManager.getScanResults();
            lastRefreshTime = System.currentTimeMillis();

            for (ScanResult result : scanResults) {
                if (result != null && result.BSSID.equals(accessPoint.getBSSID()) && result.SSID.equals(accessPoint.getSSID())) {
                    accessPoint = new WifiAccessPoint(result);
                    ((TextView)findViewById(R.id.AccessPointName)).setText(accessPoint.getName());
                    ((TextView)findViewById(R.id.AccessPointDbm)).setText(Integer.toString(accessPoint.getStrengthDbm()) + " Dbm");
                    ((TextView)findViewById(R.id.AccessPointPw)).setText(Integer.toString(accessPoint.getStrengthPw()) + " pW");
                    ((ImageView)findViewById(R.id.WifiIcon)).getLayoutParams().height =
                            accessPoint.getIconSize(getResources().getDimensionPixelSize(R.dimen.wifiIconMaxHeight), -90, -50);
                    break;
                }
            }

            wifiManager.startScan();
            handler.postDelayed(this, delay);
        }
    }
}
