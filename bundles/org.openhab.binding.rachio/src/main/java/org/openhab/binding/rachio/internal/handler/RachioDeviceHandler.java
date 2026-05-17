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
import static org.openhab.binding.rachio.internal.RachioUtils.getTimestamp;

import java.math.BigDecimal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioBindingConstants;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioDevice;
import org.openhab.binding.rachio.internal.api.RachioZone;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioZoneStatus;
import org.openhab.binding.rachio.internal.api.json.RachioEventGsonDTO;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioDeviceHandler} is responsible for handling commands, which are
 * sent to one of the device channels.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioDeviceHandler extends AbstractRachioThingHandler {
    private final Logger logger = LoggerFactory.getLogger(RachioDeviceHandler.class);

    @Nullable
    RachioDevice dev;

    public RachioDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        thingId = getThing().getUID().getAsString();
        logger.debug("Initializing Rachio Thing '{}'.", thingId);

        String errorMessage = "";
        ThingStatusDetail errorStatusDetail = ThingStatusDetail.COMMUNICATION_ERROR;
        String configuredDeviceId = getThingConfigurationString(PROPERTY_DEV_ID);
        try {
            if (!initializeCloudHandler()) {
                errorMessage = "Rachio bridge is not initialized";
                return;
            }

            RachioBridgeHandler handler = cloudHandler;
            dev = resolveDevice(handler, configuredDeviceId);
            RachioDevice d = dev;
            if (d == null || handler == null) {
                errorMessage = buildDeviceResolutionError(configuredDeviceId);
                errorStatusDetail = ThingStatusDetail.CONFIGURATION_ERROR;
                return;
            }

            thingId = d.name;
            d.setThingHandler(this);
            handler.registerStatusListener(this);
            handler.registerWebHook(d.id);
            if (configuredDeviceId.isBlank()) {
                logger.debug(
                        "Rachio controller Thing '{}' used legacy UID/property mapping. Configure deviceId='{}' to decouple the openHAB Thing ID from the Rachio controller UUID.",
                        getThing().getUID(), d.id);
            }
            if (!isBridgeOnline()) {
                logger.debug("{}: Rachio Bridge is offline!", thingId);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            } else {
                goOnline();
                logger.debug("{}: Device {} initialized.", thingId, d.name);
                return;
            }
        } catch (RachioApiException e) {
            errorMessage = e.toString();
        } catch (RuntimeException e) {
            if (e.getMessage() != null) {
                errorMessage = e.getMessage();
            }
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.warn("{}: ERROR: {}", thingId, errorMessage);
                updateStatus(ThingStatus.OFFLINE, errorStatusDetail, errorMessage);
            }
        }
    }

    private @Nullable RachioDevice resolveDevice(@Nullable RachioBridgeHandler handler, String configuredDeviceId) {
        if (handler == null) {
            return null;
        }

        if (!configuredDeviceId.isBlank()) {
            logger.debug("Resolving Rachio controller Thing '{}' by configured deviceId '{}'", getThing().getUID(),
                    configuredDeviceId);
            return handler.getDevByConfiguredDeviceId(getThing(), configuredDeviceId);
        }

        logger.debug("Rachio controller Thing '{}' has no configured deviceId; trying legacy property/UID fallback",
                getThing().getUID());
        return handler.getDevByThing(getThing());
    }

    private String buildDeviceResolutionError(String configuredDeviceId) {
        if (configuredDeviceId.isBlank()) {
            return "Missing Rachio deviceId. Add the controller via Inbox discovery or configure the Rachio API controller UUID manually.";
        }
        return "Configured Rachio deviceId was not found in the account: '" + configuredDeviceId + "'.";
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        logger.debug("{}: Handle Command {} for channel {}", thingId, command, channel);

        RachioBridgeHandler handler = cloudHandler;
        RachioDevice d = dev;
        if ((handler == null) || (d == null)) {
            logger.debug("{}: Cloud handler or device not initialized!", thingId);
            return;
        }

        String errorMessage = "";
        try {
            if (command == RefreshType.REFRESH) {
                if (handleRefreshCommand(channel)) {
                    logger.debug("{}: Return cached data for channel {}: {}", thingId, channel,
                            channelData.get(channel));
                }
                return;
            }

            if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_ACTIVE)) {
                if (command instanceof OnOffType) {
                    if (command == OnOffType.OFF) {
                        logger.debug("{}: Pause device {} (disable watering, schedules etc.)", thingId, d.name);
                        handler.disableDevice(d.id);
                    } else {
                        logger.debug("{}: Resume device {} (enable watering, schedules etc.)", thingId, d.name);
                        handler.enableDevice(d.id);
                    }
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_RUN_TIME)) {
                if (command instanceof DecimalType) {
                    int runtime = ((DecimalType) command).intValue();
                    logger.debug("Default Runtime for zones set to {} sec", runtime);
                    d.setRunTime(runtime);
                } else {
                    logger.debug("Command value is no DecimalType: {}", command);
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_PAUSE_TIME)) {
                if (command instanceof DecimalType) {
                    int duration = ((DecimalType) command).intValue();
                    d.setPauseDuration(duration);
                    logger.debug("Pause duration for active zone runs set to {} sec", d.getPauseDuration());
                    updateChannel(RachioBindingConstants.CHANNEL_DEVICE_PAUSE_TIME,
                            new DecimalType(new BigDecimal(d.getPauseDuration()).toString()));
                } else {
                    logger.debug("Command value is no DecimalType: {}", command);
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_RUN_ZONES)) {
                if (command instanceof StringType) {
                    logger.debug("Run multiple zones: '{}' ('' = ALL)", command.toString());
                    d.setRunZones(command.toString());
                } else {
                    logger.debug("Command value is no StringType: {}", command);
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_RUN)) {
                if (command == OnOffType.ON) {
                    int defaultRuntime = handler.getDefaultRuntime();
                    int controllerRuntime = d.getRunTime();
                    int effectiveRuntime = d.getMultiZoneRunTime(defaultRuntime);
                    logger.debug(
                            "Starting multiple zones '{}' with controller runtime {} sec (fallback default {} sec, effective {} sec)",
                            d.getRunZones(), controllerRuntime, defaultRuntime, effectiveRuntime);
                    handler.runMultipleZones(d.getAllRunZonesJson(defaultRuntime));
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_STOP)) {
                if (command == OnOffType.ON) {
                    logger.info("STOP watering for device '{}'", d.name);
                    handler.stopWatering(d.id);
                    updateState(RachioBindingConstants.CHANNEL_DEVICE_STOP, OnOffType.OFF);
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_RAIN_DELAY)) {
                if (command instanceof DecimalType) {
                    logger.info("Start rain delay cycle for {} sec", command.toString());
                    d.setRainDelayTime(((DecimalType) command).intValue());
                    handler.startRainDelay(d.id, ((DecimalType) command).intValue());
                } else {
                    logger.debug("Command value is no DecimalType: {}", command);
                }
            } else if (channel.equals(RachioBindingConstants.CHANNEL_DEVICE_PAUSED)) {
                if (command == OnOffType.ON) {
                    logger.info("Pause active zone run for device '{}' for {} sec", d.name, d.getPauseDuration());
                    handler.pauseZoneRun(d.id, d.getPauseDuration());
                    d.setPaused(true);
                    updateChannel(RachioBindingConstants.CHANNEL_DEVICE_PAUSED, OnOffType.ON);
                } else if (command == OnOffType.OFF) {
                    logger.info("Resume active zone run for device '{}'", d.name);
                    handler.resumeZoneRun(d.id);
                    d.setPaused(false);
                    updateChannel(RachioBindingConstants.CHANNEL_DEVICE_PAUSED, OnOffType.OFF);
                }
            }
        } catch (RachioApiException e) {
            errorMessage = e.toString();
        } catch (RuntimeException e) {
            errorMessage = e.getMessage();
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.debug("ERROR: {}", errorMessage);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorMessage);
            }
        }
    }

    @Override
    protected void postChannelData() {
        RachioDevice d = dev;
        if (d != null) {
            logger.debug("Updating  status");
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_NAME, new StringType(d.getThingName()));
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_ONLINE, d.getOnline());
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_ACTIVE, d.getEnabled());
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_PAUSED, d.getPaused());
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_PAUSE_TIME,
                    new DecimalType(new BigDecimal(d.getPauseDuration()).toString()));
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_SLEEP_MODE, d.getSleepMode());
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RUN_ZONES, new StringType(d.getRunZones()));
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RUN_TIME,
                    new DecimalType(new BigDecimal(d.getRunTime()).toString()));
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RAIN_DELAY,
                    new DecimalType(new BigDecimal(d.rainDelay).toString()));
            updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RAIN_STRIPPED,
                    d.rainSensorTripped ? OnOffType.ON : OnOffType.OFF);
            updateChannel(RachioBindingConstants.CHANNEL_LAST_EVENT, new StringType(d.getEvent()));
            DateTimeType ts = d.getEventTime();
            updateChannel(RachioBindingConstants.CHANNEL_LAST_EVENTTS, ts != null ? ts : UnDefType.UNDEF);
        }
    }

    @Override
    public void onConfigurationUpdated() {
        try {
            RachioBridgeHandler handler = cloudHandler;
            RachioDevice d = dev;
            if (handler != null && d != null) {
                handler.registerWebHook(d.id);
            }
        } catch (RachioApiException e) {
            logger.debug("{}: Unable to renew webhook registration: {}", thingId, e.toString());
        }
    }

    @Override
    public boolean onThingStateChangedl(@Nullable RachioDevice updatedDev, @Nullable RachioZone updatedZone) {
        RachioDevice d = dev;
        if ((updatedDev != null) && (d != null) && d.id.equals(updatedDev.id)) {
            logger.debug("Update for device '{}' received.", d.id);
            dev.update(updatedDev);
            updateChannel(CHANNEL_LAST_UPDATE, getTimestamp());
            postChannelData();
            updateStatus(d.getStatus());
            return true;
        }
        return false;
    }

    @Override
    public void shutdown() {
        if (dev != null) {
            dev.setStatus("OFFLINE");
        }
        super.shutdown();
    }

    @Override
    protected void goOnline() {
        updateProperties();
        postChannelData();
        RachioDevice d = dev;
        if (d != null) {
            updateStatus(d.getStatus());
        }
    }

    @Override
    protected void onBridgeOnline() {
        if (dev == null) {
            logger.debug("Bridge is ONLINE; retrying controller initialization for '{}'", getThing().getUID());
            initialize();
        } else {
            goOnline();
        }
    }

    public boolean webhookEvent(RachioEventGsonDTO event) {
        boolean update = true;
        RachioDevice d = dev;
        if (d == null) {
            return false;
        }

        try {
            String etype = event.type;
            RachioZone zone = null;
            if (etype.equals("ZONE_STATUS")) {
                RachioZoneStatus runStatus = event.zoneRunStatus;
                if (runStatus != null) {
                    zone = d.getZoneByNumber(runStatus.zoneNumber);
                }
            } else if (event.subType.equals("ZONE_DELTA")) {
                zone = d.getZoneById(event.zoneId);
            }

            boolean zoneUpdated = false;
            if (zone != null) {
                RachioZoneHandler handler = zone.getThingHandler();
                if (handler != null) {
                    zoneUpdated = handler.webhookEvent(event);
                }
            }

            boolean devicePauseChanged = false;
            if (etype.equals("ZONE_STATUS")) {
                String state = event.zoneRunStatus != null ? event.zoneRunStatus.state : event.subType;
                if ("ZONE_CYCLING".equals(state)) {
                    if (!d.paused) {
                        logger.info("{}: Device detected external pause for zone {}.", thingId,
                                zone != null ? zone.name : event.zoneName);
                        d.setPaused(true);
                        devicePauseChanged = true;
                    }
                } else if ("ZONE_STARTED".equals(state) || "ZONE_STOPPED".equals(state)
                        || "ZONE_COMPLETED".equals(state) || "ZONE_CYCLING_COMPLETED".equals(state)) {
                    if (d.paused) {
                        logger.info("{}: Device detected external resume for zone {}.", thingId,
                                zone != null ? zone.name : event.zoneName);
                        d.setPaused(false);
                        devicePauseChanged = true;
                    }
                }
            }

            if (zoneUpdated && !devicePauseChanged) {
                return true;
            }

            String evt = event.subType.isEmpty() ? event.type : event.subType;
            dev.setEvent(evt, getTimestamp()); // and funnel all zone events to the device
            if (event.subType.equals("RAIN_DELAY_ON")) {
                handleRainDelayOnEvent(event, d);
            } else if (event.subType.equals("RAIN_DELAY_OFF")) {
                logger.info("{}: Device reported Rain Delay OFF.", thingId);
                d.setRainDelayTime(0);
            } else if (etype.equals("DEVICE_STATUS")) {
                // sub types:
                // COLD_REBOOT, ONLINE, OFFLINE, OFFLINE_NOTIFICATION, SLEEP_MODE_ON, SLEEP_MODE_OFF, BROWNOUT_VALVE
                // RAIN_SENSOR_DETECTION_ON, RAIN_SENSOR_DETECTION_OFF, RAIN_DELAY_ON, RAIN_DELAY_OFF
                logger.debug("Device {} ('{}') changed to status '{}'.", d.name, d.id, event.subType);
                if (event.subType.equals("COLD_REBOOT")) {
                    if (event.network != null) {
                        dev.setNetwork(event.network);
                    }
                    if (d.network != null) {
                        String networkDetails = String.format("ip=%s/%s, gw=%s, dns=%s/%s, wifi rssi=%s", d.network.ip,
                                d.network.nm, d.network.gw, d.network.dns1, d.network.dns2, d.network.rssi);
                        logger.info("{}: Device {} was restarted, {}.", thingId, d.name, networkDetails);
                    } else {
                        logger.info("{}: Device {} was restarted (network information unavailable).", thingId, d.name);
                    }
                } else if (event.subType.equals("ONLINE")) {
                    logger.info("{}: Device is ONLINE.", thingId);
                    dev.setStatus(event.subType);
                } else if (event.subType.equals("OFFLINE") || event.subType.equals("OFFLINE_NOTIFICATION")) {
                    logger.info("{}: Device is OFFLINE (subType = '{}').", thingId, event.subType);
                    dev.setStatus(event.subType);
                } else if (event.subType.equals("SLEEP_MODE_ON")) {
                    logger.info("{}: Device switch to sleep mode.", thingId);
                    dev.setSleepMode(event.subType);
                } else if (event.subType.equals("SLEEP_MODE_OFF")) {
                    logger.info("{}: Device was resumed (exit from sleep mode).", thingId);
                    dev.setSleepMode(event.subType);
                } else if (event.subType.equals("RAIN_SENSOR_DETECTION_ON")) {
                    logger.info("{}: Device reported Rain Sensor ON.", thingId);
                    d.rainSensorTripped = true;
                } else if (event.subType.equals("RAIN_SENSOR_DETECTION_OFF")) {
                    logger.info("{}: Device reported Rain Sensor OFF.", thingId);
                    d.rainSensorTripped = false;
                } else {
                    update = false; // details missing
                }
            } else if (event.type.equals("SCHEDULE_STATUS")) {
                logger.info("{}: Status {} for schedule {}: {} (start={}, end={}, duration={}min)", thingId,
                        event.subType, event.scheduleName, event.summary, event.startTime, event.endTime,
                        event.durationInMinutes);
                updateChannel(CHANNEL_SCHED_NAME, new StringType(event.scheduleName));
                updateChannel(CHANNEL_SCHED_INFO, new StringType(event.summary));
                if (!event.startTime.isEmpty()) {
                    updateChannel(CHANNEL_SCHED_START, new DateTimeType(event.startTime));
                }
                if (!event.endTime.isEmpty()) {
                    updateChannel(CHANNEL_SCHED_END, new DateTimeType(event.endTime));
                }
            } else {
                update = false; // unknown event
            }

            if (update) {
                postChannelData();
                updateChannel(CHANNEL_LAST_UPDATE, getTimestamp());
                return true;
            }
            logger.debug("{}: Unhandled event {}.{} for device {} ({}): {}", thingId, event.type, event.subType, d.name,
                    d.id, event.summary);
            return false;
        } catch (RuntimeException e) {
            logger.debug("{}: Unable to process event {}.{} - {}", thingId, event.type, event.subType, event.summary,
                    e);
            return false;
        }
    }

    private void handleRainDelayOnEvent(RachioEventGsonDTO event, RachioDevice device) {
        int rainDelaySeconds = event.getRainDelaySecondsRemaining();
        if (rainDelaySeconds >= 0) {
            logger.info("{}: Device reported Rain Delay ON for {} sec.", thingId, rainDelaySeconds);
            device.setRainDelayTime(rainDelaySeconds);
        } else {
            logger.info("{}: Device reported Rain Delay ON without duration details; refreshing device state.",
                    thingId);
            refreshRainDelayState();
        }
    }

    private void refreshRainDelayState() {
        RachioBridgeHandler handler = cloudHandler;
        if (handler != null) {
            handler.refreshDeviceStatus(RachioBridgeHandler.RefreshReason.WEBHOOK_RECONCILIATION);
        } else {
            logger.debug("{}: Unable to refresh rain delay state because cloud handler is not initialized.", thingId);
        }
    }

    private void updateProperties() {
        RachioDevice d = dev;
        if (d != null) {
            logger.trace("{}: Updating device properties", thingId);
            updateProperties(d.fillProperties());
        }
    }
}
