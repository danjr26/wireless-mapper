package com.wiprof.wirelessprofiler;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

import static android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT;
import static android.telephony.TelephonyManager.NETWORK_TYPE_CDMA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EDGE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B;
import static android.telephony.TelephonyManager.NETWORK_TYPE_GPRS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_IDEN;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static com.wiprof.wirelessprofiler.CellularAccessPoint.GeneralNetworkType.CDMA;
import static com.wiprof.wirelessprofiler.CellularAccessPoint.GeneralNetworkType.GSM;
import static com.wiprof.wirelessprofiler.CellularAccessPoint.GeneralNetworkType.LTE;
import static com.wiprof.wirelessprofiler.CellularAccessPoint.GeneralNetworkType.OTHER;
import static com.wiprof.wirelessprofiler.CellularAccessPoint.GeneralNetworkType.UMTS;

/**
 * Created by danie on 3/26/2018.
 */

public class CellularAccessPoint implements Serializable {
    public static final long serialVersionUID = 3L;
    public enum GeneralNetworkType {
        CDMA,
        GSM,
        LTE,
        UMTS,
        OTHER
    }

    private GeneralNetworkType generalNetworkType;
    private int strengthDbm;

    private int mobileCountryCode; // default to 310 (U.S.A. for CDMA)
    private int mobileNetworkCode;
    private int locationAreaCode;
    private int cellID;

    private double latitude;
    private double longitude;

    private boolean isRegistered;

    public CellularAccessPoint(Context context, CellInfo cellularInfo) {
        isRegistered = cellularInfo.isRegistered();

        latitude = -181;
        longitude = -181;

        if(cellularInfo instanceof CellInfoCdma) {
            generalNetworkType = CDMA;
        } else {
            if(cellularInfo instanceof CellInfoGsm) {
                generalNetworkType = GSM;
            } else {
                if(cellularInfo instanceof CellInfoLte) {
                    generalNetworkType = LTE;
                } else {
                    if(cellularInfo instanceof CellInfoWcdma) {
                        generalNetworkType = UMTS;
                    } else {
                        generalNetworkType = OTHER;
                    }
                }
            }
        }

        switch (generalNetworkType) {
            case CDMA:
                CellInfoCdma cdmaInfo = (CellInfoCdma)cellularInfo;
                CellIdentityCdma cdmaIdentity = cdmaInfo.getCellIdentity();

                strengthDbm = cdmaInfo.getCellSignalStrength().getDbm();
                mobileCountryCode = 310; // USA
                mobileNetworkCode = cdmaIdentity.getSystemId();
                locationAreaCode = cdmaIdentity.getNetworkId();
                cellID = cdmaIdentity.getBasestationId();
                break;
            case GSM:
                CellInfoGsm gsmInfo = (CellInfoGsm)cellularInfo;
                CellIdentityGsm gsmIdentity = gsmInfo.getCellIdentity();
                strengthDbm = gsmInfo.getCellSignalStrength().getDbm();
                mobileCountryCode = gsmIdentity.getMcc();
                mobileNetworkCode = gsmIdentity.getMnc();
                locationAreaCode = gsmIdentity.getLac();
                cellID = gsmIdentity.getCid();
                break;
            case LTE:
                CellInfoLte lteInfo = (CellInfoLte)cellularInfo;
                CellIdentityLte lteIdentity = lteInfo.getCellIdentity();
                strengthDbm = lteInfo.getCellSignalStrength().getDbm();
                mobileCountryCode = lteIdentity.getMcc();
                mobileNetworkCode = lteIdentity.getMnc();
                locationAreaCode = lteIdentity.getTac();
                cellID = lteIdentity.getCi();
                break;
            case UMTS:
                CellInfoWcdma wcdmaInfo = (CellInfoWcdma)cellularInfo;
                CellIdentityWcdma wcdmaIdentity = wcdmaInfo.getCellIdentity();
                strengthDbm = wcdmaInfo.getCellSignalStrength().getDbm();
                mobileCountryCode = wcdmaIdentity.getMcc();
                mobileNetworkCode = wcdmaIdentity.getMnc();
                locationAreaCode = wcdmaIdentity.getLac();
                cellID = wcdmaIdentity.getCid();
                break;
            default:
                strengthDbm = 0;
                break;
        }

        strengthDbm = (strengthDbm + 5) / -10;

        if(strengthDbm == 0) {
            strengthDbm = -130;
        }

        new LocationPullQuery().execute(
                generalNetworkTypeToString(generalNetworkType),
                Integer.toString(mobileCountryCode),
                Integer.toString(mobileNetworkCode),
                Integer.toString(locationAreaCode),
                Integer.toString(cellID)
        );
    }

