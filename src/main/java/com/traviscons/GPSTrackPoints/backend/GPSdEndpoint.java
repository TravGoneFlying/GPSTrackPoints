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
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONException;
import org.json.JSONObject;

import com.traviscons.GPSTrackPoints.api.IObjectListener;
import com.traviscons.GPSTrackPoints.types.ATTObject;
import com.traviscons.GPSTrackPoints.types.DeviceObject;
import com.traviscons.GPSTrackPoints.types.DevicesObject;
import com.traviscons.GPSTrackPoints.types.IGPSObject;
import com.traviscons.GPSTrackPoints.types.PollObject;
import com.traviscons.GPSTrackPoints.types.SKYObject;
import com.traviscons.GPSTrackPoints.types.TPVObject;
import com.traviscons.GPSTrackPoints.types.VersionObject;
import com.traviscons.GPSTrackPoints.types.WatchObject;
import com.traviscons.GPSTrackPoints.types.subframes.SUBFRAMEObject;

/**
 * GPSd client endpoint. Constructs a thread to listen to a socket. Processing the incoming
 * data stream and provides it to a result parser AbstractResultParser.
 *
 * @author thoeger
 */
public class GPSdEndpoint {

	private Socket socket;

	private BufferedReader in;

	private BufferedWriter out;

	private SocketThread listenThread;

	private final boolean daemon;

	private final List<IObjectListener> listeners = new ArrayList<IObjectListener>(1);

	private IGPSObject asyncResult = null;

	private final Object asyncMutex = new Object();

	private final Object asyncWaitMutex = new Object();

	private final AbstractResultParser resultParser;

	private String server;

	private int port;

	private String lastWatch;

	private AtomicLong retryInterval = new AtomicLong(1000);

	GPSPosition myGPSPosition = new GPSPosition();


	/**
	 * The caller must supply the server name and a port
	 *
	 * The caller can supply a result parser derived from AbstractResultParser.
	 *
	 * The caller can supply a flag to determine if the socket thread is started as a daemon.
	 *
	 * @param server       the server name or IP
	 * @param port         the server port
	 * @param resultParser the result parser
	 * @param daemon       whether to start the underlying socket thread as a daemon, as defined in {@link Thread#setDaemon}
	 */
	public GPSdEndpoint(final String server, final int port, final AbstractResultParser resultParser, final boolean daemon) {
		this.server = server;
		this.port = port;
		if (server == null) {
			throw new IllegalArgumentException("server can not be null!");
		}
		if ((port < 0) || (port > 65535)) {
			throw new IllegalArgumentException("Illegal port number: " + port);
		}
		if (resultParser == null) {
			throw new IllegalArgumentException("resultParser can not be null!");
		}

		this.resultParser = resultParser;

		this.daemon = daemon;
	}

	/**
	 * The caller must supply the server name and a port
	 *
	 * The caller can supply a result parser derived from AbstractResultParser.
	 *
	 * This constructor starts the thread as a daemon (e.g. default daemon=true).
	 *
	 * @param server       the server name or IP
	 * @param port         the server port
	 * @param resultParser the result parser
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public GPSdEndpoint(final String server, final int port, final AbstractResultParser resultParser) throws UnknownHostException, IOException {
		this(server, port, resultParser, true);
	}

	/**
	 * The caller must supply the server name and a port
	 *
	 * This constructor starts the thread as a daemon and creates a new ResultParser using the
	 * ResultParser class.
	 *
	 * @param server       the server name or IP
	 * @param port         the server port
	 */
	public GPSdEndpoint(final String server, final int port) {
		this(server, port, new ResultParser(), true);
	}

	/**
	 * start the socket thread
	 */
	public void start() {
		this.listenThread = new SocketThread(this.in, this, this.resultParser, this.daemon);
		this.listenThread.start();
	}

	/**
	 * Stops the socket thread
	 */
	public void stop() {

		try {
			if (this.socket != null) {
				this.socket.close();
			}
		} catch (final IOException e1) {
			System.err.println("DEBUG: GPSdEndpoint - Close forced: " + e1.getMessage());
		}

		this.listeners.clear();

		if (this.listenThread != null) {
			this.listenThread.halt();
		}

		this.listenThread = null;
	}

