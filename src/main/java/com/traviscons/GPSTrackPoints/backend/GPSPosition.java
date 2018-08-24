package com.traviscons.GPSTrackPoints.backend;

import com.traviscons.GPSTrackPoints.types.TPVObject;

/** Hold a GPS position and protect access to it for thread safety.
*/
class GPSPosition {
	private TPVObject tpv;

	synchronized public void setPosition(TPVObject newTPV) {
		tpv = newTPV;
	}

	synchronized public TPVObject getPosition() {
		return(tpv);
	}
}