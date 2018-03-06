package com.wiprof.wirelessprofiler;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static android.content.Context.MODE_PRIVATE;

public class Pin implements Serializable {
    public static final long serialVersionUID = 1L;
    private static String saveFileName = "pins";
    private transient Marker marker;
    private transient LatLng location;
    private long time;
    private ArrayList<WifiAccessPoint> wifiInfo;
    private transient int wifiInfoActiveIndex;
    private transient int color;
    private transient boolean isSelected;

    public Pin() {

    }

    public Pin(LatLng location, ArrayList<WifiAccessPoint> wifiInfo, long time) {
        this.location = location;
        this.wifiInfo = wifiInfo;
        this.time = time;
        this.marker = null;
        this.color = 0;
        this.isSelected = false;
        wifiInfoActiveIndex = -1;
    }


    public LatLng getLocation() {
        return location;
    }

    public long getTime() {
        return time;
    }

    public String getReadableTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss a", Locale.US).format(new Date(time));
    }

    public String getAgoTimestamp() {
        long millis = System.currentTimeMillis() - time;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long years = (long)(days / 365.25);

        if(years > 0) {
            if(years == 1) {
                return "last year";
            }
            return String.format(Locale.US,"%d years ago", years);
        }
        if(days > 0) {
            if(days == 1) {
                return "yesterday";
            }
            return String.format(Locale.US, "%d days ago", days);
        }
        if(hours > 0) {
            if(hours == 1) {
                return "an hour ago";
            }
            return String.format(Locale.US, "%d hours ago", hours);
        }
        if(minutes > 0) {
            if(minutes == 1) {
                return "a minute ago";
            }
            return String.format(Locale.US, "%d minutes ago", minutes);
        }
        return "moments ago";
    }

    public void setLocation(LatLng location) {
        this.location = location;
    }

    public void updateSnippet() {
        if(marker != null) {
            marker.setSnippet(getAgoTimestamp());
        }
    }

    public Marker getMarker() {
        return marker;
    }

    public ArrayList<WifiAccessPoint> getWifiInfo() {
        return wifiInfo;
    }

    public WifiAccessPoint getWifiFilterResult(String filter) {
        for(int i = 0; i < wifiInfo.size(); i++) {
            WifiAccessPoint accessPoint = wifiInfo.get(i);
            if(accessPoint != null && accessPoint.getName().equals(filter)) {
               return accessPoint;
            }
        }
        return null;
    }

    public boolean isValid() {
        return !(location == null || wifiInfo == null || location.latitude < -90.0 ||
                location.latitude > 90.0 || location.longitude < -180.0 || location.latitude > 180.0);
    }

    public void Display(GoogleMap map, PinMap.FilterMode filterMode, String filter) {
        if(marker != null) {
            marker.remove();
        }

        float alpha = 1.0f;
        float hsv[] = new float[] {0.0f, 0.0f, 0.5f};
        String title = "N/A";
        String snippet = getAgoTimestamp();

        switch(filterMode) {
            case FILTER_WIFI:
                for(int i = 0; i < wifiInfo.size(); i++) {
                    WifiAccessPoint accessPoint = wifiInfo.get(i);
                    if(accessPoint != null && accessPoint.getName().equals(filter)) {
                        wifiInfoActiveIndex = i;
                        title = Integer.toString(accessPoint.getStrengthDbm()) + " Dbm";

                        int strength = accessPoint.getStrengthDbm();
                        if(strength < -80) strength = -80;
                        if(strength > -40) strength = -40;
                        alpha = 1.0f;
                        hsv[0] = 240 + ((strength + 40) * 6);
                        hsv[1] = 1.0f;
                        hsv[2] = 1.0f;
                        break;
                    }
                }
                break;
            case FILTER_BLUETOOTH:

                break;
        }

        color = Color.HSVToColor(hsv);//MainActivity.getInstance().getResources().getColor(R.color.colorWifiSignal);
        Bitmap bitmap = BitmapFactory.decodeResource(MainActivity.getInstance().getResources(), R.drawable.pin);
        Bitmap tintedBitmap = tintBitmap(bitmap, color);

        marker = map.addMarker(
                new MarkerOptions()
                        .position(location)
                        .title(title)
                        .alpha(alpha)
                        .snippet(snippet)
                        .icon(BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(tintedBitmap, 30, 100, false)))
        );
    }

    public void Hide() {
        marker.remove();
        marker = null;
    }

    private static Bitmap tintBitmap(Bitmap bitmap, int color) {
        Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint);
        return newBitmap;
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        outputStream.defaultWriteObject();
        outputStream.writeDouble(location.latitude);
        outputStream.writeDouble(location.longitude);
        outputStream.flush();
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        location = new LatLng(inputStream.readDouble(), inputStream.readDouble());
    }

    public static void putAllToFile(ArrayList<Pin> pins, Context context) {
        try {
            File file = new File(context.getFilesDir(), saveFileName);
            file.createNewFile();
            FileOutputStream fileStream = new FileOutputStream(file);
            ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
            objectStream.writeInt(pins.size());
            for(int i = 0; i < pins.size(); i++) {
                objectStream.writeObject(pins.get(i));
            }
            objectStream.close();
            fileStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<Pin> getAllFromFile(Context context) {
        ArrayList<Pin> pins = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), saveFileName);
            FileInputStream fileStream = new FileInputStream(file);
            ObjectInputStream objectStream = new ObjectInputStream(fileStream);
            int nPins = objectStream.readInt();
            pins.ensureCapacity(nPins);
            for(int i = 0; i < nPins; i++) {
                pins.add((Pin)objectStream.readObject());
            }
            objectStream.close();
            fileStream.close();
        } catch(FileNotFoundException e) {

        } catch (Exception e) {
            e.printStackTrace();
        }
        return pins;
    }

    public static void clearAllFromFile(Context context) {
        try {
            File file = new File(context.getFilesDir(), saveFileName);
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();;
        }
    }

    public static ArrayList<Pin> getAllFromIntent(Intent intent) {
        ArrayList<Pin> pins = new ArrayList<>();
        int nPins = intent.getIntExtra("nPins", 0);
        pins.ensureCapacity(nPins);
        for(int i = 0; i < nPins; i++) {
            pins.add((Pin) intent.getSerializableExtra("pin" + Integer.toString(i)));
        }
        return pins;
    }

    public static void putAllToIntent(ArrayList<Pin> pins, Intent intent) {
        intent.putExtra("nPins", pins.size());
        for(int i = 0; i < pins.size(); i++) {
            intent.putExtra("pin" + Integer.toString(i), pins.get(i));
        }
    }

    public void select() {
        isSelected = true;
    }

    public void unselect() {
        isSelected = false;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public int getColor() {
        return color;
    }
}
