package com.traviscons.GPSTrackPoints.tests;

import org.junit.*;
import static org.junit.Assert.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import java.util.concurrent.TimeUnit;

import java.io.FileWriter;
import java.io.FileReader;

import java.lang.Runtime;

import com.traviscons.GPSTrackPoints.backend.WatchButton;
import com.traviscons.GPSTrackPoints.backend.iButtonCallback;

/** Set up a button and watch for transitions.
*
* This uses the sysfs interface to the Raspberry Pi GPIO. It uses the sysfs
* of files to set up the button. The only thing that is not possible is the
* setup of the pull up resistor. This is done using a raspi-gpio command line.
*
* Once set up, the java.nio watch is used to monitor the value file for the gpio.
*
* We are looking for specific transitions to support the GPS track logging. All we
* want is a short button push (&lt;3 seconds) and a long button push (>3 seconds).
*
* Call back functions are used to signal when the button is pushed for less than 3
* seconds or when the button is held for more than 3 seconds.
*/

public class WatchButtonTest {
	@Test
	public void initButton() {
		WatchButton wp = new WatchButton(23, "."); // Let WatchButton access files in the local directory
	}


	@Test
	public void setCallback() {
		WatchButton wp = new WatchButton(23, "."); // Let WatchButton access files in the local directory
		class ButtonCallback implements iButtonCallback {
			public void ShortPush() {
				System.err.println("Got a short push");
			}

			public void LongPush() {
				System.err.println("Got long push");
			}
		}
		ButtonCallback myButtonCallback = new ButtonCallback();

		wp.initButton();

		wp.setButtonCallback(myButtonCallback);
	}


	@Test
	public void launchThread() {
		WatchButton wp = new WatchButton(23, "."); // Let WatchButton access files in the local directory
		class ButtonCallback implements iButtonCallback {
			public void ShortPush() {
				System.err.println("Got a short push");
			}

			public void LongPush() {
				System.err.println("Got long push");
			}
		}

		ButtonCallback myButtonCallback = new ButtonCallback();

		wp.initButton();

		wp.setButtonCallback(myButtonCallback);

		// start the watch thread
		Thread watchThread = new Thread(wp);
		watchThread.start();

		System.err.println("Came back from start()");

		try {
			Thread.sleep(1000);
			FileWriter sysfs = new FileWriter("./gpio23/value");
			sysfs.write("1");
			sysfs.close();
			Thread.sleep(2000);

			// push the button for 1 second
			sysfs = new FileWriter("./gpio23/value");
			sysfs.write("0");
			sysfs.close();
			Thread.sleep(1000);
			sysfs = new FileWriter("./gpio23/value");
			sysfs.write("1");
			sysfs.close();

			// pause for 3 seconds
			Thread.sleep(3000);

			// push the button for 2 second
			sysfs = new FileWriter("./gpio23/value");
			sysfs.write("0");
			sysfs.close();
			Thread.sleep(2000);
			sysfs = new FileWriter("./gpio23/value");
			sysfs.write("1");
			sysfs.close();

			// trigger long press which will end the button watch
			sysfs = new FileWriter("./gpio23/value");
			sysfs.write("0");
			sysfs.close();
			Thread.sleep(4000);
		} catch (Exception e) {
			System.err.println("Cauth exception during test" + e.toString());
		}

		try {
			watchThread.join();
		} catch (Exception e) {
			System.err.println("Caught exception while sleeping " + e.toString());
		}
	}
}
