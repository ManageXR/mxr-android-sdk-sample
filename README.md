# mxr-android-sdk-serial-sample

This project demonstrates how your app can communicate with the ManageXR Admin App to get your device's serial number. In order for this to work, your device must be managed by ManageXR.

## Try it!

Clone this repo, build it, and install it. Launch the app on your device and you'll see a 2D window that prints out your device's serial number.

## To add this functionality to your own project:

Add the following permission to your `AndroidManifest.xml`:

`<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`

Add the following module to your build.gradle:

`implementation 'org.apache.commons:commons-lang3:3.0'`

Copy the [MXRAdminAppMessenger.java](https://github.com/ManageXR/mxr-java-sdk-serial-sample/blob/master/app/src/main/java/com/managexr/getserialsample/MXRAdminAppMessenger.java) class into your project.

Get your device's serial number like this:
```
MXRAdminAppMessenger mxrAdminAppMessenger = new MXRAdminAppMessenger(this);
mxrAdminAppMessenger.onSerialUpdated((serial)->{
    TextView textView = findViewById(R.id.serial);
    textView.setText(serial);
});
```
Notes: 
- Getting the device's serial number is an asynchronous action because it requires communicating with the ManageXR Admin App via an IPC messenger. This project stores the serial number in SharedPreferences so the next time you query it, you'll get it immediately. 
- Read the [MXRAdminAppMessenger.java](https://github.com/ManageXR/mxr-java-sdk-serial-sample/blob/master/app/src/main/java/com/managexr/getserialsample/MXRAdminAppMessenger.java)  source code for other public methods that are unused in this example, but may be useful to you when you implement this in your own project. 
- Getting the device's serial number does not require internet.

