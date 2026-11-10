package com.example.bai1.gmaps01;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Configuration artifact only: never starts Maps, GPS, auth or backend services. */
public final class ConfigurationActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setPadding(32, 80, 32, 32);
        text.setTextSize(20);
        text.setText("P5-E10-M01 — CONFIGURATION ONLY\n\n"
            + "BLOCKED: missing authorized demo Maps API key.\n"
            + "No Google Map or tiles have been loaded.\n\n"
            + "com.example.bai1.p5e10maps.demo\n\n"
            + "F06 OPEN\nSTRICT_ROUTING_DISABLED = true");
        setContentView(text);
    }
}
