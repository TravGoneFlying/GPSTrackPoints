package com.traviscons.GPSTrackPoints.backend;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.concurrent.TimeUnit;

import java.io.FileWriter;
import java.io.FileReader;

import java.lang.Runtime;

/** Take over the activity LED on Raspberry Pi 3+ and use to indicate logging
*
* This uses the sysfs interface to LED0 (the green activity LED). It normally is
* used to indicate SD Card activity. We will take it over and use it to indicate logging
* of a GPS position.
*
* The LED will normally be off. When we log a position, we will flash the LED for 1/2 second
*
* This requires a thread to modify the states of the LED on a timed basis and
* accessor methods to change the state. These will be synchronized to provide
* thread safety.
*/

public class LogStatus implements Runnable {
	String LED0Path; ///< Holds the constructed path of the gpio pin
	enum LEDSTATES {OFF, FLASH}
	LEDSTATES LED0State;
	int LED0StateCount;
	boolean runningState;

	/** Implement a basic test of the LED.
	* \param none
	*
	* <h1>Design</h1>
	*/
	public static void main(String args[]) {
		LogStatus lsLED = new LogStatus(); // sets up the LED to its default state and shuts the LED off

		// start the LED thread
		Thread lsLEDThread = new Thread(lsLED);
		lsLEDThread.start();

		// sleep for 5 seconds. The LED will be off
		lsLED.sleep(5000);

		// Trigger 4 flashes over a period of seconds
		lsLED.logLED();
		lsLED.sleep(1000);
		lsLED.logLED();
		lsLED.sleep(2000);
		lsLED.logLED();
		lsLED.sleep(3000);
		lsLED.logLED();

		// Sleep for 5 seconds.
		lsLED.sleep(5000);

		// Trigger 4 flashes over a period of seconds
		lsLED.logLED();
		lsLED.sleep(4000);
		lsLED.logLED();
		lsLED.sleep(5000);
		lsLED.logLED();
		lsLED.sleep(6000);
		lsLED.logLED();
		lsLED.sleep(1000);

		// Stop the LED and end the test
		lsLED.stopLED();


		try {
			lsLEDThread.join();
		} catch (Exception e) {
			System.err.println("Caught exception while sleeping " + e.toString());
		}
	}

	private void sleep(int period) {
		try {
			Thread.sleep(period);
		} catch (InterruptedException e) {
		}
	}

	/** Constructor that sets up the LED.
	*
	*/
	LogStatus() {
		LED0Path = "/sys/class/leds/led0";

		initLED0();
	}

	/** Initialize the LED to an off state
	*/

	private void initLED0() {
		FileWriter sysFile;

		LED0State = LEDSTATES.OFF;
		LED0StateCount = 0; // We don't need to time the off state

		try {
			sysFile = new FileWriter(LED0Path + "/trigger");
			sysFile.write("none");
			sysFile.close();

		} catch (Exception e) {
			System.err.println("Caught an exception setting the trigger for LED0 " + e.toString());
		}

		LED0TurnOff();
	}

	private void LED0TurnOn() {
		try {
			FileWriter sysFile = new FileWriter(LED0Path + "/brightness");
			sysFile.write("1");
			sysFile.close();
		} catch (Exception e) {
			System.err.println("Caught an exception turning LED0 On " + e.toString());
		}
	}

	private void LED0TurnOff() {
		try {
			FileWriter sysFile = new FileWriter(LED0Path + "/brightness");
			sysFile.write("0");
			sysFile.close();
		} catch (Exception e) {
			System.err.println("Caught an exception turning LED0 off " + e.toString());
		}
	}

	synchronized public void stopLED() {
		FileWriter sysFile;

		LED0State = LEDSTATES.OFF;

		LED0TurnOff();
		runningState = false;
	}

	/** Called to flash the LED for a GPS Fix
	*/
	synchronized public void logLED() {
		LED0State = LEDSTATES.FLASH;
		LED0StateCount = 0;
		LED0TurnOn();
	}

	/** Start a thread to maintain a time base for flashing LED0.
	*
	* Every 100ms, determine the state of the LED and advance to the next state.
	*/

	public void run() {
		runningState = true;

		while (runningState) {

			switch (LED0State) {
				case OFF:
					// Nothing to do
					break;

				case FLASH:
					LED0StateCount++;

					if (LED0StateCount >= 5) {
						// We reached 0.5 seconds, turn the LED off
						LED0State = LEDSTATES.OFF;
						LED0StateCount = 0;
						LED0TurnOff();
					}
					break;
			}

			if (runningState) {
				try {
					Thread.sleep(100); // Sleep for 100ms at a time
				} catch (InterruptedException ex) {
					// Something woke us up but not a watch event
					System.err.println("poll was interrupted");
				}
			}
		}
	}
}
