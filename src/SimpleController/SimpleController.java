package SimpleController;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineEvent.Type;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class SimpleController {

	// Serial setting
	public static final int SERIAL_PORT_RATE= 19200;
	
	// Status strings
	public static final String DOORWINDOW_SENSOR_OPEN = "01,0024,00";
	public static final String DOORWINDOW_SENSOR_CLOSE = "01,0025,00";

	public static final String KEYFOBE3_LOCK = "-3:arm mode 3";
	public static final String KEYFOBE3_UNLOCK = "-3:arm mode 0";
	public static final String KEYFOBE3_PANIC = "-3: panic...";

	public static final String MOTION_SENSOR_OCCUPIED = "-1: Got a zone status:34";
	public static final String MOTION_SENSOR_VACANT = "-1: Got a zone status:32";

	// Sound file pathes
	public static final String ALARM_SOUND_PATH = "leftright.wav";
	public static final String BEEP_SOUND_PATH = "leftright.wav";
	public static final String LOCK_SOUND_PATH = "leftright.wav";
	public static final String UNLOCK_SOUND_PATH = "leftright.wav";

	/*
	 * System status: 0: unlock; 1: lock;
	 */
	public static int lockStatus = 0;

	public SimpleController() {
		super();
	}

	void connect(String portName) throws Exception {
		CommPortIdentifier portIdentifier = CommPortIdentifier
				.getPortIdentifier(portName);
		if (portIdentifier.isCurrentlyOwned()) {
			System.out.println("Error: Port is currently in use");
		} else {
			CommPort commPort = portIdentifier.open(this.getClass().getName(),
					2000);

			if (commPort instanceof SerialPort) {
				SerialPort serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(SERIAL_PORT_RATE, SerialPort.DATABITS_8,
						SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

				InputStream in = serialPort.getInputStream();
				OutputStream out = serialPort.getOutputStream();

				(new Thread(new SerialReader(in))).start();
				(new Thread(new SerialWriter(out))).start();

			} else {
				System.out
						.println("Error: Only serial ports are handled by this example.");
			}
		}
	}

	/** */
	public static class SerialReader implements Runnable {
		InputStream in;

		public SerialReader(InputStream in) {
			this.in = in;
		}

		public void run() {
			byte[] buffer = new byte[1024];
			int len = -1;

			try {
				while ((len = this.in.read(buffer)) > -1) {
					// Here print out all serial data
					String serialBuffer = new String(buffer, 0, len);
					parseIasStatus(serialBuffer);
					serialBuffer = null;
					// System.out.print(new String(buffer, 0, len));

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (UnsupportedAudioFileException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (LineUnavailableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	/** */
	public static class SerialWriter implements Runnable {
		OutputStream out;

		public SerialWriter(OutputStream out) {
			this.out = out;
		}

		public void run() {
			try {
				int c = 0;
				while ((c = System.in.read()) > -1) {
					// Here put char to write to serial
					this.out.write(c);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void listPorts() {
		java.util.Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier
				.getPortIdentifiers();
		while (portEnum.hasMoreElements()) {
			CommPortIdentifier portIdentifier = portEnum.nextElement();
			System.out.println(portIdentifier.getName() + " - "
					+ getPortTypeName(portIdentifier.getPortType()));
		}
	}

	public static String getPortTypeName(int portType) {
		switch (portType) {
		case CommPortIdentifier.PORT_I2C:
			return "I2C";
		case CommPortIdentifier.PORT_PARALLEL:
			return "Parallel";
		case CommPortIdentifier.PORT_RAW:
			return "Raw";
		case CommPortIdentifier.PORT_RS485:
			return "RS485";
		case CommPortIdentifier.PORT_SERIAL:
			return "Serial";
		default:
			return "unknown type";
		}
	}

	public static void main(String[] args) {
		try {

			if (args == null || args.length != 2) {
				// help
				System.out.println("List of all ports available:");
				listPorts();
				System.out
						.println("\nTo run the program, please type in: java -jar SimpleController.jar start PortFromTheListAbove");
			} else if (args[0].equals("start")) {
				// start
				String port = args[1];
				(new SimpleController()).connect(port);
				System.out.println("Controller starts");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void parseIasStatus(String serialBuffer) throws IOException,
			UnsupportedAudioFileException, LineUnavailableException,
			InterruptedException {

		// Debug
		// System.out.print(serialBuffer);

		if (serialBuffer.contains(DOORWINDOW_SENSOR_OPEN)
				|| serialBuffer.contains(MOTION_SENSOR_OCCUPIED)) {
			if (isSystemLocked()) {
				startAlarm();
			}else{
				playBeepSound();
			}
		} else if (serialBuffer.contains(KEYFOBE3_LOCK)) {
			lockSystem();
		} else if (serialBuffer.contains(KEYFOBE3_UNLOCK)) {
			unlockSystem();
		} else if (serialBuffer.contains(KEYFOBE3_PANIC)) {
			startPanic();
		}

		serialBuffer = null;
	}

	private static void startAlarm() {
		playAlarmSound();
		System.out.println("[" + getTime() + "]" + "  System Alarmed");
	}

	private static void startPanic() {
		playAlarmSound();
		System.out.println("[" + getTime() + "]" + "  System Panic");
	}

	private static void lockSystem() {
		lockStatus = 1;
		System.out.println("[" + getTime() + "]" + "  System Locked");
		playLockSound();
	}

	private static void unlockSystem() {
		lockStatus = 0;
		System.out.println("[" + getTime() + "]" + "  System Unlocked");
		playUnlockSound();
	}

	private static boolean isSystemLocked() {
		if (lockStatus == 1)
			return true;
		else
			return false;
	}

	private static String getTime() {
		Calendar cal = Calendar.getInstance();
		cal.getTime();
		SimpleDateFormat sdf = new SimpleDateFormat(
				"EEE, d MMM yyyy HH:mm:ss Z");
		return sdf.format(cal.getTime());
	}

	private static synchronized void playAlarmSound() {
		new Thread(new Runnable() {
					public void run() {
						try {
							playClip(new File(ALARM_SOUND_PATH));
						} catch (Exception e) {
							System.err.println(e.getMessage());
						}
					}
				}).start();
	}
	
	private static synchronized void playBeepSound() {
		new Thread(new Runnable() {
					public void run() {
						try {
							playClip(new File(BEEP_SOUND_PATH));
							System.out.println("[" + getTime() + "]" + "  Playing sound");
						} catch (Exception e) {
							System.err.println(e.getMessage());
						}
					}
				}).start();
	}
	
	private static synchronized void playLockSound() {
		new Thread(new Runnable() {
					public void run() {
						try {
							playClip(new File(LOCK_SOUND_PATH));
						} catch (Exception e) {
							System.err.println(e.getMessage());
						}
					}
				}).start();
	}
	
	private static synchronized void playUnlockSound() {
		new Thread(new Runnable() {
					public void run() {
						try {
							playClip(new File(UNLOCK_SOUND_PATH));
						} catch (Exception e) {
							System.err.println(e.getMessage());
						}
					}
				}).start();
	}

	private static void playClip(File clipFile) throws IOException,
			UnsupportedAudioFileException, LineUnavailableException,
			InterruptedException {
		class AudioListener implements LineListener {
			private boolean done = false;

			@Override
			public synchronized void update(LineEvent event) {
				Type eventType = event.getType();
				if (eventType == Type.STOP || eventType == Type.CLOSE) {
					done = true;
					notifyAll();
				}
			}

			public synchronized void waitUntilDone()
					throws InterruptedException {
				while (!done) {
					wait();
				}
			}
		}
		AudioListener listener = new AudioListener();
		AudioInputStream audioInputStream = AudioSystem
				.getAudioInputStream(clipFile);
		try {
			Clip clip = AudioSystem.getClip();
			clip.addLineListener(listener);
			clip.open(audioInputStream);
			try {
				clip.start();
				listener.waitUntilDone();
			} finally {
				clip.close();
			}
		} finally {
			audioInputStream.close();
		}
	}
}
