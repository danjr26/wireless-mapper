package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by danie on 2/20/2018.
 */

public class WifiFilterListEntryAdapter extends ArrayAdapter<WifiAccessPoint> {
    public WifiFilterListEntryAdapter(Context context, ArrayList<WifiAccessPoint> accessPoints) {
        super(context, 0, accessPoints);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        WifiAccessPoint accessPoint = getItem(position);

        if(convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_filter_list_wifi_entry, parent, false);
        }

        int strength = accessPoint.getStrengthDbm();

        ((TextView)convertView.findViewById(R.id.AccessPointName)).setText(accessPoint.getName());
        if(strength != 0) {
            ((TextView) convertView.findViewById(R.id.AccessPointStrengthDbm)).setText(Integer.toString(strength) + " Dbm");
        } else {
            ((TextView) convertView.findViewById(R.id.AccessPointStrengthDbm)).setText("N/A");
        }

        convertView.setTag(position);

        return convertView;
    }
}
