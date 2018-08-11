package com.traviscons.GPSTrackPoints.backend;

/*
 * #%L
 * GPSd4Java
 * %%
 * Copyright (C) 2011 - 2012 Taimos GmbH
 * %%
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
 * #L%
 */

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import java.io.FileWriter;
import java.io.BufferedWriter;
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
 * lat, long and altitude are displayed to the console.
 *
 */
public class GPSTrackPointsRun {
	protected final DateFormat dateFormat; // Don't make this static!
	
	private GPSTrackPointsRun() {
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

	}

	/**
	 * @param args
	 *            the args
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

			String GPXFilename = "Share/newTrackPoints.gpx";
			
			ep.addListener(new ObjectListener(GPXFilename) {

				@Override
				public void handleTPV(final TPVObject tpv) {
					BufferedWriter GPXOutput = null;
					
					if (tpv.getAltitude() != Double.NaN) {
						try {
							GPXOutput = new BufferedWriter(new FileWriter(GPXFilename));
							GPXOutput.write("TPV : " + tpv.getTimestampText() + " " + tpv.getLatitude() + " " + tpv.getLongitude() + " " + tpv.getAltitude());
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
			while (true) {Thread.sleep(60000);}
	
		} catch (final Exception e) {
			System.err.println("ERROR: Tester - Problem encountered" + e);
		}
	}
}
