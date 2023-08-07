package com.managexr.getserialsample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        MXRAdminAppMessenger mxrAdminAppMessenger = new MXRAdminAppMessenger(this);
        mxrAdminAppMessenger.onSerialUpdated((serial)->{
            TextView textView = findViewById(R.id.serial);
            textView.setText(serial);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }
}