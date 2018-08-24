package com.traviscons.GPSTrackPoints.api;

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

import java.io.FileWriter;
import java.io.IOException;

import com.traviscons.GPSTrackPoints.types.ATTObject;
import com.traviscons.GPSTrackPoints.types.DeviceObject;
import com.traviscons.GPSTrackPoints.types.DevicesObject;
import com.traviscons.GPSTrackPoints.types.SKYObject;
import com.traviscons.GPSTrackPoints.types.TPVObject;
import com.traviscons.GPSTrackPoints.types.subframes.SUBFRAMEObject;

import com.traviscons.GPSTrackPoints.backend.FixStatus;

/**
 * Adapter class for {@link IObjectListener}
 *
 * @author thoeger
 */
public class ObjectListener implements IObjectListener {
	public String GPXFilename;
	public FixStatus fsLED;

	public ObjectListener(String inGPXFilename, FixStatus infsLED) {
		GPXFilename = inGPXFilename;
		fsLED = infsLED;
	}

	@Override
	public void handleTPV(final TPVObject tpv) {
		// implement in subclass if needed
	}

	@Override
	public void handleSKY(final SKYObject sky) {
		// implement in subclass if needed
	}

	@Override
	public void handleATT(final ATTObject att) {
		// implement in subclass if needed
	}

	@Override
	public void handleSUBFRAME(final SUBFRAMEObject subframe) {
		// implement in subclass if needed
	}

	@Override
	public void handleDevices(final DevicesObject devices) {
		// implement in subclass if needed
	}

	@Override
	public void handleDevice(final DeviceObject device) {
		// implement in subclass if needed
	}

}
