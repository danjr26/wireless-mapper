package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by danie on 4/15/2018.
 */

public class CellularFilterListEntryAdapter extends ArrayAdapter<CellularAccessPoint> {
    public CellularFilterListEntryAdapter(Context context, ArrayList<CellularAccessPoint> accessPoints) {
        super(context, 0, accessPoints);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        CellularAccessPoint accessPoint = getItem(position);

        if(convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_filter_list_cellular_entry, parent, false);
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
