package com.wiprof.wirelessprofiler;

import android.net.wifi.ScanResult;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

public class WifiAccessPoint implements Serializable {
    public static final long serialVersionUID = 2L;
    private String SSID;
    private String BSSID;
    private String capabilities;
    public int frequency;
    public int level;
    public long timestamp;

    public WifiAccessPoint(@NonNull WifiAccessPoint source) {
        this.SSID =         source.SSID;
        this.BSSID =        source.BSSID;
        this.capabilities = source.capabilities;
        this.frequency =    source.frequency;
        this.level =        source.level;
        this.timestamp =    source.timestamp;
    }

    public WifiAccessPoint(@NonNull ScanResult scanResult) {
        this.SSID =         scanResult.SSID;
        this.BSSID =        scanResult.BSSID;
        this.capabilities = scanResult.capabilities;
        this.frequency =    scanResult.frequency;
        this.level =        scanResult.level;
        this.timestamp =    scanResult.timestamp;
    }

    public void fillView(@NonNull ConstraintLayout view) {
        ((TextView)view.findViewById(R.id.AccessPointName)).setText(getName());
        ((TextView)view.findViewById(R.id.AccessPointStrengthDbm)).setText(Integer.toString(getStrengthDbm()) + " Dbm");
        ((TextView)view.findViewById(R.id.AccessPointPw)).setText(Integer.toString(getStrengthPw()) + " pW");
        ((ImageView)view.findViewById(R.id.WifiIcon)).getLayoutParams().height =
                getIconSize(MainActivity.getInstance().getResources().getDimensionPixelSize(R.dimen.wifiIconMaxHeight), -90, -40);
    }

    @NonNull
    public String getName() {
        String name = SSID;
        if(name == null || name.isEmpty()) {
            name = MainActivity.getInstance().getResources().getString(R.string.wifi_empty_name);
        }
        name = MainActivity.getInstance().tagWifiAccessPointName(name, BSSID);
        return name;
    }

    public int getStrengthDbm() {
        return level;
    }

    public int getStrengthPw() {
        double sigFig = Math.pow(10, 8 - Math.ceil(level / -10));
        return (int)(Math.pow(10, level / 10.0)*10e9 / sigFig) * (int) sigFig;
    }

    public float getStrengthZeroOne(float zero, float one) {
        if(level <= zero)
            return 0.0f;
        if(level >= one)
            return 1.0f;
        return (level - zero) / (one - zero);
    }

    public String getReadableTimestamp(String format) {
        long wallTime = System.currentTimeMillis() - SystemClock.uptimeMillis() + timestamp / 1000L;
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        Date date = new Date(wallTime);
        return dateFormat.format(date);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getIconSize(int maxSize, float zero, float one) {
        float strength = getStrengthZeroOne(zero, one);
        int size = (int)(strength * (float)maxSize);
        if(size == 0)
            size = 1;
        return size;
    }

    public int getChannel() {
        if(frequency >= 2412 && frequency < 2484) {
            return (frequency - 2407) / 5;
        } else {
            if(frequency == 4284) {
                return 14;
            }
            else {
                if(frequency >= 3657 && frequency <= 3693) {
                    return (frequency - 3655) / 5 + 131;
                }
                else {
                    if(frequency >= 5035 && frequency <= 5865) {
                        return (frequency - 5000) / 5;
                    }
                    else {
                        return -1;
                    }
                }
            }
        }
    }

    public String getSSID() {
        return SSID;
    }

    public String getBSSID() {
        return BSSID;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public int getFrequency() {
        return frequency;
    }

    public void Drain() {
        level = 0;
        timestamp = 0L;
    }
}
