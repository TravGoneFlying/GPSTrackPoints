package com.traviscons.GPSTrackPoints.backend;

/* GPSTrackpointsRun
 * Copyright 2018 Travis Marlatte
 *
 * Derived from GPSd4Java
 * Copyright (C) 2011 - 2012 Taimos GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import java.time.LocalDateTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.lang.Runtime;

import java.io.RandomAccessFile;
import java.io.IOException;

import com.traviscons.GPSTrackPoints.api.ObjectListener;
import com.traviscons.GPSTrackPoints.types.ATTObject;
import com.traviscons.GPSTrackPoints.types.DeviceObject;
import com.traviscons.GPSTrackPoints.types.DevicesObject;
import com.traviscons.GPSTrackPoints.types.SATObject;
import com.traviscons.GPSTrackPoints.types.SKYObject;
import com.traviscons.GPSTrackPoints.types.TPVObject;
import com.traviscons.GPSTrackPoints.types.subframes.SUBFRAMEObject;

/**
 * Initialize and execute a listener for GPSd.
 *
 * Use DateFormat to establish the desired presentation of the GPS timestamp<br>
 * use &quot;yyyy-MM-dd'T'HH:mm:ss.SSS'Z'&quot;
 * </p>
 * This class contains a main() that sets up the rest of the system. Then sleeps.
 * </p>
 * A GPSdEndpoint is created and provided an ObjectListener and a ResultParser
 * </p>
 * The GPX output file is created and the header information is written.
 * The file name is fixed.
 * </p>
 * The GPSdEndpoint launches a thread to listen for messages and dispatch them through
 * the ObjectListener and ResultParser.
 *
 * \todo The GPX output file name should use a date stamp.
 *
 * \todo if the Shared folder doesn't already exist, create it
 *
 * \author Travis Marlatte
 * \copyright &copy; Copyright 2018 Travis Marlatte
 * @author original author of GPSd4Java: thoeger
 */

public class GPSTrackPointsRun {
	/// dateFormat to convert GPS timestamp to a desirable format
	protected final DateFormat dateFormat; // Don't make this static!

	private GPSTrackPointsRun() {
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	/**
	 * @param args Command line arguments<br>
	 * &lt;hostname> &lt;port #><br>
	 * default is localhost and port 2947
	 */
	public static void main(final String[] args) {
		WatchButton wp = new WatchButton(23); // watch for button action on GPIO23
		FixStatus fsLED = new FixStatus(); // take over LED1
		int wptIndex = 0;
		GPSPosition myGPSPosition = new GPSPosition();



		// start the LED thread
		Thread fsLEDThread = new Thread(fsLED);
		fsLEDThread.start();

		LogStatus lsLED = new LogStatus(); // sets up the LED to its default state and shuts the LED off

		// start the LED thread
		Thread lsLEDThread = new Thread(lsLED);
		lsLEDThread.start();

		String host = "localhost";
		int port = 2947;

		switch (args.length) {
		case 0:
			// Nothing to do, use default
			break;
		case 1:
			// only server specified
			host = args[0];
			break;
		case 2:
			// Server and port specified
			host = args[0];
			if (args[1].matches("\\d+")) {
				port = Integer.parseInt(args[1]);
			}
			break;
		default:
			break;
		}

		GPSdEndpoint ep = null;
		ResultParser rp = new ResultParser();

		try {
			ep = new GPSdEndpoint(host, port, rp);
		} catch (Exception e) {
			System.err.println("Caught an exception setting socket port " + e.toString());
		}

		String GPXFilename = GPSTrackPointsRun.getNewGPXFilename();
		GPSTrackPointsRun.writeGPXHeader(GPXFilename);

		try {
			ep.addListener(new ObjectListener(GPXFilename, fsLED, myGPSPosition) {

				@Override
				public void handleTPV(final TPVObject tpv) {
					if (!Double.isNaN(tpv.getAltitude())) {

						myGPSPosition.setPosition(tpv);

						if (fsLED != null) {
							fsLED.GPSFixLED1();
						}
					}
				}

				@Override
				public void handleSKY(final SKYObject sky) {
					// System.err.println("INFO: Tester - SKY: " + sky);
					for (final SATObject sat : sky.getSatellites()) {
						// System.err.println("INFO: Tester - SAT: " + sat);
					}
				}

				@Override
				public void handleSUBFRAME(final SUBFRAMEObject subframe) {
					// System.err.println("INFO: Tester - SUBFRAME: " + subframe);
				}

				@Override
				public void handleATT(final ATTObject att) {
					// System.err.println("INFO: Tester - ATT: " + att);
				}

				@Override
				public void handleDevice(final DeviceObject device) {
					// System.err.println("INFO: Tester - Device: " + device);
				}

				@Override
				public void handleDevices(final DevicesObject devices) {
					for (final DeviceObject d : devices.getDevices()) {
						// System.err.println("INFO: Tester - Device: " + d);
					}
				}
			});

			ep.start();
			ep.watch(true, true);

			ButtonCallback myButtonCallback = new ButtonCallback(GPXFilename, lsLED, wptIndex, myGPSPosition);

			wp.initButton();

			wp.setButtonCallback(myButtonCallback);

			// start the watch thread
			Thread watchThread = new Thread(wp);
			watchThread.start();

			// The push button thread will kill itself after a long push
			try {
				watchThread.join();
			} catch (Exception e) {
				System.err.println("Caught exception while sleeping " + e.toString());
			}

			ep.stop();

			fsLED.stopLED1();
			lsLEDThread.stop();

		} catch (final Exception e) {
			System.err.println("ERROR: GPSTrackPoints - Problem encountered" + e);
		}

		// shutdown
		Runtime rt = Runtime.getRuntime();
		try {
			rt.exec("shutdown now");
		} catch (IOException e) {
			System.err.println("Caught exception trying to shutdown " + e.toString());
		}
	}

	private static String getNewGPXFilename() {
		String localDateTime = LocalDateTime.now().toString();
		String GPXFilename = "Shared/TrackPoints" + localDateTime + ".gpx";

		return(GPXFilename);
	}

	private static void writeGPXHeader(String GPXFilename) {
		if (GPXFilename == null || GPXFilename.matches(""))
			return;

		try {
			RandomAccessFile GPXHeader = new RandomAccessFile(GPXFilename, "rw"); // Open no append
			GPXHeader.writeChars("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			GPXHeader.writeChars("<gpx version=\"1.1\" creator=\"com.traviscons.GPSTrackPoints\"\n");
			GPXHeader.writeChars("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
			GPXHeader.writeChars("<metadata><name>GPSTRackPoints " + GPXFilename + "</name></metadata>\n");
			GPXHeader.writeChars("</gpx>");
			GPXHeader.close();

		} catch (IOException e) {
			System.err.println("Caught an exception writing GPX header " + e.toString());
		}


	}
}

