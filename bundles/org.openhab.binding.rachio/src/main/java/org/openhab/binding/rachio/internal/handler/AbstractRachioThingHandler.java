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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.State;

/**
 * Base class for Rachio child thing handlers with common bridge lookup, cached refresh, and status propagation logic.
 *
 * @author Jeff James - Initial architectural concept
 * @author Kovacs Istvan - Adaptation and integration into the openHAB 5.1+ Rachio binding
 */
@NonNullByDefault
public abstract class AbstractRachioThingHandler extends BaseThingHandler implements RachioStatusListener {
    protected String thingId = "";
    protected final Map<String, State> channelData = new HashMap<>();

    @Nullable
    protected Bridge bridge;

    @Nullable
    protected RachioBridgeHandler cloudHandler;

    protected AbstractRachioThingHandler(Thing thing) {
        super(thing);
    }

    protected boolean initializeCloudHandler() {
        bridge = getBridge();
        Bridge currentBridge = bridge;
        if (currentBridge == null) {
            return false;
        }

        ThingHandler handler = currentBridge.getHandler();
        if (handler instanceof RachioBridgeHandler bridgeHandler) {
            cloudHandler = bridgeHandler;
            return true;
        }
        return false;
    }

    protected String getThingConfigurationString(String parameterName) {
        Object value = getThing().getConfiguration().getProperties().get(parameterName);
        return value != null ? value.toString().trim() : "";
    }

    protected boolean isBridgeOnline() {
        Bridge currentBridge = bridge;
        return currentBridge != null && currentBridge.getStatus() == ThingStatus.ONLINE;
    }

    protected boolean handleRefreshCommand(String channel) {
        State state = channelData.get(channel);
        if (state != null) {
            updateState(channel, state);
            return true;
        }
        postChannelData();
        return false;
    }

    protected boolean updateChannel(String channelName, State newValue) {
        State currentValue = channelData.get(channelName);
        if ((currentValue != null) && currentValue.equals(newValue)) {
            return false;
        }

        if (currentValue == null) {
            channelData.put(channelName, newValue);
        } else {
            channelData.replace(channelName, newValue);
        }

        updateState(channelName, newValue);
        return true;
    }

    @Override
    public void onConfigurationUpdated() {
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        super.bridgeStatusChanged(bridgeStatusInfo);

        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    public void shutdown() {
        unregisterStatusListener();
        updateStatus(ThingStatus.OFFLINE);
    }

    @Override
    public void dispose() {
        unregisterStatusListener();
        super.dispose();
    }

    private void unregisterStatusListener() {
        RachioBridgeHandler handler = cloudHandler;
        if (handler != null) {
            handler.unregisterStatusListener(this);
        }
    }

    protected abstract void goOnline();

    protected abstract void postChannelData();
}
