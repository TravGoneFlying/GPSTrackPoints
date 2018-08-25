package com.traviscons.GPSTrackPoints.backend;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.concurrent.TimeUnit;

import java.io.FileWriter;
import java.io.FileReader;

import java.lang.Runtime;

/** Take over the power LED on Raspberry Pi 3+ and use to indicate GPS Fix status
*
* This uses the sysfs interface to LED1 (the red power LED). It normally is
* on to indicate power. We will take it over and use it to indicate GPS fix status.
*
* We will show three states of the unit:
* 1) Power on but no GPS Fix: Slow flash (1/2 second once every 5 seconds)
* 2) Power on with GPS Fix: short flash with every position report: 1/4 second with every GPS fix (about once per second)
* 3) Shutdown: Off
*
* State 2 depends on a receiving a trigger for each GPS position report. The LED will be
* off in between triggers and will revert to the OnNoFix flash rate
*
* This requires a thread to modify the states of the LED on a timed basis and
* accessor methods to change the state. These will be synchronized to provide
* thread safety.
*/

public class FixStatus implements Runnable {
	String LED1Path; ///< Holds the constructed path of the gpio pin
	enum FLASHSTATES {OFFNOFIX, ONNOFIX, ONFIX, OFF}
	FLASHSTATES LED1State;
	int LED1StateCount;

	/** Implement a basic test of the LED.
	* \param none
	*
	* <h1>Design</h1>
	* Initialize LED1. It's default state will be slow flash, waiting for a GPS Fix.
	* After a few seconds, transition to normal flash to show a GPS fix.
	* After a few seconds, transition back to a slow flash.
	* After a few seconds, transition to off.
	* After a few seconds, restore the LED to on for its normal power mode.
	*/
	public static void main(String args[]) {
		FixStatus fsLED = new FixStatus(); // sets up the LED to its default state and shuts the LED off

		// start the LED thread
		Thread fsLEDThread = new Thread(fsLED);
		fsLEDThread.start();

		// sleep for 15 seconds while the LED flashes slowly (its default state)
		fsLED.sleep(10000);

		// Trigger 4 quick flashes for GPS fixes
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);
		fsLED.GPSFixLED1();

		// Sleep for 15 seconds. The LED will remain off for 5 seconds then flash slowly
		// this tests the transition from GPS fixes back to no fix
		fsLED.sleep(15000);

		// Trigger 4 quick flashes for GPS fixes
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);
		fsLED.GPSFixLED1();
		fsLED.sleep(1000);

		// Stop the LED and end the test
		fsLED.stopLED1();


		try {
			fsLEDThread.join();
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
	* The default state is OFFNOFIX
	*/
	FixStatus() {
		LED1Path = "/sys/class/leds/led1";

		initLED1();
	}

	/** Initialize the LED to an OFF state.
	*/

	private void initLED1() {
		FileWriter sysFile;

		LED1State = FLASHSTATES.OFFNOFIX;
		LED1StateCount = 0; // the timing thread will count this up to time this OFFNOFIX state.

		try {
			sysFile = new FileWriter(LED1Path + "/trigger");
			sysFile.write("none");
			sysFile.close();

		} catch (Exception e) {
			System.err.println("Caught an exception setting the trigger for LED1 " + e.toString());
		}

		LED1TurnOff();
	}

	private void LED1TurnOn() {
		try {
			FileWriter sysFile = new FileWriter(LED1Path + "/brightness");
			sysFile.write("1");
			sysFile.close();
		} catch (Exception e) {
			System.err.println("Caught an exception turning LED1 On " + e.toString());
		}
	}

	private void LED1TurnOff() {
		try {
			FileWriter sysFile = new FileWriter(LED1Path + "/brightness");
			sysFile.write("0");
			sysFile.close();
		} catch (Exception e) {
			System.err.println("Caught an exception turning LED1 off " + e.toString());
		}
	}

	synchronized public void stopLED1() {
		FileWriter sysFile;

		LED1State = FLASHSTATES.OFF;

		LED1TurnOff();
	}

	/** Called to flash the LED for a GPS Fix
	*/
	synchronized public void GPSFixLED1() {
		if (LED1State != FLASHSTATES.OFF) {
			LED1State = FLASHSTATES.ONFIX;
			LED1StateCount = 0;
			LED1TurnOn();
		}
	}

	/** Start a thread to maintain a time base for flashing LED1.
	*
	* Every 100ms, determine the state of the LED and advance to the next state.
	*/

	public void run() {
		boolean runningState = true;

		while (runningState) {

			switch (LED1State) {
				case OFFNOFIX:
					LED1StateCount++;

					if (LED1StateCount >= 50) {
						// We reached 5.0 seconds, flash LED for 1/4 second
						LED1State = FLASHSTATES.ONNOFIX;
						LED1StateCount = 0;
						LED1TurnOn();
					}
					break;

				case ONNOFIX:
					LED1StateCount++;

					if (LED1StateCount >= 2) {
						// We reached 0.2 second, turn the LED off
						LED1State = FLASHSTATES.OFFNOFIX;
						LED1StateCount = 0;
						LED1TurnOff();
					}
					break;

				case ONFIX:
					LED1StateCount++;

					if (LED1StateCount >= 2) {
						// We reached 0.1 seconds, turn the LED off
						LED1State = FLASHSTATES.OFFNOFIX;
						LED1StateCount = 0;
						LED1TurnOff();
					}
					break;

				case OFF:
					runningState = false;
					break;

				default:
					runningState = false;
					break;
			}

			if (runningState) {
				try {
					Thread.sleep(100); // Sleep for 100ms at a time
				} catch (InterruptedException ex) {
					// Something woke us up but not a watch event
					System.err.println("ERROR: GPS Fix LED was interrupted");
				}
			}
		}
	}
}
