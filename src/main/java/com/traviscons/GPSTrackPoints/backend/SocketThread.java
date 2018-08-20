package com.traviscons.GPSTrackPoints.backend;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.net.SocketException;

/**
 * thread reading input from GPSd server
 *
 * @author thoeger
 */
public class SocketThread extends Thread {

	private final BufferedReader reader;

	private final GPSdEndpoint endpoint;

	private final AbstractResultParser resultParser;

	private final WaitableBoolean running = new WaitableBoolean(true);

	/**
	 * @param reader       the socket input
	 * @param endpoint     the endpoint
	 * @param resultParser the result parser
	 * @param daemon       whether to configure the thread as a daemon, as defined in {@link Thread#setDaemon}
	 */
	public SocketThread(final BufferedReader reader, final GPSdEndpoint endpoint,
							  final AbstractResultParser resultParser, final boolean daemon) {

		if (resultParser == null) {
			throw new IllegalArgumentException("resultParser can not be null!");
		}

		this.reader = reader;
		this.endpoint = endpoint;
		this.resultParser = resultParser;

		this.setDaemon(daemon);
		this.setName("GPS Socket Thread");
	}

	/**
	 * @param reader        the socket input
	 * @param endpoint      the endpoint
	 * @param resultParser  the result parser
	 */
	public SocketThread(final BufferedReader reader, final GPSdEndpoint endpoint, final AbstractResultParser resultParser) {
		this(reader, endpoint, resultParser, true);
	}

	@Override
	public void run() {
		if (this.reader != null) {
			while (this.running.get()) {
				try {
					// read line from socket
					final String s = this.reader.readLine();
					if (s == null) {
						break;
					}
					if (!s.isEmpty()) {
						// parse line and handle it accordingly
						this.endpoint.handle(this.resultParser.parse(s));
					}
				} catch (final SocketException e) {
					break; // stop
				} catch (final Exception e) {
					// TODO handle this better
					System.err.println("WARNING: SocketThread - Problem encountered while reading/parsing/handling line" + e);
				}
			}
		}
		if (this.running.get() && !Thread.interrupted()) {
			if (this.reader != null) {
				System.err.println("WARNING: SocketThread - Problem encountered while reading/parsing/handling line, attempting restart");
			}
			retry();
		}
	}

	/// Initializes the socket interface. This is used for the first
	/// initialization as well as recovery.
	protected void retry() {
		if (this.reader != null) {
			System.err.println("DEBUG: SocketThread - Disconnected from GPS socket, retrying connection");
		} else {
			System.err.println("DEBUG: SocketThread - Connecting to GPSD socket");
		}

		while (this.running.get()) {
			try {
				this.running.waitFor(this.endpoint.getRetryInterval());
				this.endpoint.handleDisconnected();
				System.err.println("DEBUG: SocketThread - Connected to GPS socket");
				this.running.set(false);
			} catch (InterruptedException ix) {
				break;
			} catch (IOException e) {
				System.err.println("DEBUG: SocketThread - Still disconnected from GPS socket, retrying connection again");
			}
		}
	}

	/**
	 * Halts the socket thread.
	 *
	 */
	public void halt() {
		this.running.set(false);

		try {
			this.reader.close();
		} catch (final IOException e) {
			// ignore
		}
	}
}