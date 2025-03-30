package com.managexr.getserialsample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    MXRAdminAppMessenger mxrAdminAppMessenger;

    // TODO: Change this to the package name of the app you want to set as the kiosk app.
    static final private String KIOSK_APP_A = "com.bodyswaps.bodyswap";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         mxrAdminAppMessenger = new MXRAdminAppMessenger(this);
        setContentView(R.layout.activity_main);
        mxrAdminAppMessenger.onSerialUpdated((serial) -> {
            TextView textView = findViewById(R.id.serial);
            textView.setText(serial);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    public void onSetKioskAppAClick(View view) {
        mxrAdminAppMessenger.overrideKioskAppAsync(KIOSK_APP_A);
    }

    public void onClearKioskOverrideClick(View view) {
        mxrAdminAppMessenger.overrideKioskAppAsync(null);
    }
}