	/**
	 * send WATCH command with just the enable flags for WATCH and JSON.
	 *
	 * This command sets watcher mode. It also sets or elicits a report of per-subscriber
	 * policy and the raw bit. An argument WATCH object changes the subscriber's policy.
	 * The response describes the subscriber's policy. The response will also include a DEVICES object.
	 *
	 * The device name is defaulted to null
	 *
	 * @param enable   enable/disable watch mode
	 * @param dumpData enable/disable dumping of data
	 * @return {@link WatchObject}
	 * @throws IOException   on IO error in socket
	 * @throws JSONException
	 */
	public WatchObject watch(final boolean enable, final boolean dumpData) throws IOException, JSONException {
		return this.watch(enable, dumpData, null);
	}

	/**
	 * Construct a JSONObject with a WATCH command using the parameters for WATCH enable, JSON enable
	 * and a device name parameters. The device name can be null.
	 *
	 * This command sets watcher mode. It also sets or elicits a report of per-subscriber
	 * policy and the raw bit. An argument WATCH object changes the subscriber's policy.
	 * The response describes the subscriber's policy. The response will also include a DEVICES object.
	 *
	 * In watcher mode, GPS reports are dumped as TPV and SKY responses.
	 *
	 * Other possible enables flags are for:
	 * 		nmea (default false), raw (default false), scaled (default false), split24 (default false), pps (default false)
	 * 		URL for the remote daemon. Defaults for the local daemon.
	 *
	 * @param enable   enable/disable watch mode
	 * @param dumpData enable/disable dumping of data
	 * @param device   If present, enable watching only of the specified device rather than all devices
	 * @return {@link WatchObject}
	 * @throws IOException   on IO error in socket
	 * @throws JSONException
	 */
	public WatchObject watch(final boolean enable, final boolean dumpData, final String device) throws IOException, JSONException {
		JSONObject watch = new JSONObject();
		watch.put("class", "WATCH");
		watch.put("enable", enable);
		watch.put("json", dumpData);
		if (device != null) {
			watch.put("device", device);
		}
		return this.syncCommand("?WATCH=" + watch.toString(), WatchObject.class);
	}

	/**
	 * Constructs a JSONObject with a Poll command. Device must already be in WATCH mode for POLL to be valid.
	 *
	 * @return {@link PollObject}
	 * @throws IOException on IO error in socket
	 */
	public PollObject poll() throws IOException {
		return this.syncCommand("?POLL;", PollObject.class);
	}

	/**
	 * Constructs a JSONObject with a Poll VERSION command. GPSD will respond with
	 * the VERSION response.
	 *
	 * Note that GPSD will also send a VERSION response when a connection is first made.
	 *
	 * @return {@link VersionObject}
	 * @throws IOException on IO error in socket
	 */
	public VersionObject version() throws IOException {
		return this.syncCommand("?VERSION;", VersionObject.class);
	}