    public void fillView(@NonNull ConstraintLayout view) {
        ((TextView)view.findViewById(R.id.AccessPointName)).setText(getName());
        ((TextView)view.findViewById(R.id.AccessPointStrengthDbm)).setText(Integer.toString(getStrengthDbm()) + " Dbm");
        ((TextView)view.findViewById(R.id.AccessPointPw)).setText(Integer.toString(getStrengthFw()) + " fW");
        ((ImageView)view.findViewById(R.id.CellularIcon)).getLayoutParams().width =
                getIconSize(MainActivity.getInstance().getResources().getDimensionPixelSize(R.dimen.wifiIconMaxHeight), -120, -60);

        if(!isIDValid()) {
            Button trackButton = ((Button)view.findViewById(R.id.TrackButton));
            trackButton.setClickable(false);
            trackButton.setAlpha(0.4f);
        }
    }

    public String getName() {
        if(!isIDValid()) {
            return String.format(Locale.US, "[%s]\nNo ID Provided", generalNetworkTypeToString(generalNetworkType));
        }
        return String.format(Locale.US, "[%s]\n%03d.%03d.%05d.%09d",
                generalNetworkTypeToString(generalNetworkType), mobileCountryCode, mobileNetworkCode, locationAreaCode, cellID);
    }

    public String getUniqueCellString() {
        return Integer.toString(mobileCountryCode) + Integer.toString(mobileNetworkCode) + Integer.toString(locationAreaCode) + Integer.toString(cellID);
    }

    public float getStrengthZeroOne(float zero, float one) {
        if(strengthDbm <= zero)
            return 0.0f;
        if(strengthDbm >= one)
            return 1.0f;
        return (strengthDbm - zero) / (one - zero);
    }

    public int getStrengthDbm() {
        return strengthDbm;
    }

    public int getStrengthFw() {
        double sigFig = Math.pow(10, 11 - Math.ceil(strengthDbm / -10));
        return (int)(Math.pow(10, strengthDbm / 10.0)*10e12 / sigFig) * (int) sigFig;
    }

    public int getIconSize(int maxSize, float zero, float one) {
        float strength = getStrengthZeroOne(zero, one);
        int size = (int)(strength * (float)maxSize);
        if(size == 0)
            size = 1;
        return size;
    }

    public static String generalNetworkTypeToString(GeneralNetworkType generalNetworkType) {
        switch (generalNetworkType) {
            case CDMA:
                return "CDMA";
            case GSM:
                return "GSM";
            case LTE:
                return "LTE";
            case UMTS:
                return "UMTS";
            default:
                return "UNKNOWN";
        }
    }

