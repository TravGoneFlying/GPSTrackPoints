package com.traviscons.GPSTrackPoints.backend;

import java.io.FileWriter;
import java.io.IOException;

import com.traviscons.GPSTrackPoints.types.TPVObject;

public class ButtonCallback implements iButtonCallback {
	String GPXFilename;
	LogStatus lsLED;
	int wptIndex;
	GPSPosition myGPSPosition;
	

	ButtonCallback(String GPXFilename, LogStatus lsLED, int wptIndex, GPSPosition myGPSPosition) {
		this.GPXFilename = GPXFilename;
		this.lsLED = lsLED;
		this.wptIndex = wptIndex;
		this.myGPSPosition = myGPSPosition;
	}

	public void ShortPush() {
		TPVObject lastTPV = myGPSPosition.getPosition();
		FileWriter GPXOutput;

		try {
			GPXOutput = new FileWriter(GPXFilename, true); // Open with append
			GPXOutput.write("<wpt lat=\"" + lastTPV.getLatitude() + "\" lon=\"" + lastTPV.getLongitude() + "\">");
			GPXOutput.write("<ele>" + lastTPV.getAltitude() + "</ele><time>" + lastTPV.getTimestampText() + "</time>");
			GPXOutput.write("<name>"+wptIndex+"</name>");
			switch (lastTPV.getMode()) {
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
			GPXOutput.close(); // Closing also flushes
			lsLED.logLED();
			wptIndex++;
		} catch (IOException e) {
			System.err.println("Caught Exception writing to the GPX output " + e.toString());
		}

	}

	public void LongPush() {
	}
}

