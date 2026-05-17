/**
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioDevice;
import org.openhab.binding.rachio.internal.api.RachioZone;
import org.openhab.binding.rachio.internal.api.json.RachioEventGsonDTO;
import org.openhab.binding.rachio.internal.api.json.RachioEventGsonDTO.RachioWebhookPayload;
import org.openhab.binding.rachio.internal.api.json.RachioSmartHoseTimerGsonDTO.RachioValve;
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
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for an individual Smart Hose Timer Valve.
 */
@NonNullByDefault
public class RachioValveHandler extends AbstractRachioThingHandler {
    private final Logger logger = LoggerFactory.getLogger(RachioValveHandler.class);

    @Nullable
    private RachioValve valve;
    private int runTime = 0;
    private OnOffType runState = OnOffType.OFF;
    private String lastEvent = "";
    @Nullable
    private DateTimeType lastEventTime;
    @Nullable
    private Boolean lastFlowDetected;
    private String lastRunType = "";
    private String lastEndReason = "";
    private boolean statusListenerRegistered = false;

    public RachioValveHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        thingId = getThing().getUID().getAsString();
        String valveId = getThingConfigurationOrPropertyString(PROPERTY_VALVE_ID);
        logger.debug("Initializing Rachio Valve Thing '{}', configured valveId='{}'", getThing().getUID(), valveId);

        if (valveId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Missing Rachio valveId. Add the Valve via Inbox discovery or configure the Rachio Valve UUID manually.");
            return;
        }
        if (!initializeCloudHandler()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }
        refreshValve(valveId, true);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        RachioBridgeHandler handler = cloudHandler;
        RachioValve currentValve = valve;
        if (handler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }

        if (command == RefreshType.REFRESH) {
            if (currentValve == null) {
                initialize();
            } else {
                refreshValve(currentValve.id, false);
            }
            return;
        }

        if (currentValve == null) {
            logger.debug("{}: Valve model is not initialized; command for channel '{}' ignored", thingId, channel);
            return;
        }

