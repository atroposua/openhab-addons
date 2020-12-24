/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.adb.internal;

import static org.openhab.binding.adb.internal.ADBBindingConstants.*;

import java.io.File;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.OpenHAB;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ADBHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Atropos - Initial contribution
 */
@NonNullByDefault
public class ADBHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ADBHandler.class);
    private @Nullable String keyFolderName;
    private @Nullable ADBConfiguration config;
    private @Nullable WorkerThread mWorkerThread;

    public ADBHandler(Thing thing) {
        super(thing);
    }

    private boolean mIsDisplayOn;

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (DISPLAY.equals(channelUID.getId())) {
            if (command instanceof RefreshType) {
                updateState(channelUID, OnOffType.from(mIsDisplayOn));
            } else if (command instanceof OnOffType) {
                if (mWorkerThread != null)
                    mWorkerThread.connection.setDisplay(command == OnOffType.ON ? true : false);
            }
        }
    }

    private void createKeysFolder() {
        keyFolderName = OpenHAB.getUserDataFolder() + File.separator + "adb_binding_keys";
        logger.info(keyFolderName);
        File folder = new File(keyFolderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    @Override
    public void initialize() {
        // logger.debug("Start initializing!");
        config = getConfigAs(ADBConfiguration.class);
        createKeysFolder();
        updateStatus(ThingStatus.UNKNOWN);
        if (mWorkerThread == null) {
            mWorkerThread = new WorkerThread(new ADBConnection(config.host, config.port, keyFolderName));
            mWorkerThread.start();
        }
    }

    @Override
    public void handleRemoval() {
        this.stop();
        updateStatus(ThingStatus.REMOVED);
    }

    private void stop() {
        if (this.mWorkerThread != null)
            this.mWorkerThread.interrupt();
    }

    private class WorkerThread extends Thread {
        ADBConnection connection;

        public ADBConnection getConnection() {
            return connection;
        }

        public WorkerThread(ADBConnection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            logger.info("Started worker thread.");
            while (true) {
                try {

                    boolean isOnline = connection.ping();
                    updateStatus(isOnline ? ThingStatus.ONLINE : ThingStatus.OFFLINE);

                    if (isOnline) {
                        ADBHandler.this.mIsDisplayOn = connection.isDisplayOn();
                        updateState(ADBBindingConstants.DISPLAY,
                                ADBHandler.this.mIsDisplayOn ? OnOffType.ON : OnOffType.OFF);
                    }

                    Thread.sleep(30000);
                } catch (Exception ex) {

                }
            }
        }
    }
}
