package com.wiprof.wirelessprofiler;

import android.support.constraint.ConstraintLayout;
import android.widget.TextView;

public class AttributeEntry {
    public String name;
    public String value;
    public String infoText;

    public AttributeEntry(String in_name, String in_value, String in_infoText) {
        name = in_name;
        value = in_value;
        infoText = in_infoText;
    }

    public void fillView(ConstraintLayout view) {
        ((TextView)view.findViewById(R.id.AttributeEntryName)).setText(name);
        ((TextView)view.findViewById(R.id.AttributeEntryValue)).setText(value);
    }
}