        String errorMessage = "";
        try {
            if (CHANNEL_VALVE_RUN_TIME.equals(channel)) {
                if (command instanceof DecimalType decimalCommand) {
                    runTime = Math.max(0, decimalCommand.intValue());
                    logger.debug("{}: Valve will start for {} sec", thingId, runTime);
                    updateChannel(CHANNEL_VALVE_RUN_TIME, new DecimalType(BigDecimal.valueOf(runTime)));
                } else {
                    logger.debug("{}: runTime command value is not numeric: {}", thingId, command);
                }
            } else if (CHANNEL_VALVE_RUN.equals(channel)) {
                if (command == OnOffType.ON) {
                    int duration = getEffectiveRunTime(currentValve, handler);
                    logger.info("{}: Start Smart Hose Timer valve '{}' for {} sec", thingId,
                            currentValve.getThingName(), duration);
                    handler.startValveWatering(currentValve.id, duration);
                    runState = OnOffType.ON;
                    updateChannel(CHANNEL_VALVE_RUN, runState);
                } else if (command == OnOffType.OFF) {
                    logger.info("{}: Stop Smart Hose Timer valve '{}'", thingId, currentValve.getThingName());
                    handler.stopValveWatering(currentValve.id);
                    runState = OnOffType.OFF;
                    updateChannel(CHANNEL_VALVE_RUN, runState);
                }
            } else if (CHANNEL_VALVE_DEFAULT_RUNTIME.equals(channel)) {
                if (command instanceof DecimalType decimalCommand) {
                    int defaultRuntime = decimalCommand.intValue();
                    if (defaultRuntime <= 0) {
                        logger.debug("{}: Invalid valve defaultRuntime {}; expected a positive number of seconds",
                                thingId, defaultRuntime);
                        return;
                    }
                    logger.info("{}: Set Smart Hose Timer valve '{}' default runtime to {} sec", thingId,
                            currentValve.getThingName(), defaultRuntime);
                    handler.setValveDefaultRuntime(currentValve.id, defaultRuntime);
                    currentValve.defaultRuntimeSeconds = defaultRuntime;
                    logger.debug(
                            "{}: ValveState.matches may remain false until the physical valve synchronizes the cloud-side default runtime update",
                            thingId);
                    postChannelData();
                } else {
                    logger.debug("{}: defaultRuntime command value is not numeric: {}", thingId, command);
                }
            }
        } catch (RachioApiException e) {
            errorMessage = e.toString();
        } catch (RuntimeException e) {
            String message = e.getMessage();
            errorMessage = message != null ? message : e.toString();
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.debug("{}: {}", thingId, errorMessage);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorMessage);
            }
        }
    }

    private int getEffectiveRunTime(RachioValve currentValve, RachioBridgeHandler handler) {
        if (runTime > 0) {
            return runTime;
        }
        int valveDefaultRuntime = currentValve.getDefaultRuntimeSeconds();
        if (valveDefaultRuntime > 0) {
            return valveDefaultRuntime;
        }
        int bridgeDefaultRuntime = handler.getDefaultRuntime();
        return bridgeDefaultRuntime > 0 ? bridgeDefaultRuntime : DEFAULT_ZONE_RUNTIME_SEC;
    }

    private boolean refreshValve(String valveId, boolean initialLoad) {
        RachioBridgeHandler handler = cloudHandler;
        if (handler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return false;
        }

        try {
            valve = handler.getValve(valveId);
            RachioValve currentValve = valve;
            if (currentValve == null || currentValve.id.isBlank()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Configured Rachio valveId was not found in the account.");
                return false;
            }
            thingId = currentValve.getThingName();
            if (!statusListenerRegistered) {
                handler.registerStatusListener(this);
                statusListenerRegistered = true;
            }
            if (initialLoad || getThing().getStatus() != ThingStatus.ONLINE) {
                handler.registerValveWebHook(currentValve.id);
            }
            logger.debug("{}: Valve model lookup succeeded: valveId='{}', baseStationId='{}'", thingId, currentValve.id,
                    currentValve.baseStationId);
            goOnline();
            return true;
        } catch (RachioApiException e) {
            String message = "Unable to load Rachio Valve '" + valveId + "': " + e.getMessage();
            logger.debug("{}: {}", thingId, message);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            return false;
        } catch (RuntimeException e) {
            String message = "Unable to initialize Rachio Valve '" + valveId + "': " + e.getMessage();
            logger.debug("{}: {}", thingId, message, e);
            updateStatus(ThingStatus.OFFLINE,
                    initialLoad ? ThingStatusDetail.CONFIGURATION_ERROR : ThingStatusDetail.COMMUNICATION_ERROR,
                    message);
            return false;
        }
    }

    public boolean handlesValveId(String valveId) {
        RachioValve currentValve = valve;
        return currentValve != null && currentValve.id.equalsIgnoreCase(valveId);
    }

    public boolean webhookEvent(RachioEventGsonDTO event) {
        RachioValve currentValve = valve;
        if (currentValve == null || !handlesValveId(event.resourceId)) {
            return false;
        }

        RachioWebhookPayload payload = event.payload;
        if (payload == null) {
            payload = new RachioWebhookPayload();
        }
        lastEvent = event.eventType.isBlank() ? event.type : event.eventType;
        lastEventTime = getTimestamp();
        if (!payload.runType.isBlank()) {
            lastRunType = payload.runType;
        }
        if (!payload.endReason.isBlank()) {
            lastEndReason = payload.endReason;
        }
        if (payload.hasFlowDetected()) {
            lastFlowDetected = payload.getFlowDetected();
        }

        if (EVENT_VALVE_RUN_START.equals(event.eventType)) {
            logger.info("{}: Smart Hose Timer valve '{}' STARTED watering (duration={} sec, runType={})", thingId,
                    currentValve.getThingName(), payload.getDurationSeconds(), payload.runType);
            runState = OnOffType.ON;
        } else if (EVENT_VALVE_RUN_END.equals(event.eventType)) {
            logger.info("{}: Smart Hose Timer valve '{}' STOPPED watering (duration={} sec, endReason={}, runType={})",
                    thingId, currentValve.getThingName(), payload.getDurationSeconds(), payload.endReason,
                    payload.runType);
            runState = OnOffType.OFF;
        } else {
            logger.debug("{}: Unhandled Smart Hose Timer valve event '{}' for valve '{}'", thingId, event.eventType,
                    currentValve.id);
            return false;
        }

        postChannelData();
        updateChannel(CHANNEL_LAST_UPDATE, getTimestamp());
        return true;
    }

    @Override
    protected void postChannelData() {
        RachioValve currentValve = valve;
        if (currentValve == null) {
            return;
        }
        updateChannel(CHANNEL_VALVE_NAME, new StringType(currentValve.getThingName()));
        updateChannel(CHANNEL_VALVE_ONLINE, onlineState(currentValve));
        updateChannel(CHANNEL_VALVE_RUN, runState);
        updateChannel(CHANNEL_VALVE_RUN_TIME, new DecimalType(BigDecimal.valueOf(runTime)));
        updateChannel(CHANNEL_VALVE_DEFAULT_RUNTIME,
                new DecimalType(BigDecimal.valueOf(currentValve.getDefaultRuntimeSeconds())));
        updateChannel(CHANNEL_VALVE_STATE_MATCHES,
                currentValve.hasStateMatches() ? currentValve.stateMatches() ? OnOffType.ON : OnOffType.OFF
                        : UnDefType.UNDEF);
        updateChannel(CHANNEL_VALVE_FLOW_DETECTED, flowDetectedState(currentValve));
        updateChannel(CHANNEL_VALVE_BATTERY_LEVEL, batteryLevelState(currentValve));
        updateChannel(CHANNEL_VALVE_SERIAL_NUMBER, stringOrUndef(currentValve.serialNumber));
        updateChannel(CHANNEL_VALVE_LAST_RUN_TYPE, stringOrUndef(lastRunType));
        updateChannel(CHANNEL_VALVE_LAST_END_REASON, stringOrUndef(lastEndReason));
        updateChannel(CHANNEL_LAST_EVENT, stringOrUndef(lastEvent));
        DateTimeType eventTime = lastEventTime;
        updateChannel(CHANNEL_LAST_EVENTTS, eventTime != null ? eventTime : UnDefType.UNDEF);
    }

    @Override
    protected void goOnline() {
        RachioValve currentValve = valve;
        if (currentValve != null) {
            updateProperties(currentValve.fillProperties());
        }
        postChannelData();
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    protected void onBridgeOnline() {
        RachioValve currentValve = valve;
        if (currentValve == null) {
            initialize();
        } else {
            refreshValve(currentValve.id, false);
        }
    }

    @Override
    public boolean onThingStateChangedl(@Nullable RachioDevice updatedDev, @Nullable RachioZone updatedZone) {
        return false;
    }

    @Override
    public void onConfigurationUpdated() {
        RachioBridgeHandler handler = cloudHandler;
        RachioValve currentValve = valve;
        if (handler != null && currentValve != null) {
            try {
                handler.registerValveWebHook(currentValve.id);
            } catch (RachioApiException e) {
                logger.debug("{}: Unable to renew valve webhook registration: {}", thingId, e.toString());
            }
        }
    }

    private State onlineState(RachioValve currentValve) {
        if (!currentValve.hasOnlineState()) {
            return UnDefType.UNDEF;
        }
        return currentValve.isOnline() ? OnOffType.ON : OnOffType.OFF;
    }

    private State flowDetectedState(RachioValve currentValve) {
        Boolean webhookFlowDetected = lastFlowDetected;
        if (webhookFlowDetected != null) {
            return webhookFlowDetected.booleanValue() ? OnOffType.ON : OnOffType.OFF;
        }
        if (!currentValve.hasFlowDetected()) {
            return UnDefType.UNDEF;
        }
        return currentValve.flowDetected() ? OnOffType.ON : OnOffType.OFF;
    }

    private State batteryLevelState(RachioValve currentValve) {
        Double batteryLevel = currentValve.batteryLevel;
        if (batteryLevel == null || batteryLevel.isNaN() || batteryLevel.isInfinite()) {
            return UnDefType.UNDEF;
        }
        return new DecimalType(BigDecimal.valueOf(batteryLevel.doubleValue()));
    }

    private State stringOrUndef(String value) {
        return value.isBlank() ? UnDefType.UNDEF : new StringType(value);
    }
}
