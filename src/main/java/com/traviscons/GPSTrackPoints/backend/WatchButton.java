package com.traviscons.GPSTrackPoints.backend;

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

class WatchButton implements Runnable {
	int gpioNum; ///< Holds the gpio pin number. Note not the physical pin number.
	String gpioName; ///< Holds the constucted name of the gpio pin. e.g. "gpio23"
	String gpioPath; ///< Holds the constructed path of the gpio pin
	String gpioValuePath; ///< Holds the constructed path of the gpio value file
	iButtonCallback buttonCallback; ///< object to provide the callback mechanism

	/** Implement a main to test a thread that waits for the button
	* \param none
	*
	* <h1>Design</h1>
	* Set up a watch event for button 23. Initialize the button (direction in,
	* edge both, pull up resistor.
	* </p>
	* Then start the watch thread and sleep for a minute.
	*/
	public static void main(String args[]) {
		System.err.println("Running just to test for button transitions");
		System.err.println("Defaults to GPIO23 (pin 16) with a pull up");
		WatchButton wp = new WatchButton(23);
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

		// sleep for a minute
		try {
			watchThread.join();
		} catch (Exception e) {
			System.err.println("Caught exception while sleeping " + e.toString());
		}
	}

	/** Constructor that sets the bpio number to use for the button
	* \param inGpioNum the gpio number (not the pin number)
	* e.g. GPIO23 is pin 16. The number to use is 23.
	*
	* \todo Implement error checking on the gpio number
	*/
	WatchButton(int inGpioNum) {
		gpioNum = inGpioNum;
		gpioName = "gpio" + gpioNum;
		gpioPath = "/sys/class/gpio/gpio" + gpioNum;
		gpioValuePath = "/sys/class/gpio/gpio" + gpioNum + "/value";

	}

	/** Initialize the gpio pin for input, edge detection both, pull up resistor.
	* The button will be normally open. Close to ground the pin. pin 14 is ground
	* right next to pin 16. It also means we don't have a power wire floating around.
	*/

	public void initButton() {
		FileWriter sysFile;

		try {
			sysFile = new FileWriter("/sys/class/gpio/export");
			sysFile.write(Integer.toString(gpioNum));
			sysFile.close();
			Thread.sleep(100); // sleep after exporting
	
		} catch (Exception e) {
			System.err.println("Caught an exception exporting the button " + e.toString());
		}

		try {
			sysFile = new FileWriter(gpioPath + "/direction");
			sysFile.write("in");
			sysFile.close();
			
		} catch (Exception e) {
			System.err.println("Caught an exception setting the direction for the button " + e.toString());
		}

		try {
			sysFile = new FileWriter(gpioPath + "/edge");
			sysFile.write("both");
			sysFile.close();
		} catch (Exception e) {
			System.err.println("Caught an exception setting the edge for the button " + e.toString());
		}

		try {
	
			Runtime rt = Runtime.getRuntime();
			rt.exec("raspi-gpio set " + gpioNum + " pu");
		} catch (Exception e) {
			System.err.println("Caught an exception setting the pull up for the button " + e.toString());
		}
	}

	public void resetButton() {
		FileWriter sysFile;

		try {
			sysFile = new FileWriter("/sys/class/gpio/unexport");
			sysFile.write(Integer.toString(gpioNum));
			sysFile.close();
		} catch (Exception e) {
			System.err.println("Caught an exception unexporting the button " + e.toString());
		}
	}


	/** Set the callback object.
	*
	* \param inbuttonCallback the object to use for the callbacks.
	*/
	public void setButtonCallback(iButtonCallback inButtonCallback) {
		buttonCallback = inButtonCallback;
	}

	/** Start a thread to watch for the button transitions. This thread
	* uses watch to block while waiting. Once the button is pushed, simple sleeps
	* are used to time the push.
	*
	* We'll take the watch signal that it has been pushed.
	* \todo Study to make sure watch debounces reliably
	* 
	* Once the button has been pushed, we use a loop with 100ms sleeps to watch
	* the button. If it is released &lt;3 seconds - signal a GPS position log. If
	* it remains pressed for 3 seconds or greater, signal a shutdown. Note we do not
	* wait for the button to be relesed after 3 seconds.
	*/

	public void run() {
		/* buttonState keeps track of the button. 0 is inactive
		* 1 is active waiting for a release or for 3 seconds to expire.
		* 2 we had a 3 second timeout and we are waiting for the button to
		* go inactive.
		*/
		int buttonState = 0; // 0 is inactive
		boolean runningState = true;
		
		try {
			// Set up the watch service. This is part of java.nio
			WatchService watcher = FileSystems.getDefault().newWatchService();
			
			// We watch the gpio directory. WatchService cannot watch specific files
			// We'll watch for anything but we really want a modification of the value file
			Path dir = Paths.get(gpioPath);
			dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			
			while (runningState) {
				WatchKey key;
				try {
					// This is the main point of watching.
					// we will block while we wait for a key to be available
					// take() is the other mechanism that will block
					// The poll event will return a null with the timeout
					if (buttonState == 0 || buttonState == 2) {
						/* The button state is inactive or we are waiting for to go inactive.
						* We can block until the button transitions.
						*/
						key = watcher.take();
					} else {
						/* The button statis active. Let's set a 3
						* second timeout. If we get a transition
						* before 3 seconds, that is a normal push.
						* If we get a timeout, then it's a long push.
						*/
						key = watcher.poll(3000, TimeUnit.MILLISECONDS);
					}
				} catch (InterruptedException ex) {
					// Something woke us up but not a watch event
					System.err.println("poll was interrupted");
					break;
				}

				// If the key is null, then it was the timeout. 
				if (key == null) {
					/* We now know that it was a long push. */
					if (buttonCallback != null) {
						buttonCallback.LongPush();
						resetButton();
						runningState = false;
					}
					buttonState = 2; // wait for the button to go inactive
					continue;
				}

				// We have one or more watch events, let's see what it is
				for (WatchEvent<?> event : key.pollEvents()) {
					// get event type
					WatchEvent.Kind<?> kind = event.kind();
			 
					// get file name
					@SuppressWarnings("unchecked")
					WatchEvent<Path> ev = (WatchEvent<Path>) event;
					Path fileName = ev.context();
			 
					if (kind == OVERFLOW) {
						System.err.println("Overflow");
						continue;
					} else if (kind == ENTRY_CREATE) {
			 
						// process create event
						System.err.println("Create " + fileName);
			 
					} else if (kind == ENTRY_DELETE) {
			 
						// process delete event
						System.err.println("Delete " + fileName);
			 
					} else if (kind == ENTRY_MODIFY) {
						// process modify event
						int value;

						if (fileName.toString().matches("value")) {
							FileReader vFile = new FileReader(gpioValuePath);
							value = vFile.read();
							if (value == 49) {
								/* Button high. That's inactive */
								if (buttonState == 1 && buttonCallback != null) {
									/* This is a normal release without a timeout */
									buttonCallback.ShortPush();
								}
								
								/* whether it was a normal release or after a timeout,
								/* we can go back to inactive
								*/
								buttonState = 0;
							} else if (value == 48) {
								/* Button low. That's active */
								buttonState = 1;
							
							}
							vFile.close();
						}

					}
				}
			 
				// IMPORTANT: The key must be reset after processed
				boolean valid = key.reset();
				if (!valid) {
					System.err.println("key is not valid after a reset. Exiting");
					break;
				}
			}
		} catch (IOException ex) {
	            System.err.println(ex);
        	}
	}
}
