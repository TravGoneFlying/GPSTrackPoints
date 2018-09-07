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
			GPXOutput.seek(GPXOutput.length() - 7); // There is a </gpx> at the end that we want to overwrite
			writeString(GPXOutput, "<wpt lat=\"" + lastTPV.getLatitude() + "\" lon=\"" + lastTPV.getLongitude() + "\">");
			writeString(GPXOutput, "<ele>" + lastTPV.getAltitude() + "</ele><time>" + lastTPV.getTimestampText() + "</time>");
			writeString(GPXOutput, "<name>"+wptIndex+"</name>");
			switch (lastTPV.getMode()) {
				case ThreeDimensional:
					writeString(GPXOutput, "<fix>3d</fix>");
					break;
				case TwoDimensional:
					writeString(GPXOutput, "<fix>2d</fix>");
					break;
				default:
					// Do nothing
			}
			writeString(GPXOutput, "</wpt>\n");
			writeString(GPXOutput, "</gpx>\n");
			GPXOutput.close(); // Closing also flushes
			lsLED.logLED();
			wptIndex++;
		} catch (IOException e) {
			System.err.println("Caught Exception writing to the GPX output " + e.toString());
		}
	}

	private void writeString(RandomAccessFile myFile, String outString) {
		for (int i=0; i<outString.length(); i++) {
			try {
				myFile.writeByte(outString.charAt(i));
			} catch (IOException e) {
				System.err.println("Exception " + e.toString());
			}
		}
	}

	public void LongPush() {
	}
}

