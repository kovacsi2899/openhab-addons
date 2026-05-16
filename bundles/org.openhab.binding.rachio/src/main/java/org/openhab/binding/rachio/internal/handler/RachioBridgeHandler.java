/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.rachio.internal.handler;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;
import static org.openhab.binding.rachio.internal.RachioUtils.getString;

import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioConfiguration;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioDevice;
import org.openhab.binding.rachio.internal.api.RachioZone;
import org.openhab.binding.rachio.internal.api.json.RachioEventGsonDTO;
import org.openhab.binding.rachio.internal.discovery.RachioDiscoveryService;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.PRIORITY;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioBridgeHandler} is responsible for implementing the cloud api access.
 * The concept of a Bridge is used. In general multiple bridges are supported using different API keys.
 * Devices are linked to the bridge. All devices and zones go offline if the cloud api access fails.
 *
 * @author Markus Michels - initial contribution
 */
@NonNullByDefault
public class RachioBridgeHandler extends AbstractRachioBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(RachioBridgeHandler.class);
    private final RachioApi rachioApi;
    private RachioConfiguration bindingConfig = new RachioConfiguration();
    private RachioConfiguration thingConfig = new RachioConfiguration();
    private String personId = "";

    public enum RefreshReason {
        SCHEDULED_POLL,
        WEBHOOK_RECONCILIATION,
        INITIALIZATION,
        MANUAL;
    }

    /**
     * Thing Handler for the Bridge thing. Handles the cloud connection and links devices+zones to a bridge.
     * Creates an instance of the RachioApi (holding all RachioDevices + RachioZones for the given apikey)
     *
     * @param bridge: Bridge class object
     */
    public RachioBridgeHandler(final Bridge bridge) {
        super(bridge);
        rachioApi = new RachioApi(personId);
    }

    public void setConfiguration(RachioConfiguration defaultConfig) {
        bindingConfig = defaultConfig;
    }

    /**
     * Initialize the bridge/cloud handler. Creates a connection to the Rachio Cloud, reads devices + zones and
     * initialized the Thing mapping.
     */
    @Override
    public void initialize() {
        String errorMessage = "";

        try {
            // Set defaults from Binding Config
            thingConfig = bindingConfig;
            thingConfig.updateConfig(getConfig().getProperties());

            logger.debug("RachioCloud: Connecting to Rachio Cloud");
            createCloudConnection(rachioApi, RefreshReason.INITIALIZATION);
            updateProperties();

            // Pass BridgeUID to device, RachioDeviceHandler will fill DeviceUID
            Bridge bridgeThing = this.getThing();
            HashMap<String, RachioDevice> deviceList = getDevices();
            for (HashMap.Entry<String, RachioDevice> de : deviceList.entrySet()) {
                RachioDevice dev = de.getValue();
                ThingUID devThingUID = new ThingUID(THING_TYPE_DEVICE, bridgeThing.getUID(), dev.getThingID());
                dev.setUID(this.getThing().getUID(), devThingUID);
                // Set DeviceUID for all zones
                HashMap<String, RachioZone> zoneList = dev.getZones();
                for (HashMap.Entry<String, RachioZone> ze : zoneList.entrySet()) {
                    RachioZone zone = ze.getValue();
                    ThingUID zoneThingUID = new ThingUID(THING_TYPE_ZONE, bridgeThing.getUID(), zone.getThingID());
                    zone.setUID(dev.getUID(), zoneThingUID);
                }
            }

            logger.info("RachioCloud: Connector initialized");
            updateStatus(ThingStatus.ONLINE);
        } catch (RachioApiException e) {
            errorMessage = e.toString();
            if (e.getApiResult().isResponseRateLimit()) {
                logger.warn("RachioCloud: Account is blocked due to rate limit, wait 24h and retry");
            }
        } catch (UnknownHostException e) {
            errorMessage = "Unknown Host or Internet connection down";
        } catch (RuntimeException e) {
            errorMessage = getString(e.getMessage());
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.debug("RachioCloud: {}", errorMessage);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorMessage);
            }
        }
    }

    /**
     * This routine is called every time the Thing configuration has been changed
     */
    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        rachioStatusListeners.stream().forEach(l -> l.onConfigurationUpdated());
    }

    /**
     * Get the services registered for this bridge. Provides the discovery service.
     */
    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(RachioDiscoveryService.class);
    }

    /**
     * Handle Thing commands - the bridge doesn't implement any commands
     */
    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        // cloud handler has no channels
        logger.debug("RachioCloud: Command {} for {} ignored", command, channelUID.getAsString());
    }

    /**
     * Update device status (poll Rachio Cloud)
     * in addition webhooks are used to get events (if callbackUrl is configured)
     */
    public void refreshDeviceStatus() {
        refreshDeviceStatus(RefreshReason.MANUAL);
    }

    /**
     * Update device status (poll Rachio Cloud)
     * in addition webhooks are used to get events (if callbackUrl is configured)
     */
    public void refreshDeviceStatus(RefreshReason refreshReason) {
        String errorMessage = "";
        logger.trace("RachioCloud: refreshDeviceStatus ({})", refreshReason);

        try {
            if (!beginRefresh()) {
                logger.debug("RachioCloud: Already checking");
                return;
            }

            HashMap<String, RachioDevice> deviceList = getDevices();
            if (deviceList == null) {
                logger.debug("RachioCloud: Cloud access not initialized yet!");
                return;
            }

            RachioApi checkApi = new RachioApi(personId);
            createCloudConnection(checkApi, refreshReason);
            if (checkApi.getLastApiResult().isRateLimitBlocked()) {
                String errorCritical = "";
                errorCritical = MessageFormat.format(
                        "RachioCloud: API access blocked on update ({0} / {1}), reset at {2}",
                        checkApi.getLastApiResult().rateRemaining, checkApi.getLastApiResult().rateLimit,
                        checkApi.getLastApiResult().rateReset);
                logger.debug("{}", errorCritical);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorCritical); // shutdown
                                                                                                         // bridge+devices+zones
                return;
            }
            if (this.getThing().getStatus() != ThingStatus.ONLINE) {
                logger.debug("RachioCloud: Bridge is ONLINE");
                updateStatus(ThingStatus.ONLINE);
            }

            HashMap<String, RachioDevice> checkDevList = checkApi.getDevices();
            for (HashMap.Entry<String, RachioDevice> de : checkDevList.entrySet()) {
                RachioDevice checkDev = de.getValue();
                RachioDevice dev = deviceList.get(checkDev.id);
                if (dev == null) {
                    logger.info("RachioCloud: New device detected: {} - {}", checkDev.id, checkDev.name);
                } else {
                    if (!dev.compare(checkDev)) {
                        logger.trace("RachioCloud: Update data for device {}", dev.name);
                        if (dev.getThingHandler() != null) {
                            dev.getThingHandler().onThingStateChangedl(checkDev, null);
                        } else {
                            notifyThingStateChanged(checkDev, null);
                        }
                    } else {
                        logger.trace("RachioCloud: Device {} was not updaterd", checkDev.id);
                    }

                    HashMap<String, RachioZone> zoneList = dev.getZones();
                    HashMap<String, RachioZone> checkZoneList = checkDev.getZones();
                    for (HashMap.Entry<String, RachioZone> ze : checkZoneList.entrySet()) {
                        RachioZone checkZone = ze.getValue();
                        RachioZone zone = zoneList.get(checkZone.id);
                        if (zone == null) {
                            logger.debug("RachioCloud: New zone detected: {} - {}", checkDev.id, checkZone.name);
                        } else {
                            if (!zone.compare(checkZone)) {
                                logger.trace("RachioCloud: Update status for zone {}", zone.name);
                                if (zone.getThingHandler() != null) {
                                    zone.getThingHandler().onThingStateChangedl(null, checkZone);
                                } else {
                                    notifyThingStateChanged(null, checkZone);
                                }
                            } else {
                                logger.trace("RachioCloud: Zone {} was not updated.", checkZone.id);
                            }
                        }
                    }
                    // Sync the zoneList with the new state
                    zoneList.keySet().retainAll(checkZoneList.keySet());
                    for (HashMap.Entry<String, RachioZone> entry : checkZoneList.entrySet()) {
                        if (!zoneList.containsKey(entry.getKey())) {
                            zoneList.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } catch (RachioApiException e) {
            errorMessage = e.toString();
        } catch (RuntimeException | UnknownHostException e) {
            errorMessage = getString(e.getMessage());
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.debug("RachioBridge: {}", errorMessage);
            }
            endRefresh();
        }
    }

    @Override
    public void shutdown() {
        logger.info("RachioCloud: Shutting down");
        super.shutdown();
    }

    /**
     * Create a new Rachio cloud service connection. If a connection already exists, it will be replaced.
     *
     * @throws RachioApiException if there is an error while authenticating to the service
     */
    private void createCloudConnection(RachioApi api, RefreshReason refreshReason)
            throws RachioApiException, UnknownHostException {
        if (thingConfig.apikey.isEmpty()) {
            throw new RachioApiException(
                    "RachioCloud: Unable to connect to Rachio Cloud: apikey not set, check services/rachio.cfg!");
        }

        // initialiaze API access, may throw an exception
        api.initialize(thingConfig.apikey, this.getThing().getUID(), getPriority(refreshReason));
        personId = api.getPersonId();
    }

    private PRIORITY getPriority(RefreshReason refreshReason) {
        switch (refreshReason) {
            case SCHEDULED_POLL:
                return PRIORITY.LOW;
            case WEBHOOK_RECONCILIATION:
            case INITIALIZATION:
            case MANUAL:
            default:
                return PRIORITY.MED;
        }
    }

    /**
     * puts the device into standby mode = disable watering, schedules etc.
     *
     * @param deviceId: Device (ID retrieved from initialization)
     * @return true: successful, failed (check http error code)
     */
    public void disableDevice(String deviceId) throws RachioApiException {
        rachioApi.disableDevice(deviceId);
    }

    /**
     * puts the device into run mode = watering, schedules etc.
     *
     * @param deviceId: Device (ID retrieved from initialization)
     * @return true: successful, failed (check http error code)
     */
    public void enableDevice(String deviceId) throws RachioApiException {
        rachioApi.enableDevice(deviceId);
    }

    /**
     * Stop watering for all zones, disable schedule etc. - puts the device into standby mode
     *
     * @param deviceId: Device (ID retrieved from initialization)
     * @return true: successful, failed (check http error code)
     * @return
     */
    public void stopWatering(String deviceId) throws RachioApiException {
        rachioApi.stopWatering(deviceId);
    }

    /**
     * Start rain delay cycle.
     *
     * @param deviceId: Device (ID retrieved from initialization)
     * @param delayTime: Number of seconds for the rain delay cycle
     * @return true: successful, failed (check http error code)
     */
    public void startRainDelay(String deviceId, int delayTime) throws RachioApiException {
        rachioApi.rainDelay(deviceId, delayTime);
    }

    /**
     * Pause the active zone run for a device.
     *
     * @param deviceId Device (ID retrieved from initialization)
     * @param duration Number of seconds to pause the active run
     * @throws RachioApiException if the API call fails
     */
    public void pauseZoneRun(String deviceId, int duration) throws RachioApiException {
        rachioApi.pauseZoneRun(deviceId, duration);
    }

    /**
     * Resume the active zone run for a device.
     *
     * @param deviceId Device (ID retrieved from initialization)
     * @throws RachioApiException if the API call fails
     */
    public void resumeZoneRun(String deviceId) throws RachioApiException {
        rachioApi.resumeZoneRun(deviceId);
    }

    /**
     * Start watering for multiple zones.
     *
     * @param zoneListJson: Contains a list of { "id": n} with the zone ids to start
     * @return true: successful, failed (check http error code)
     */
    public void runMultipleZones(String zoneListJson) throws RachioApiException {
        rachioApi.runMultilpeZones(zoneListJson);
    }

    /**
     * Start a single zone for given number of seconds.
     *
     * @param zoneId: Rachio Cloud Zone ID
     * @param runTime: Number of seconds to run
     * @return true: successful, failed (check http error code)
     */
    public void startZone(String zoneId, int runTime) throws RachioApiException {
        rachioApi.runZone(zoneId, runTime);
    }

    /**
     * Enable or disable a zone.
     *
     * @param zoneId Rachio Cloud Zone ID
     * @param enabled true to enable, false to disable
     * @throws RachioApiException if the API call fails
     */
    public void setZoneEnabled(String zoneId, boolean enabled) throws RachioApiException {
        if (enabled) {
            rachioApi.enableZone(zoneId);
        } else {
            rachioApi.disableZone(zoneId);
        }
    }

    //
    // ------ Read Thing config
    //

    /**
     * Retrieve the apikey for connecting to rachio cloud
     *
     * @return the polling interval in seconds
     */
    public String getApiKey() {
        String apikey = thingConfig.apikey;
        if (!apikey.isEmpty()) {
            return apikey;
        }
        apikey = getConfigAs(RachioConfiguration.class).apikey;
        if (!apikey.isEmpty()) {
            return apikey;
        }
        Configuration config = getThing().getConfiguration();
        Object value = config.get(PARAM_APIKEY);
        return value != null ? value.toString() : "";
    }

    /**
     * Retrieve the polling interval from Thing config
     *
     * @return the polling interval in seconds
     */
    public int getPollingInterval() {
        return thingConfig.pollingInterval;
    }

    /**
     * Retrieve the callback URL for Rachio Cloud Eevents
     *
     * @return callbackUrl
     */
    public String getCallbackUrl() {
        return thingConfig.callbackUrl;
    }

    public String getCallbackUsername() {
        return thingConfig.callbackUsername;
    }

    public String getCallbackPassword() {
        return thingConfig.callbackPassword;
    }

    /**
     * Retrieve the clearAllCallbacks flag from thing config
     *
     * @return true=clear all callbacks, false=clear only the current one (avoid multiple instances)
     */
    public Boolean getClearAllCallbacks() {
        return thingConfig.clearAllCallbacks;
    }

    /**
     * Retrieve the default runtime from Thing config
     *
     * @return the polling interval in seconds
     */
    public int getDefaultRuntime() {
        return thingConfig.defaultRuntime;
    }

    //
    // ------ Stuff used by other classes
    //

    /**
     * Get the list of discovered devices (those retrieved from the Rachio Cloud)
     *
     * @return HashMap of RachioDevice
     */
    @Nullable
    public HashMap<String, RachioDevice> getDevices() {
        try {
            return rachioApi.getDevices();
        } catch (RuntimeException e) {
            logger.debug("Unable to retrieve device list", e);
        }
        return null;
    }

    /**
     * return RachioDevice by device Thing UID
     *
     * @param thingUID
     * @return RachioDevice for that device Thing UID
     */
    @Nullable
    public RachioDevice getDevByUID(@Nullable ThingUID thingUID) {
        return rachioApi.getDevByUID(getThing().getUID(), thingUID);
    }

    /**
     * return RachioZone for given Zone Thing UID
     *
     * @param thingUID
     * @return
     */
    @Nullable
    public RachioZone getZoneByUID(@Nullable ThingUID thingUID) {
        return rachioApi.getZoneByUID(getThing().getUID(), thingUID);
    }

    /**
     * Register a webhook at Rachio Cloud for the given device ID. The webhook triggers our servlet to process device
     * and zone events.
     *
     * @param deviceId: Matching device ID (as retrieved from device initialization)
     * @return true: successful, false: failed (check http error code)
     */
    public void registerWebHook(String deviceId) throws RachioApiException {
        if (getCallbackUrl().isEmpty()) {
            logger.debug("RachioCloud: No callbackUrl configured.");
        } else {
            rachioApi.registerWebHook(deviceId, getCallbackUrl(), getCallbackUsername(), getCallbackPassword(),
                    getExternalId(), getClearAllCallbacks());
        }
    }

    /**
     * Handle inbound WebHook event (dispatch to device handler)
     *
     * @param event
     * @return
     */
    public boolean webHookEvent(RachioEventGsonDTO event) {
        try {
            HashMap<String, RachioDevice> deviceList = getDevices();
            if (deviceList == null) {
                return false;
            }
            for (HashMap.Entry<String, RachioDevice> de : deviceList.entrySet()) {
                RachioDevice dev = de.getValue();
                if (dev.id.equalsIgnoreCase(event.deviceId) && (dev.getThingHandler() != null)) {
                    RachioDeviceHandler th = dev.getThingHandler();
                    if (th != null) {
                        return th.webhookEvent(event);
                    }
                }
            }
            logger.debug("RachioCloud: Event {}.{} for unknown device {}: {}", event.category, event.type,
                    event.deviceId, event.summary);
        } catch (RuntimeException e) {
            logger.debug("RachioCloud: Unable to process event {}.{} for device {}", event.category, event.type,
                    event.deviceId, e);
        }
        return false;
    }

    @Nullable
    public String getExternalId() {
        return rachioApi.getExternalId();
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        Collection<ConfigStatusMessage> configStatusMessages = new ArrayList<>();

        RachioConfiguration config = getConfigAs(RachioConfiguration.class);

        if (config.apikey.isEmpty()) {
            configStatusMessages.add(ConfigStatusMessage.Builder.error(PARAM_APIKEY)
                    .withMessageKeySuffix("ERROR: No/invalid APIKEY in binding configuration!")
                    .withArguments(PARAM_APIKEY).build());
        }

        return configStatusMessages;
    }

    private void updateProperties() {
        updateProperties(rachioApi.fillProperties());
    }

    @Override
    protected int getPollingIntervalSeconds() {
        return getPollingInterval();
    }

    @Override
    protected void runScheduledRefresh() {
        refreshDeviceStatus(RefreshReason.SCHEDULED_POLL);
    }

    @Override
    public synchronized void dispose() {
        logger.debug("RachioCloud: Disposing handler");
        super.dispose();
    }
}