	/**
	 * Add a Listener for data coming from GPSD.
	 *
	 * @param listener the listener to add
	 */
	public void addListener(final IObjectListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Remove a listener from GPSD.
	 *
	 * @param listener the listener to remove
	 */
	public void removeListener(final IObjectListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Send command to GPSd and wait for a response of a specific class message.
	 *
	 * @param command is a JSON constructed command
	 * @param responseClass is the class of message we expect in response.
	 * @returns the message received
	 */
	private <T extends IGPSObject> T syncCommand(final String command, final Class<T> responseClass) throws IOException {
		synchronized (this.asyncMutex) {
			if (out != null) {
				this.out.write(command + "\n");
				this.out.flush();
			}
			if (responseClass == WatchObject.class) {
				lastWatch = command;
			}
			int endLoop = 5;
			while (endLoop > 0) {
				// wait for the specific message
				// FIXME it is possible that the expected result is overwritten before getting to this point.
				// the loop counter will prevent an infinite loop but we still won't get our expected message.
				final IGPSObject result = this.waitForResult();
				if ((result == null) || result.getClass().equals(responseClass)) {
					return responseClass.cast(result);
				}
				endLoop--;
			}
			return(null);
		}
	}

	/**
	 * Send command without waiting for a response.
	 *
	 * @param command is a JSON constructed command.
	 */
	private void voidCommand(final String command) throws IOException {
		synchronized (this.asyncMutex) {
			this.out.write(command + "\n");
			this.out.flush();
		}
	}

	/**
	 * wait for a response for one second
	 *
	 * @return an IGPSObject with the response. Response can be null.
	 */
	private IGPSObject waitForResult() {
		synchronized (this.asyncWaitMutex) {
			if (this.asyncResult == null) {
				try {
					this.asyncWaitMutex.wait(1000);
				} catch (final InterruptedException e) {
					System.err.println("INFO: GPSdEndpoint - Interrupted while waiting for result" + e);
				}
			}
			final IGPSObject result = this.asyncResult;
			this.asyncResult = null;
			return result;
		}
	}

	/**
	 * Determine the class of the message and dispatch it to specific listeners.
	 *
	 * @param object an IGPSObject with the message
	 * @return void
	 */
	void handle(final IGPSObject object) {
		if (object instanceof TPVObject) {
			for (final IObjectListener l : this.listeners) {
				l.handleTPV((TPVObject) object);
			}
		} else if (object instanceof SKYObject) {
			for (final IObjectListener l : this.listeners) {
				l.handleSKY((SKYObject) object);
			}
		} else if (object instanceof ATTObject) {
			for (final IObjectListener l : this.listeners) {
				l.handleATT((ATTObject) object);
			}
		} else if (object instanceof SUBFRAMEObject) {
			for (final IObjectListener l : this.listeners) {
				l.handleSUBFRAME((SUBFRAMEObject) object);
			}
		} else if (object instanceof DevicesObject) {
			for (final IObjectListener l : this.listeners) {
				l.handleDevices((DevicesObject) object);
			}
		} else if (object instanceof DeviceObject) {
			for (final IObjectListener l : this.listeners) {
				l.handleDevice((DeviceObject) object);
			}
		} else {
			// object was requested, so put it in the response object
			synchronized (this.asyncWaitMutex) {
				this.asyncResult = object;
				this.asyncWaitMutex.notifyAll();
			}
		}
	}

	/**
	 * Attempt to kick a failed device back into life on gpsd server.
	 * <p>
	 * see: https://lists.gnu.org/archive/html/gpsd-dev/2015-06/msg00001.html
	 *
	 * @param path Path of device known to gpsd
	 * @throws IOException
	 * @throws JSONException
	 */
	public void kickDevice(String path) throws IOException, JSONException {
		final JSONObject d = new JSONObject();
		d.put("class", "DEVICE");
		d.put("path", path);
		this.voidCommand("?DEVICE=" + d);
	}

	/**
	 * Our socket thread got disconnect and is exiting.
	 */
	void handleDisconnected() throws IOException {
		synchronized (this.asyncMutex) {
			if (socket != null) {
				socket.close();
			}
			this.socket = new Socket(server, port);
			this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
			this.out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));

			this.listenThread = new SocketThread(this.in, this, this.resultParser, this.daemon);
			this.listenThread.start();
			if (lastWatch != null) { // restore watch if we had one.
				this.syncCommand(lastWatch, WatchObject.class);
			}
		}

	}

	/**
	 * Set a retry interval for reconnecting to GPSD if the socket closes.
	 * Default value is 1000ms.
	 *
	 * @param millis how long to wait between each reconnection attempts.
	 */
	public void setRetryInterval(long millis) {
		retryInterval.set(millis);
	}

	/**
	 * Returns the retry interval for reconnecting to GPSD if the socket closes.
	 * Default value is 1000ms.
	 *
	 * @return retry interval
	 */
	public long getRetryInterval() {
		return retryInterval.get();
	}

}
