package com.wiprof.wirelessprofiler;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;


public class WifiAccessPointAdapter extends ArrayAdapter<WifiAccessPoint> {
    public WifiAccessPointAdapter(Context context, ArrayList<WifiAccessPoint> accessPoints) {
        super(context, 0, accessPoints);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        WifiAccessPoint accessPoint = getItem(position);

        if(convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_wifi_access_point, parent, false);
        }

        if(accessPoint == null) {
            ((TextView)convertView.findViewById(R.id.AccessPointName)).setText("???");
            ((TextView)convertView.findViewById(R.id.AccessPointDbm)).setText("???");
            ((TextView)convertView.findViewById(R.id.AccessPointPw)).setText("???");
        } else {
            accessPoint.fillView((ConstraintLayout) convertView);
        }

        convertView.setBackgroundColor(getContext().getResources().getColor(R.color.colorContent));

        return convertView;
    }
}
