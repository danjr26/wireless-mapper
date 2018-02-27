package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;

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

        LatLng location = pin.getLocation();
        DecimalFormat format = new DecimalFormat("###.########");

        ((ImageView)convertView.findViewById(R.id.PinIcon)).setColorFilter(pin.getColor(), PorterDuff.Mode.MULTIPLY);
        ((TextView)convertView.findViewById(R.id.LatitudeLongitude)).setText(
                "( " + format.format(location.latitude) + ", " + format.format(location.longitude) + " )");
        ((TextView)convertView.findViewById(R.id.Timestamp)).setText(pin.getReadableTimestamp());

        convertView.setTag(position);

        return convertView;
    }
}