    public static String networkTypeToString(int networkType) {
        switch(networkType) {
            case NETWORK_TYPE_CDMA:
                return "CDMA";
            case NETWORK_TYPE_IDEN:
                return "iDEN";
            case NETWORK_TYPE_GPRS:
                return "GPRS";
            case NETWORK_TYPE_EDGE:
                return "EDGE";
            case NETWORK_TYPE_UMTS:
                return "UMTS";
            case NETWORK_TYPE_1xRTT:
                return "1xRTT (CDMA2000)";
            case NETWORK_TYPE_EVDO_0:
                return "EVDO A";
            case NETWORK_TYPE_EVDO_A:
                return "EVDO B";
            case NETWORK_TYPE_EVDO_B:
                return "EVDO 0";
            case NETWORK_TYPE_EHRPD:
                return "EHRPD";
            case NETWORK_TYPE_HSDPA:
                return "HSDPA";
            case NETWORK_TYPE_HSPA:
                return "HSPA";
            case NETWORK_TYPE_HSUPA:
                return "HSUPA";
            case NETWORK_TYPE_HSPAP:
                return "HSPAP";
            case NETWORK_TYPE_LTE:
                return "LTE";
            default:
                return "unknown";
        }
    }

    public static String networkTypeToGenerationString(int networkType) {
        switch (networkType) {
            case NETWORK_TYPE_CDMA:
            case NETWORK_TYPE_IDEN:
            case NETWORK_TYPE_GPRS:
            case NETWORK_TYPE_EDGE:
                return "2G";
            case NETWORK_TYPE_UMTS:
            case NETWORK_TYPE_1xRTT:
            case NETWORK_TYPE_EVDO_0:
            case NETWORK_TYPE_EVDO_A:
            case NETWORK_TYPE_EVDO_B:
            case NETWORK_TYPE_EHRPD:
            case NETWORK_TYPE_HSDPA:
            case NETWORK_TYPE_HSPA:
            case NETWORK_TYPE_HSUPA:
            case NETWORK_TYPE_HSPAP:
                return "3G";
            case NETWORK_TYPE_LTE:
                return "4G";
            default:
                return "UNKNOWN";
        }
    }

    public static class LocationPullService extends IntentService {

        public LocationPullService() {
            super("cellular_location_pull");
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            String urlString = String.format(
                    Locale.US,
                    "http://dsg1.crc.nd.edu/wirelessmapper/index.php?radio=%s&mcc=%d&mnc=%d&lac=%d&cid=%d",
                    intent.getStringExtra("generalNetworkType"),
                    intent.getIntExtra("mobileCountryCode", -1),
                    intent.getIntExtra("mobileNetworkCode", -1),
                    intent.getIntExtra("locationAreaCode", -1),
                    intent.getIntExtra("cellID", -1)
            );

            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                InputStream inStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inStream));
                String output = reader.readLine();
                inStream.close();

                if(output.equals("null")) {
                    return;
                }

                Scanner scanner = new Scanner(output);
                double latitude = scanner.nextDouble();
                double longitude = scanner.nextDouble();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

        }
    }

    private class LocationPullQuery extends AsyncTask<String, Void, LatLng> {

        @Override
        protected LatLng doInBackground(String... strings) {
            String urlString = String.format(
                    Locale.US,
                    "http://dsg1.crc.nd.edu/wirelessmapper/index.php?radio=%s&mcc=%s&mnc=%s&lac=%s&cid=%s",
                    strings[0],
                    strings[1],
                    strings[2],
                    strings[3],
                    strings[4]
            );
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                InputStream inStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inStream));
                String output = reader.readLine();
                inStream.close();
                connection.disconnect();

                if(output.equals("null")) {
                    return new LatLng(-181, -181);
                }

                Scanner scanner = new Scanner(output);
                LatLng location = new LatLng(scanner.nextDouble(), scanner.nextDouble());
                return location;
            } catch (IOException e) {
                e.printStackTrace();
                return new LatLng(-181, -181);
            }
        }

        @Override
        protected void onPostExecute(LatLng result) {
            latitude = result.latitude;
            longitude = result.longitude;
        }
    }

    boolean isIDValid() {
        return !(mobileCountryCode == Integer.MAX_VALUE || mobileNetworkCode == Integer.MAX_VALUE || locationAreaCode == Integer.MAX_VALUE || cellID == Integer.MAX_VALUE);
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public boolean isLocationValid() {
        return !(latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || latitude > 180.0);
    }

    public LatLng getLocation() {
        return new LatLng(latitude, longitude);
    }
}
