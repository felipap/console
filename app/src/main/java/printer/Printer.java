package printer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class Printer {

    private static final String TAG = Printer.class.getSimpleName();

    // Id. for arduino (0x0043 for uno)
    private static final int VID = 0x2341;
    private static final int PID = 0x0042;

    public Printer(Activity activity) {


    }

    public static UsbDevice findPrinter(Activity activity) {


        Intent intent = activity.getIntent();
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (device != null)
            return device;

        // Device is null because:
        // - it isn't connected
        // - its intent isn't "attached" to this app

        UsbManager usbManager = (UsbManager) activity
                .getApplicationContext()
                .getSystemService(Context.USB_SERVICE);

        Log.d(TAG, "No usb attached to intent. Crawling available usb devices.");

        for (final UsbDevice d : usbManager.getDeviceList().values()) {
            Log.d(TAG, "Device found: " + String.format("%04X:%04X",
                    d.getVendorId(), d.getProductId()));

            if (d.getVendorId() == VID && d.getProductId() == PID) {
                Log.d(TAG, "Device is printer.");
                return d;

            }
        }
        return null;
    }

}
