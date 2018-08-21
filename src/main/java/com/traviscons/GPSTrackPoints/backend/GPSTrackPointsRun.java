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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import java.io.FileWriter;
import java.io.IOException;

import com.traviscons.GPSTrackPoints.api.ObjectListener;
import com.traviscons.GPSTrackPoints.backend.GPSdEndpoint;
import com.traviscons.GPSTrackPoints.backend.ResultParser;
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
		try {
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

			final GPSdEndpoint ep;
			ResultParser rp = new ResultParser();
			ep = new GPSdEndpoint(host, port, rp);

			String GPXFilename = "Shared/newTrackPoints.gpx";

			FileWriter GPXHeaderFooter = new FileWriter(GPXFilename, false); // Open no append

			GPXHeaderFooter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			GPXHeaderFooter.write("<gpx version=\"1.0\" creator=\"com.traviscons.GPSTrackPoints\">\n");
			GPXHeaderFooter.write("<name>GPSTRackPoints date</name>\n");
			GPXHeaderFooter.close();

			ep.addListener(new ObjectListener(GPXFilename) {

				@Override
				public void handleTPV(final TPVObject tpv) {
					FileWriter GPXOutput = null;

					if (!Double.isNaN(tpv.getAltitude())) {
						try {
							GPXOutput = new FileWriter(GPXFilename, true); // Open with append
							GPXOutput.write("<wpt lat=\"" + tpv.getLatitude() + "\" lon=\"" + tpv.getLongitude() + "\"><ele>" + tpv.getAltitude() + "</ele><time>" + tpv.getTimestampText() + "</time>");
							switch (tpv.getMode()) {
								case ThreeDimensional:
									GPXOutput.write("<fix>3d</fix>");
									break;
								case TwoDimensional:
									GPXOutput.write("<fix>2d</fix>");
									break;
								default:
									// Do nothing
							}
							GPXOutput.write("</wpt>\n");
							GPXOutput.close();
						} catch (IOException e) {
							System.err.println("Caught Exception writing to the GPX output " + e.toString());
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

//			System.err.println("INFO: Tester - Version: " + ep.version());

//			System.err.println("INFO: Tester - Watch: " + ep.watch(true, true));

//			System.err.println("INFO: Tester - Poll: " + ep.poll());

			/* Infinite loop while the listeners do the work */
			Thread.sleep(60000);

			ep.stop();

			GPXHeaderFooter = new FileWriter(GPXFilename, true); // Open with append
			GPXHeaderFooter.write("</gpx>\n");
			GPXHeaderFooter.close();


		} catch (final Exception e) {
			System.err.println("ERROR: Tester - Problem encountered" + e);
		}
	}
}
