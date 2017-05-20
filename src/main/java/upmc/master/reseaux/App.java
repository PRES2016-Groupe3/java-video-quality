package upmc.master.reseaux;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class App {

	// AR.Drone 1.0

	private static final int PORT = 5555;
	private static final int BUFFER_SIZE = 100 * 1024;
	private static final byte[] DRONE_IP = { (byte) 192, (byte) 168, (byte) 1, (byte) 1 };
	private static final byte[] TRIGGER_BYTES = { 0x01, 0x00, 0x00, 0x00 };
	private static final int DRONE_TIMEOUT = 100;
	private static final int DRONE_CONNECT_TIMEOUT = 2000;

	// GoPro

	private static final String GO_PRO_IP = "10.5.5.9";
	private static final int GO_PRO_PORT = 8080;
	private static final int GO_PRO_TIMEOUT = 5000;

	// Cli

	private static Camera camera;
	private static String fileName;
	private static int duration;
	private static final int DEFAULT_DURATION = 10;

	// Metrics

	private static int receivedFrames;
	private static int lostFrames;
	private static int lostPackets;
	private static int sentPackets;
	private static long startTime;

	private static final String HORIZONTAL_LINE = "------------------------------------------------------------------------";

	public static void main(String[] args) {
		final String horizontalLine = "----------------------";

		CommandLineParser parser = new DefaultParser();

		String header = System.lineSeparator() + "Options"

		+ System.lineSeparator() + System.lineSeparator();

		String footer = System.lineSeparator()

		+ "Options a or g are mandatory";
		HelpFormatter formatter = new HelpFormatter();

		Options options = new Options();

		options.addOption("h", "help", false, "Display this help and exit" + System.lineSeparator() + horizontalLine);

		Option a = Option.builder("a").longOpt("ar-drone").hasArg(false).desc(

		"AR.Drone 1.0" + System.lineSeparator() + horizontalLine)

		.build();

		Option g = Option.builder("g").longOpt("go-pro").hasArg(false)
				.desc("GoPro" + System.lineSeparator() + horizontalLine).build();

		Option f = Option.builder("f").longOpt("filename").hasArg(true).argName("filename")

		.desc("Filename " + System.lineSeparator() + horizontalLine)

		.build();

		Option d = Option.builder("d").longOpt("duration").hasArg(true).argName("time")

		.desc("Duration (in seconds)" + System.lineSeparator() + horizontalLine)

		.build();

		options.addOption(a);

		options.addOption(g);

		options.addOption(f);

		options.addOption(d);

		formatter.setSyntaxPrefix(System.lineSeparator() + "Syntax: ");

		CommandLine line;
		try {

			line = parser.parse(options, args);

			if (line.hasOption("h")) {

				formatter.printHelp("java-video-quality", header, options, footer, true);

				return;

			}

			if (line.hasOption("a")) {
				camera = Camera.AR_DRONE;
			} else if (line.hasOption("g")) {
				camera = Camera.GO_PRO;
			} else {
				formatter.printHelp("java-video-quality", header, options, footer, true);
				return;
			}

			if (line.hasOption("f")) {
				fileName = line.getOptionValue("f");
			} else {
				fileName = defaultFileName();
			}
			if (line.hasOption("d")) {
				duration = Integer.parseInt(line.getOptionValue("d"));
			}

			if (duration <= 0) {
				duration = DEFAULT_DURATION;
			}

			System.out.println("Connecting to " + camera.getName() + "...");
			TimeUnit.SECONDS.sleep(5);
			switch (camera) {
			case AR_DRONE:
				evaluateARDrone();
				break;
			case GO_PRO:
				evaluateGoPro();
				break;
			}

		} catch (ParseException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	private static void evaluateARDrone() {

		byte[] buf = new byte[BUFFER_SIZE];

		InetAddress droneAddr = null;

		try {
			droneAddr = InetAddress.getByAddress(DRONE_IP);
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		DatagramPacket in = new DatagramPacket(buf, buf.length, droneAddr, PORT);
		DatagramPacket out = new DatagramPacket(TRIGGER_BYTES, TRIGGER_BYTES.length, droneAddr, PORT);

		try (DatagramSocket socket = new DatagramSocket(PORT);) {

			socket.setSoTimeout(DRONE_CONNECT_TIMEOUT);
			socket.send(out);
			sentPackets++;
			socket.receive(in);
			receivedFrames++;
		} catch (SocketTimeoutException e) {
			connectionFailed();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Evaluation in progress...");

		scheduleTime();

		try (DatagramSocket socket = new DatagramSocket(PORT);) {
			socket.setSoTimeout(DRONE_TIMEOUT);
			while (true) {

				sendAndWait(socket, in, out);
				receivedFrames++;

			}
		} catch (SocketException e) {
			e.printStackTrace();
		}

	}

	private static void sendAndWait(DatagramSocket socket, DatagramPacket in, DatagramPacket out) {
		try {
			socket.send(out);
			sentPackets++;
			socket.receive(in);
		} catch (SocketTimeoutException e) {
			lostPackets++;
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void evaluateGoPro() {
		int nextSeq = 0;
		int formerSeq = 0;
		HttpURLConnection hurl = null;
		String line = null;

		try {
			hurl = (HttpURLConnection) new URL("http://" + GO_PRO_IP + ":" + GO_PRO_PORT + "/live/amba.m3u8")
					.openConnection();
			hurl.setConnectTimeout(GO_PRO_TIMEOUT);
			hurl.setReadTimeout(GO_PRO_TIMEOUT);

			InputStream is = hurl.getInputStream();
			while (is.read() != -1)
				;

		} catch (SocketTimeoutException | NoRouteToHostException e) {
			connectionFailed();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Evaluation in progress...");

		scheduleTime();

		while (true) {
			long loadPlaylistTime = System.currentTimeMillis();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(loadPlaylist(hurl)));) {

				int newSeq = -1;
				int targetDuration = -1;
				int firstSeqment;

				while (newSeq == -1 || targetDuration == -1) {
					line = reader.readLine();
					if ((line.startsWith("#EXT-X-TARGETDURATION:"))) {
						targetDuration = Integer.parseInt(line.replaceAll("[^0-9]", ""));

					} else if ((line.startsWith("#EXT-X-MEDIA-SEQUENCE:"))) {
						newSeq = Integer.parseInt(line.replaceAll("[^0-9]", ""));
					}

				}

				firstSeqment = nextSeq - newSeq;

				if (nextSeq != 0 && firstSeqment < 0) {
					lostFrames += -firstSeqment * 8;
					nextSeq = newSeq;
				}

				nextSeq = (nextSeq == 0) ? newSeq : nextSeq;

				while (firstSeqment > 0) {
					if (!(reader.readLine().startsWith("#"))) {
						firstSeqment--;
					}
				}
				while ((line = reader.readLine()) != null) {
					if (!line.startsWith("#")) {

						InputStream is = loadMediaSegment(hurl, line);

						while (is.read() != -1)
							;

						nextSeq++;

						receivedFrames += 8;
					}
				}

				if (formerSeq == newSeq) {
					TimeUnit.SECONDS.sleep(targetDuration / 2);
				} else {
					TimeUnit.MILLISECONDS
							.sleep(targetDuration * 1000 - (System.currentTimeMillis() - loadPlaylistTime));
				}
				formerSeq = newSeq;
			} catch (SocketTimeoutException e) {
				System.out.println("Connection lost");
				writeMetricsAndExit();
			}

			catch (IOException e) {

				e.printStackTrace();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}

		}
	}

	private static InputStream loadPlaylist(HttpURLConnection hurl) throws IOException {
		hurl = (HttpURLConnection) new URL("http://" + GO_PRO_IP + ":" + GO_PRO_PORT + "/live/amba.m3u8")
				.openConnection();
		hurl.setReadTimeout(GO_PRO_TIMEOUT);
		return hurl.getInputStream();
	}

	private static InputStream loadMediaSegment(HttpURLConnection hurl, String line) throws IOException {
		hurl = (HttpURLConnection) new URL("http://" + GO_PRO_IP + ":" + GO_PRO_PORT + "/live/" + line)
				.openConnection();
		hurl.setReadTimeout(GO_PRO_TIMEOUT);
		return hurl.getInputStream();
	}

	private static String defaultFileName() {
		Calendar calendar = Calendar.getInstance();

		return camera + "_" + "[" + String.format("%02d", calendar.get(Calendar.DATE)) + "-"
				+ String.format("%02d", calendar.get(Calendar.MONTH)) + "-" + calendar.get(Calendar.YEAR) + "#"
				+ String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)) + ":"
				+ String.format("%02d", calendar.get(Calendar.MINUTE)) + ":"
				+ String.format("%02d", calendar.get(Calendar.SECOND)) + "]";

	}

	private static void writeMetricsToFile() {
		try (PrintWriter pw = new PrintWriter(new File(fileName));) {
			StringBuilder sb = new StringBuilder();
			String name = camera.getName();
			for (int i = 0; i < name.length(); i++) {
				sb.append("=");
			}
			String doubleLine = sb.toString();

			pw.println(doubleLine);
			pw.println(camera.getName());
			pw.println(doubleLine);

			pw.println();
			pw.println("Duration (expected): " + duration + " s");
			pw.println("Duration (actual): "
					+ String.format("%.2f", (float) (System.currentTimeMillis() - startTime) / 1000) + " s");
			pw.println("Received frames: " + receivedFrames);
			switch (camera) {
			case AR_DRONE:
				pw.println("Lost packets: " + lostPackets);
				pw.println("Sent packets: " + sentPackets);
				break;
			case GO_PRO:
				pw.println("Lost frames: " + lostFrames);
				break;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void writeMetricsToStandardOutput() {

		System.out.println("Duration (expected): " + duration + " s");
		System.out.println("Duration (actual): "
				+ String.format("%.2f", (float) (System.currentTimeMillis() - startTime) / 1000) + " s");
		System.out.println("Received frames: " + receivedFrames);
		switch (camera) {
		case AR_DRONE:
			System.out.println("Lost packets: " + lostPackets);
			System.out.println("Sent packets: " + sentPackets);
			break;
		case GO_PRO:
			System.out.println("Lost frames: " + lostFrames);
			break;
		}

	}

	private static void scheduleTime() {
		startTime = System.currentTimeMillis();
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				writeMetricsAndExit();

			}

		}, duration * 1000);
	}

	private static void writeMetricsAndExit() {
		title("SUCCESS");
		writeMetricsToFile();
		writeMetricsToStandardOutput();
		System.out.println(HORIZONTAL_LINE);
		System.exit(0);
	}

	private static void connectionFailed() {
		title("FAILURE");
		System.err.println("Connection failed");
		System.out.println(HORIZONTAL_LINE);
		System.exit(-1);
	}

	public static void title(String title) {

		System.out.println(HORIZONTAL_LINE);
		System.out.println(title);
		System.out.println(HORIZONTAL_LINE);

	}

}
