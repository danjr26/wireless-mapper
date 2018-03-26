package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

import static com.wiprof.wirelessprofiler.PinMap.FilterMode.FILTER_BLUETOOTH;
import static com.wiprof.wirelessprofiler.PinMap.FilterMode.FILTER_CELLULAR;
import static com.wiprof.wirelessprofiler.PinMap.FilterMode.FILTER_WIFI;

/**
 * Created by danie on 2/26/2018.
 */

public class PinListEntryAdapter extends ArrayAdapter<Pin> {
    public PinListEntryAdapter(Context context, ArrayList<Pin> pins) {
        super(context, 0, pins);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Pin pin = getItem(position);

        if(convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_pin_list_entry, parent, false);
        }

        PinMap activity = PinMap.getInstance();

        String currentFilterStrength = "";

        switch(activity.getFilterMode()) {
            case FILTER_WIFI:
                String format = activity.getResources().getString(R.string.wifi_strength_dbm_format);
                WifiAccessPoint filterResult = pin.getWifiFilterResult(activity.getFilter());
                if(filterResult == null) {
                    currentFilterStrength = activity.getResources().getString(R.string.no_signal_message);
                } else {
                    int strengthDbm = filterResult.getStrengthDbm();
                    currentFilterStrength = String.format(format, strengthDbm);
                }

            case FILTER_BLUETOOTH:
            case FILTER_CELLULAR:
        }

        ((ImageView)convertView.findViewById(R.id.PinIcon)).setColorFilter(pin.getColor(), PorterDuff.Mode.MULTIPLY);
        ((TextView)convertView.findViewById(R.id.CurrentFilterStrength)).setText(currentFilterStrength);
        ((TextView)convertView.findViewById(R.id.Timestamp)).setText(pin.getAgoTimestamp());
        ((CheckBox)convertView.findViewById(R.id.CheckBox)).setChecked(pin.isSelected());

        convertView.setTag(R.id.PinTag, pin);

        return convertView;
    }
}
