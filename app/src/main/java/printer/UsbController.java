package printer;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

public class UsbController {

	private final Printer.IConnectionHandler cHandler;
	private final UsbDeviceConnection mConn;
	private final UsbDevice mDevice;

	private final static String TAG = UsbController.class.getSimpleName();

    public UsbController(UsbDevice device,
						 UsbDeviceConnection conn,
						 Printer.ConnectionHandler handler) {
		cHandler = handler;
		mDevice = device;
		mConn = conn;

		start();
    }

	private UsbRunnable mLoop;
	private Thread mUsbThread;

	private void start() {
		if (mLoop != null) {
			cHandler.onErrorLooperRunningAlready();
			return;
		}

		mLoop = new UsbRunnable(mDevice);
		mUsbThread = new Thread(mLoop);
		mUsbThread.start();

		cHandler.onUsbStarted();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				send((byte) 0x80);
				run();
			}
		}).start();
	}

    public void stop() {
    }

    public void send(byte data) {
		mData = data;
		synchronized (sSendLock) {
			sSendLock.notify();
		}
    }

    public void receive() {
    }

	//learned this trick from some google example :)
	//basically an empty array is lighter than an  actual new Object()...
	private static final Object[] sSendLock = new Object[]{};
	private boolean mStop = false;
	private byte mData = 0x00;

	private class UsbRunnable implements Runnable {
		private final UsbDevice mDevice;

		UsbRunnable(UsbDevice dev) {
			mDevice = dev;
		}

		@Override
		public void run() {
			// android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

			if (!mConn.claimInterface(mDevice.getInterface(1), true)) {
				Log.e(TAG, "Couldn't claim interface.");
				return;
			}
			// Arduino Serial usb conv
			mConn.controlTransfer(0x21, 34, 0, 0, null, 0, 0);
			mConn.controlTransfer(0x21, 32, 0, 0, new byte[]{(byte) 0x80,
					0x25, 0x00, 0x00, 0x00, 0x00, 0x08}, 7, 0);

			UsbEndpoint epIN = null;
			UsbEndpoint epOUT = null;

			UsbInterface usbIf = mDevice.getInterface(1);
			for (int i=0; i<usbIf.getEndpointCount(); i++) {
				if (usbIf.getEndpoint(i).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
					if (usbIf.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_IN)
						epIN = usbIf.getEndpoint(i);
					else
						epOUT = usbIf.getEndpoint(i);
				}
			}

			Log.d(TAG, "calimed epIN epOut");

			for (;;) {
				synchronized (sSendLock) {
					try {
						sSendLock.wait();
					} catch (InterruptedException e) {
						if (mStop) {
							cHandler.onUsbStopped();
							return;
						}
						e.printStackTrace();
					}
					Log.d(TAG, "Apimentando");
					mConn.bulkTransfer(epOUT, new byte[]{mData}, 1, 0);

					if (mStop) {
						cHandler.onUsbStopped();
						return;
					}
				}
			}
		}
	}

}