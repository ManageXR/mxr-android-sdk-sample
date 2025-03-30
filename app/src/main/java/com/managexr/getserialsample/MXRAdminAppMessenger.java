/*
 * Copyright 2023 Mighty Immersion, Inc. All Rights Reserved.
 */
package com.managexr.getserialsample;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;

import java.util.List;

public class MXRAdminAppMessenger {
    public static class AdminAppMessageTypes {
        public static final int REGISTER_CLIENT = 0;
        public static final int GET_DEVICE_STATUS = 5;

        public static final int OVERRIDE_KIOSK_APP = 20;
        public static final int DEVICE_STATUS = 5000;
    }

    public interface BindStatusListener {
        void onBindStatusChanged(boolean bound);
    }

    public interface SerialListener {
        void onSerialUpdated(String serial);
    }

    private final static String TAG = "MXRAdminAppMessenger";
    private final static String ADMIN_SERVICE_CLASS_NAME = "com.mightyimmersion.mightyplatform.AdminService";

    private final Messenger incomingMessenger = new Messenger(new IncomingMessageHandler());
    private Messenger outgoingMessenger;
    private boolean bound;
    private final Context context;
    private BindStatusListener bindStatusListener;
    private SerialListener serialListener;

    private final int CHECK_BINDING_FREQUENCY = 60 * 1000; // 1 minute
    private final Handler checkBindingHandler = new Handler();

    public MXRAdminAppMessenger(Context _context) {
        context = _context;
        startBindToAdminServiceLoop();
    }

    /**
     * Sets a listener that will be called when the MXRAdminAppMessenger's bind status changes.
     * If the ManageXRMessenger is bound, then this app is able to communicate with the ManageXR
     * Admin App.
     */
    public void onBindStatusUpdated(BindStatusListener _listener) {
        bindStatusListener = _listener;
    }

    /**
     * Sets a listener that will be called as soon as the device's serial number is known.
     */
    public void onSerialUpdated(SerialListener _listener) {
        serialListener = _listener;
        String knownSerial = getSerial();
        if (knownSerial != null) serialListener.onSerialUpdated(knownSerial);
    }

    /**
     * @return true if this device is managed by ManageXR
     */
    public boolean isManaged() {
        return getInstalledAdminAdminServiceComponent() != null;
    }

    /**
     * @return true if this app is bound to the ManageXR Admin App
     */
    public boolean isBound() {
        return bound;
    }

    /**
     * Gets the device's serial number. This may be null if this app has never queried the ManageXR
     * Admin App for the device's serial number in the past. It is recommended to use the async
     * onSerialUpdated method instead.
     * @return serial number of device
     */
    public String getSerial() {
        return getSharedPrefs().getString("serial", null);
    }

    /**
     * If the provided serial number is different than what we already have stored locally, then
     * update the serial number saved to disk and send a message to the serialListener.
     */
    private void updateSerial(@NonNull String serial) {
        String currentSerial = getSerial();
        if (serial.equals(currentSerial)) return;

        SharedPreferences prefs = getSharedPrefs();
        SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString("serial", serial);
        prefsEditor.apply();

        serialListener.onSerialUpdated(serial);
    }

    /**
     * Helper function to get a scoped SharedPreferences object.
     */
    private SharedPreferences getSharedPrefs() {
        return context.getSharedPreferences(context.getPackageName() + ".managexr_prefs", Context.MODE_PRIVATE);
    }

