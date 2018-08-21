package com.traviscons.GPSTrackPoints.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;

/**
 * Thread to support socket to fake data from GPSd
 *
 * @author TravGoneFlying
 */

public class TestSocket extends Thread {

	private BufferedReader reader;

	private BufferedWriter writer;

	private String server;

	private int port;

	private Socket socket;

	/**
	 * @param reader       the socket input
	 * @param daemon       whether to configure the thread as a daemon, as defined in {@link Thread#setDaemon}
	 */
	public TestSocket(final BufferedReader reader, final boolean daemon) {

		port = 2947;
		server = "localhost";
		this.reader = reader;

		this.setDaemon(daemon);
		this.setName("Test Socket Thread");
	}

	/**
	 * @param reader        the socket input
	 */
	public TestSocket(final BufferedReader reader) {
		this(reader, true);
	}

	@Override
	public void run() {
		if (this.reader != null) {
			while (true) {
				try {
					// read line from socket
					final String s = this.reader.readLine();
					if (s == null) {
						break;
					}
					if (!s.isEmpty()) {
						// parse line and handle it accordingly
						GPSdCommandParse(s);
					}
				} catch (final SocketException e) {
					break; // stop
				} catch (final Exception e) {
					// TODO handle this better
					System.err.println("WARNING: TestSocket - Problem encountered while reading/parsing/handling line" + e);
				}
			}
		}
		if (!Thread.interrupted()) {
			if (this.reader != null) {
				System.err.println("WARNING: TestSocket - Problem encountered while reading/parsing/handling line, attempting restart");
			}
		}
	}

	/// Initializes the socket interface. This is used for the first
	/// initialization as well as recovery.
	protected void retry() {
		if (this.reader != null) {
			System.err.println("DEBUG: TestSocket - Disconnected from GPS socket, retrying connection");
		} else {
			System.err.println("DEBUG: TestSocket - Connecting to GPSD socket");
		}

		try {
			this.socket = new Socket(server, port);
			this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
			this.writer = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
			System.err.println("DEBUG: TestSocket - Connected to GPS socket");
		} catch (IOException e) {
			System.err.println("DEBUG: TestSocket - Still disconnected from GPS socket, retrying connection again");
		}
	}

	/**
	 * Halts the socket thread.
	 *
	 */
	public void halt() {
		try {
			this.reader.close();
			this.writer.close();
			this.socket.close();
		} catch (final IOException e) {
			// ignore
		}
	}

	public void GPSdCommandParse(String command) {
		System.err.println("DEBUG: GPSdCommandParse got command: " + command);
	}
}