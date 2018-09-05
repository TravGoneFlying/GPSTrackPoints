package com.traviscons.GPSTrackPoints.backend;

import java.io.RandomAccessFile;
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
		RandomAccessFile GPXOutput;

		try {
			GPXOutput = new RandomAccessFile(GPXFilename, "rw");
			// Must position at the end
			GPXOutput.seek(GPXOutput.length() - 6); // There is a </gpx> at the end that we want to overwrite
			GPXOutput.writeChars("<wpt lat=\"" + lastTPV.getLatitude() + "\" lon=\"" + lastTPV.getLongitude() + "\">");
			GPXOutput.writeChars("<ele>" + lastTPV.getAltitude() + "</ele><time>" + lastTPV.getTimestampText() + "</time>");
			GPXOutput.writeChars("<name>"+wptIndex+"</name>");
			switch (lastTPV.getMode()) {
				case ThreeDimensional:
					GPXOutput.writeChars("<fix>3d</fix>");
					break;
				case TwoDimensional:
					GPXOutput.writeChars("<fix>2d</fix>");
					break;
				default:
					// Do nothing
			}
			GPXOutput.writeChars("</wpt>\n");
			GPXOutput.writeChars("</gpx>");
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