    /**
     * This callback handles all messages from the ManageXR Admin App. When the Admin App knows the
     * device's serial number, this handler is called with the serial number stored in a JSON string.
     */
    class IncomingMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                Bundle bundle = msg.getData();
                if (msg.what == AdminAppMessageTypes.DEVICE_STATUS) {
                    String deviceStatusJsonString = bundle.getString("json", null);
                    if (deviceStatusJsonString == null) return;
                    JSONObject deviceStatusJson = new JSONObject(removeQuotesAndUnescape(deviceStatusJsonString));
                    String serial = deviceStatusJson.getString("serial");
                    if (serial.length() > 0) updateSerial(serial);
                }
            } catch (Exception e) {
                Log.v(TAG, "Failed to parse json: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String removeQuotesAndUnescape(String uncleanJson) {
        String noQuotes = uncleanJson.replaceAll("^\"|\"$", "");
        return StringEscapeUtils.unescapeJava(noQuotes);
    }

    ///
    // Code below this section is for binding to and maintaining communication with the ManageXR
    // Admin App.
    ///

    public void startBindToAdminServiceLoop() {
        bindToAdminServiceLoop();
    }

    private void bindToAdminServiceLoop() {
        tryBindToAdminService();
        checkBindingHandler.postDelayed(this::bindToAdminServiceLoop, CHECK_BINDING_FREQUENCY);
    }

    private void tryBindToAdminService() {
        if (bound) return;
        Log.v(TAG, "tryBindToAdminService");

        ComponentName adminServiceComponent = getInstalledAdminAdminServiceComponent();
        if (adminServiceComponent != null) {
            launchAdminAppServiceIfNeeded(adminServiceComponent.getPackageName());
            Intent bindIntent = new Intent();
            bindIntent.setComponent(adminServiceComponent);
            // This will bind to the service whether or not it is running. As soon as the service is
            // started the onServiceConnected method will fire.
            context.bindService(bindIntent, mConnection, 0);
        } else {
            Log.v(TAG, "ManageXR Admin App not installed or QUERY_ALL_PACKAGES permissions was not added to the manifest!");
        }
    }

    private void unbindFromAdminService() {
        context.unbindService(mConnection);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            outgoingMessenger = new Messenger(service);
            bound = true;
            boolean registeredAsClient = registerAsClient();
            if (registeredAsClient) {
                Log.v(TAG, "Registered as client");
                if (bindStatusListener != null) bindStatusListener.onBindStatusChanged(true);
                getDeviceStatusAsync();
            } else {
                Log.e(TAG, "Failed to register as client. Unbinding...");
                unbindFromAdminService();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.v(TAG, "onServiceDisconnected");
            outgoingMessenger = null;
            bound = false;
            if (bindStatusListener != null) bindStatusListener.onBindStatusChanged(false);
        }
    };

    private boolean registerAsClient() {
        return sendMessage(AdminAppMessageTypes.REGISTER_CLIENT);
    }

    public boolean getDeviceStatusAsync() {
        return sendMessage(AdminAppMessageTypes.GET_DEVICE_STATUS);
    }

    public boolean overrideKioskAppAsync(String packageName) {
        return sendMessage(AdminAppMessageTypes.OVERRIDE_KIOSK_APP, "{\"packageName\":\"" + packageName + "\"}");
    }

    public boolean sendMessage(int what) {
        return sendMessage(what, null);
    }

    public boolean sendMessage(int what, String jsonString) {
        if (!bound) {
            tryBindToAdminService();
            return false;
        }

        Message msg = Message.obtain(null, what);
        msg.replyTo = incomingMessenger;

        if (jsonString != null) {
            Bundle bundle = new Bundle();
            bundle.putString("json", jsonString);
            msg.setData(bundle);
        }

        try {
            outgoingMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    private ComponentName getInstalledAdminAdminServiceComponent() {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);

        for (PackageInfo packageInfo : packages) {
            if (packageInfo.packageName.startsWith("com.mightyimmersion.mightyplatform.adminapp") && !packageInfo.packageName.contains("preload")) {
                return new ComponentName(packageInfo.packageName, ADMIN_SERVICE_CLASS_NAME);
            }
        }
        return null;
    }

    private void launchAdminAppServiceIfNeeded(String packageName) {
        Intent intent = new Intent();
        intent.setClassName(packageName, ADMIN_SERVICE_CLASS_NAME);
        context.startForegroundService(intent);
    }
}
