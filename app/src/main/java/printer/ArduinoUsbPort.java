package printer;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ArduinoUsbPort extends CommonUsbSerialPort {

    private final String TAG = ArduinoUsbPort.class.getSimpleName();

    private final boolean mEnableAsyncReads;
    private UsbInterface mControlInterface;
    private UsbInterface mDataInterface;

    private UsbEndpoint mControlEndpoint;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;
    private UsbDevice mDevice;

    // What are these?
    private boolean mDtr = false;
    private boolean mRts = false;
    // And these
    private static final int USB_RECIP_INTERFACE = 0x01;
    private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;
    // perhaps these too
    private static final int SET_LINE_CODING = 0x20;  // USB CDC 1.1 section 6.2
    private static final int GET_LINE_CODING = 0x21;
    private static final int SET_CONTROL_LINE_STATE = 0x22;
    private static final int SEND_BREAK = 0x23;

    public ArduinoUsbPort(UsbDevice device, int portNumber) {
        super(device, portNumber);
        mDevice = device;
        mEnableAsyncReads = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1);
    }

    private int sendAcmControlMessage(int request, int value, byte[] buf) {
        return mConnection.controlTransfer(
                USB_RT_ACM, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }

    @Override
    public void open(UsbDeviceConnection connection) throws IOException {
        if (mConnection != null) {
            throw new IOException("Already open");
        }

        mConnection = connection;
        boolean opened = false;
        try {
            Log.d(TAG, "claiming interfaces, count=" + mDevice.getInterfaceCount());
            mControlInterface = mDevice.getInterface(0);
            Log.d(TAG, "Control interface=" + mControlInterface);
            // class should be USB_CLASS_COMM

            if (!mConnection.claimInterface(mControlInterface, true)) {
                throw new IOException("Could not claim control interface.");
            }
            mControlEndpoint = mControlInterface.getEndpoint(0);
            Log.d(TAG, "Control endpoint direction: " + mControlEndpoint.getDirection());

            Log.d(TAG, "Claiming data interface.");
            mDataInterface = mDevice.getInterface(1);
            Log.d(TAG, "data iface=" + mDataInterface);
            // class should be USB_CLASS_CDC_DATA

            if (!mConnection.claimInterface(mDataInterface, true)) {
                throw new IOException("Could not claim data interface.");
            }
            mReadEndpoint = mDataInterface.getEndpoint(1);
            Log.d(TAG, "Read endpoint direction: " + mReadEndpoint.getDirection());
            mWriteEndpoint = mDataInterface.getEndpoint(0);
            Log.d(TAG, "Write endpoint direction: " + mWriteEndpoint.getDirection());
            if (mEnableAsyncReads) {
                Log.d(TAG, "Async reads enabled.");
            } else {
                Log.d(TAG, "Async reads disabled.");
            }
            opened = true;
        } finally {
            if (!opened) {
                mConnection = null;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (mConnection == null) {
            throw new IOException("Already closed");
        }
        mConnection.close();
        mConnection = null;
    }

    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        if (false && mEnableAsyncReads) {
            final UsbRequest request = new UsbRequest();
            try {
                request.initialize(mConnection, mReadEndpoint);
                final ByteBuffer buf = ByteBuffer.wrap(dest);
                if (!request.queue(buf, dest.length)) {
                    throw new IOException("Error queueing request.");
                }
                final UsbRequest response = mConnection.requestWait();
                Log.d(TAG, "let me know 4");
                if (response == null) {
                    throw new IOException("Null response");
                }
                final int nread = buf.position();
                Log.d(TAG, "nread == "+nread);
                if (nread > 0) {
//						Log.d(TAG, HexDump.dumpHexString(dest, 0, Math.min(32, dest.length)));
                    return nread;
                } else {
                    Log.d(TAG, "nread == 0");
                    return 0;
                }
            } finally {
                Log.d(TAG, "Closing read request.");
                request.close();
            }
        }

        final int numBytesRead;
        synchronized (mReadBufferLock) {
            int readAmt = Math.min(dest.length, mReadBuffer.length);
            numBytesRead = mConnection.bulkTransfer(mReadEndpoint, mReadBuffer, readAmt,
                    timeoutMillis);
            if (numBytesRead < 0) {
                // This sucks: we get -1 on timeout, not 0 as preferred.
                // We *should* use UsbRequest, except it has a bug/api oversight
                // where there is no way to determine the number of bytes read
                // in response :\ -- http://b.android.com/28023
                if (timeoutMillis == Integer.MAX_VALUE) {
                    // Hack: Special case "~infinite timeout" as an error.
                    return -1;
                }
                return 0;
            }
            System.arraycopy(mReadBuffer, 0, dest, 0, numBytesRead);
        }
        return numBytesRead;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        // TODO(mikey): Nearly identical to FtdiSerial write. Refactor.
        int offset = 0;

        Log.d(TAG, "writing");

        while (offset < src.length) {
            final int writeLength;
            final int amtWritten;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                    writeBuffer = mWriteBuffer;
                }

                amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                        timeoutMillis);
            }
            if (amtWritten <= 0) {
//					throw new IOException("Error writing " + writeLength
//							+ " bytes at offset " + offset + " length=" + src.length);
                Log.e(TAG, "Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }

            Log.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            offset += amtWritten;
        }
        return offset;
    }

    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity) {
        byte stopBitsByte;
        switch (stopBits) {
            case STOPBITS_1: stopBitsByte = 0; break;
            case STOPBITS_1_5: stopBitsByte = 1; break;
            case STOPBITS_2: stopBitsByte = 2; break;
            default: throw new IllegalArgumentException("Bad value for stopBits: " + stopBits);
        }

        byte parityBitesByte;
        switch (parity) {
            case PARITY_NONE: parityBitesByte = 0; break;
            case PARITY_ODD: parityBitesByte = 1; break;
            case PARITY_EVEN: parityBitesByte = 2; break;
            case PARITY_MARK: parityBitesByte = 3; break;
            case PARITY_SPACE: parityBitesByte = 4; break;
            default: throw new IllegalArgumentException("Bad value for parity: " + parity);
        }

        byte[] msg = {
                (byte) ( baudRate & 0xff),
                (byte) ((baudRate >> 8 ) & 0xff),
                (byte) ((baudRate >> 16) & 0xff),
                (byte) ((baudRate >> 24) & 0xff),
                stopBitsByte,
                parityBitesByte,
                (byte) dataBits};
        sendAcmControlMessage(SET_LINE_CODING, 0, msg);
    }

    @Override
    public boolean getCD() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getCTS() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getDSR() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getDTR() throws IOException {
        return mDtr;
    }

    @Override
    public void setDTR(boolean value) throws IOException {
        mDtr = value;
        setDtrRts();
    }

    @Override
    public boolean getRI() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getRTS() throws IOException {
        return mRts;
    }

    @Override
    public void setRTS(boolean value) throws IOException {
        mRts = value;
        setDtrRts();
    }

    private void setDtrRts() {
        int value = (mRts ? 0x2 : 0) | (mDtr ? 0x1 : 0);
        sendAcmControlMessage(SET_CONTROL_LINE_STATE, value, null);
    }
}
