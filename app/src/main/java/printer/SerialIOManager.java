package printer;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SerialIOManager implements Runnable {

	private static final String TAG = SerialIOManager.class.getSimpleName();
	private static final boolean DEBUG = true;

	private static final int READ_WAIT_MILLIS = 200;
	private static final int BUFSIZ = 4096;

	private final ArduinoUsbPort mPort;

	private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);

	// Synchronized by 'mWriteBuffer'
	private final ByteBuffer mWriteBuffer = ByteBuffer.allocate(BUFSIZ);

	private enum State {
		STOPPED,
		RUNNING,
		STOPPING
	}

	// Synchronized by 'this'
	private State mState = State.STOPPED;

	// Synchronized by 'this'
	private Listener mListener;

	public interface Listener {
		/**
		 * Called when new incoming data is available.
		 */
		public void onNewData(byte[] data);

		/**
		 * Called when {@link SerialIOManager#run()} aborts due to an
		 * error.
		 */
		public void onRunError(Exception e);
	}

	/**
	 * Creates a new instance with no listener.
	 */
	public SerialIOManager(ArduinoUsbPort driver) {
		this(driver, null);
	}

	/**
	 * Creates a new instance with the provided listener.
	 */
	public SerialIOManager(ArduinoUsbPort port, Listener listener) {
		mPort = port;
		mListener = listener;

		Log.d(TAG, "thread");
		new Thread(new Runnable() {
			@Override
			public void run() {
				Log.d(TAG, "run");
				try {
					int len = mPort.read(mReadBuffer.array(), READ_WAIT_MILLIS);
					if (len > 0) {
						if (DEBUG) Log.d(TAG, "Read data len=" + len);
						final Listener listener = getListener();

						if (listener != null) {
							final byte[] data = new byte[len];
							mReadBuffer.get(data, 0, len);
							listener.onNewData(data);
							Log.d(TAG, "\""+new String(data, "ASCII")+"\"");
						}

						mReadBuffer.clear();
					}
				} catch (IOException e) {
					Log.e(TAG, "IOException");
				}
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				run();
			}
		}).start();
	}

	public synchronized void setListener(Listener listener) {
		mListener = listener;
	}

	public synchronized Listener getListener() {
		return mListener;
	}

	public void writeAsync(byte[] data) {
		synchronized (mWriteBuffer) {
			mWriteBuffer.put(data);
		}
	}

	public synchronized void stop() {
		if (getState() == State.RUNNING) {
			Log.i(TAG, "Stop requested");
			mState = State.STOPPING;
		}
	}

	private synchronized State getState() {
		return mState;
	}

	/**
	 * Continuously services the read and write buffers until {@link #stop()} is
	 * called, or until a driver exception is raised.
	 *
	 * NOTE(mikey): Uses inefficient read/write-with-timeout.
	 * TODO(mikey): Read asynchronously with {@link android.hardware.usb.UsbRequest#queue(ByteBuffer, int)}
	 */
	@Override
	public void run() {
//		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

		synchronized (this) {
			if (getState() != State.STOPPED) {
				throw new IllegalStateException("Already running.");
			}
			mState = State.RUNNING;
		}

		Log.i(TAG, "Running ..");
		try {
			while (true) {
				if (getState() != State.RUNNING) {
					Log.i(TAG, "Stopping mState=" + getState());
					break;
				}
				step();

				// Handle incoming data.
			}
		} catch (Exception e) {
			Log.w(TAG, "Run ending due to exception: " + e.getMessage(), e);
			final Listener listener = getListener();
			if (listener != null) {
				listener.onRunError(e);
			}
		} finally {
			synchronized (this) {
				mState = State.STOPPED;
				Log.i(TAG, "Stopped.");
			}
		}
	}

	private int l = 0;

	private void step() throws IOException {
		// Handle outgoing data.
		byte[] outBuff = null;
		synchronized (mWriteBuffer) {

			l++;
			if (l%1000000 == 0) Log.d(TAG, "step "+l);

			if (mWriteBuffer.position() > 0) {
				Log.d(TAG, "mWriteBuffer not empty");
				int len = mWriteBuffer.position();
				outBuff = new byte[len];
				mWriteBuffer.rewind();
				mWriteBuffer.get(outBuff, 0, len);
				mWriteBuffer.clear();
			}
		}
		if (outBuff != null) {
			if (DEBUG) {
				Log.d(TAG, "Writing data \""+ new String(outBuff, "ASCII") + "\"");
			}
			Log.d(TAG, "Writing outBuff");
			mPort.write(outBuff, READ_WAIT_MILLIS);
		}
	}

}
