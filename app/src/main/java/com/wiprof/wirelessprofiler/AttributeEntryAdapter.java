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

public class AttributeEntryAdapter extends ArrayAdapter<AttributeEntry> {
    public AttributeEntryAdapter(Context context, ArrayList<AttributeEntry> attributeEntries) {
        super(context, 0, attributeEntries);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        AttributeEntry attributeEntry = getItem(position);

        if(convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_attribute_entry, parent, false);
        }

        attributeEntry.fillView((ConstraintLayout) convertView);
        convertView.setTag(position);

        return convertView;
    }
}
