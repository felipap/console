package com.deltathinkers.console;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import printer.Printer;
import printer.ArduinoUsbPort;
import printer.SerialIOManager;
import printer.HexDump;

public class ConsoleActivity extends Activity {

    private Printer mPrinter;

    private final static String TAG = ConsoleActivity.class.getSimpleName();

    // private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;
    private EditText mEditText;
    private Button mSendButton;

    private ArduinoUsbPort sPort = null;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialIOManager mSerialIoManager;

    private final SerialIOManager.Listener mListener =
        new SerialIOManager.Listener() {

            @Override
            public void onRunError(Exception e) {
                Log.d(TAG, "Runner stopped.");
            }

            @Override
            public void onNewData(final byte[] data) {
                ConsoleActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ConsoleActivity.this.updateReceivedData(data);
                    }
                });
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_console);

        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        mSendButton = (Button) findViewById(R.id.sendButton);
        mEditText = (EditText) findViewById(R.id.editText);

        mSendButton.setOnClickListener(new View.OnClickListener () {
            public void onClick(View v) {
                String result = mEditText.getText().toString();
                if (!result.isEmpty()) {
                    try {
                        sendMessage(result);
                        mEditText.setText("");
                    } catch(IOException e) {
                    }
                }
            }
        });

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "RESUME");
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDevice());
            if (connection == null) {
                mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, ArduinoUsbPort.STOPBITS_1, ArduinoUsbPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void sendMessage(String message) throws IOException {
        if (mSerialIoManager == null) {
            printConsole("Failed to send message. No Serial Io Manager instantiated.\n");
        } else {
            printConsole("(sent): "+message+(message.endsWith("\n")?"":"\n"));
            mSerialIoManager.writeAsync(message.getBytes(Charset.forName("UTF-8")));
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialIOManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        printConsole("Read " + data.length + " bytes: \n" + HexDump.dumpHexString(data) + "\n\n");
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
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
        sPort = new ArduinoUsbPort(device, 0);
    }

    //

    private void printConsole (String text) {
        mDumpTextView.append(text);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    //


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

    // get USB permission

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Returned from Broadcast: "+action);
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
