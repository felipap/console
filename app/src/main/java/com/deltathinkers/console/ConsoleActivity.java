package com.deltathinkers.console;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

import printer.Printer;

public class ConsoleActivity extends Activity {

    private Printer mPrinter;

    private final static String TAG = ConsoleActivity.class.getSimpleName();

    // private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);

        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);

        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);

        UsbManager usbManager = (UsbManager) getApplicationContext().getSystemService(Context.USB_SERVICE);

        UsbDevice device = Printer.findPrinter(this);
        if (device == null) {
            mTitleTextView.setText("Printer device not found.");
        }
        mTitleTextView.setText("Printer device found.");
        Log.d(TAG, "Found device" + device.getDeviceName());

        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "Has permission.");
            onHasPermission(device);
        } else {
            printConsole("No permissions. Attempting to get it now.\n");
            Log.e(TAG, "Has NO permission.");
            registerReceiver(mUsbReceiver, filter);
            usbManager.requestPermission(device, mPermissionIntent);
        }
//        mPrinter = new Printer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "RESUME");
        if (sPort == null) {

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIOManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                printConsole("ERROR: Failed to close sPort");
            }
            sPort = null;
        }
        finish();
    }

    private void onFailedPermission () {
        Toast.makeText(getBaseContext(),
                "Failed to get permission. wtf",
                Toast.LENGTH_SHORT)
                .show();
    }

    private void onHasPermission (UsbDevice device) {
        printConsole("Permissions found. :)\n");

    }

    private void printConsole (String text) {
        mDumpTextView.append(text);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_console, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "OOOOO");
        if (ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null){
                        //call method to set up device communication
                        onHasPermission(device);
                    }
                }
                else {
                    Log.d(TAG, "permission denied for device " + device);
                    onFailedPermission();
                }
            }
        }
        }
    };


}
