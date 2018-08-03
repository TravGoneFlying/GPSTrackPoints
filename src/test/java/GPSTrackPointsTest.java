package com.traviscons.GPSTrackPoints.tests;

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

import org.junit.*;
import static org.junit.Assert.*;

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
 * This class provides tests during the startup phase of GPSd4Java<br>
 * It will later be replaced by JUnit Tests
 *
 * created: 17.01.2011
 *
 */
public class GPSTrackPointsTest {
	@Test
	public void mainTest() {
		try {
			String host = "localhost";
			int port = 2947;

			System.err.println("INFO: GPSTRackPointsTest:MainTest");
			final GPSdEndpoint ep = new GPSdEndpoint(host, port, new ResultParser());

			ep.addListener(new ObjectListener() {

				@Override
				public void handleTPV(final TPVObject tpv) {
					System.err.println("INFO: Tester - TPV: " + tpv);
				}

				@Override
				public void handleSKY(final SKYObject sky) {
					System.err.println("INFO: Tester - SKY: " + sky);
					for (final SATObject sat : sky.getSatellites()) {
						System.err.println("INFO: Tester - SAT: " + sat);
					}
				}

				@Override
				public void handleSUBFRAME(final SUBFRAMEObject subframe) {
					System.err.println("INFO: Tester - SUBFRAME: " + subframe);
				}

				@Override
				public void handleATT(final ATTObject att) {
					System.err.println("INFO: Tester - ATT: " + att);
				}

				@Override
				public void handleDevice(final DeviceObject device) {
					System.err.println("INFO: Tester - Device: " + device);
				}

				@Override
				public void handleDevices(final DevicesObject devices) {
					for (final DeviceObject d : devices.getDevices()) {
						System.err.println("INFO: Tester - Device: " + d);
					}
				}
			});

			ep.watch(true, true);
			ep.start();

//			System.err.println("INFO: Tester - Version: " + ep.version());

//			System.err.println("INFO: Tester - Watch: " + ep.watch(true, true));

//			System.err.println("INFO: Tester - Poll: " + ep.poll());

			Thread.sleep(5000);
		} catch (final Exception e) {
			System.err.println("ERROR: Tester - Problem encountered" + e);
		}
	}
}
