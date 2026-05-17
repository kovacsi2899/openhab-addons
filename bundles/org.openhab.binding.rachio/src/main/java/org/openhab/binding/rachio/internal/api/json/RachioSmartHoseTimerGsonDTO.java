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
package org.openhab.binding.rachio.internal.api.json;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioBindingConstants;
import org.openhab.core.thing.Thing;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * DTOs for the Rachio Smart Hose Timer ValveService.
 */
public class RachioSmartHoseTimerGsonDTO {
    private static final Gson GSON = new Gson();

    public static class RachioBaseStationListResponse {
        public ArrayList<RachioBaseStation> baseStations = new ArrayList<>();

        public static RachioBaseStationListResponse fromJson(String json) {
            RachioBaseStationListResponse response = new RachioBaseStationListResponse();
            response.baseStations.addAll(parseArray(json, RachioBaseStation.class, "baseStations", "basestations",
                    "items", "data", "results"));
            return response;
        }
    }

    public static class RachioValveListResponse {
        public ArrayList<RachioValve> valves = new ArrayList<>();

        public static RachioValveListResponse fromJson(String json) {
            RachioValveListResponse response = new RachioValveListResponse();
            response.valves.addAll(parseArray(json, RachioValve.class, "valves", "items", "data", "results"));
            return response;
        }
    }

    public static class RachioBaseStation {
        public String id = "";
        public String name = "";
        public String displayName = "";
        public String nickname = "";
        public String serialNumber = "";
        public String model = "";
        public String firmwareVersion = "";
        public String hardwareVersion = "";
        public String status = "";
        public @Nullable Boolean online;
        public @Nullable Boolean connected;

        public String getThingID() {
            return firstNonBlank(id, serialNumber, getThingName());
        }

        public String getThingName() {
            return firstNonBlank(name, displayName, nickname, "Rachio BaseStation");
        }

        public boolean isOnline() {
            @Nullable
            Boolean online = this.online;
            if (online != null) {
                return online.booleanValue();
            }
            @Nullable
            Boolean connected = this.connected;
            if (connected != null) {
                return connected.booleanValue();
            }
            return "ONLINE".equalsIgnoreCase(status) || "CONNECTED".equalsIgnoreCase(status);
        }

        public boolean hasOnlineState() {
            return online != null || connected != null || !status.isBlank();
        }

        public Map<String, String> fillProperties() {
            Map<String, String> properties = new HashMap<>();
            properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
            properties.put(PROPERTY_BASE_STATION_ID, id);
            putIfNotBlank(properties, PROPERTY_NAME, getThingName());
            putIfNotBlank(properties, Thing.PROPERTY_SERIAL_NUMBER, serialNumber);
            putIfNotBlank(properties, PROPERTY_MODEL, model);
            putIfNotBlank(properties, "firmwareVersion", firmwareVersion);
            putIfNotBlank(properties, "hardwareVersion", hardwareVersion);
            return properties;
        }
    }

    public static class RachioValve {
        public String id = "";
        public String baseStationId = "";
        public String name = "";
        public String displayName = "";
        public String nickname = "";
        public String serialNumber = "";
        public String model = "";
        public String firmwareVersion = "";
        public String hardwareVersion = "";
        public String status = "";
        public @Nullable Boolean online;
        public @Nullable Boolean connected;
        public @Nullable Double batteryLevel;
        public @Nullable Integer defaultRuntimeSeconds;
        public @Nullable RachioValveState state;
        public @Nullable RachioValveState valveState;

        public String getThingID() {
            return firstNonBlank(id, serialNumber, getThingName());
        }

        public String getThingName() {
            return firstNonBlank(name, displayName, nickname, "Rachio Valve");
        }

        public RachioValveState getState() {
            RachioValveState valveState = this.valveState;
            if (valveState != null) {
                return valveState;
            }
            RachioValveState state = this.state;
            return state != null ? state : new RachioValveState();
        }

        public boolean isOnline() {
            @Nullable
            Boolean online = this.online;
            if (online != null) {
                return online.booleanValue();
            }
            @Nullable
            Boolean connected = this.connected;
            if (connected != null) {
                return connected.booleanValue();
            }
            RachioValveState state = getState();
            @Nullable
            Boolean stateOnline = state.online;
            if (stateOnline != null) {
                return stateOnline.booleanValue();
            }
            @Nullable
            Boolean stateConnected = state.connected;
            if (stateConnected != null) {
                return stateConnected.booleanValue();
            }
            return "ONLINE".equalsIgnoreCase(status) || "CONNECTED".equalsIgnoreCase(status);
        }

        public boolean hasOnlineState() {
            RachioValveState state = getState();
            return online != null || connected != null || state.online != null || state.connected != null
                    || !status.isBlank();
        }

        public boolean stateMatches() {
            Boolean matches = getState().matches;
            return matches != null && matches.booleanValue();
        }

        public boolean hasStateMatches() {
            return getState().matches != null;
        }

        public boolean flowDetected() {
            return getState().getFlowDetected();
        }

        public boolean hasFlowDetected() {
            return getState().hasFlowDetected();
        }

        public int getDefaultRuntimeSeconds() {
            @Nullable
            Integer defaultRuntimeSeconds = this.defaultRuntimeSeconds;
            if (defaultRuntimeSeconds != null && defaultRuntimeSeconds.intValue() > 0) {
                return defaultRuntimeSeconds.intValue();
            }
            Integer runtime = getState().defaultRuntimeSeconds;
            return runtime != null ? Math.max(0, runtime.intValue()) : 0;
        }

        public Map<String, String> fillProperties() {
            Map<String, String> properties = new HashMap<>();
            properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
            properties.put(PROPERTY_VALVE_ID, id);
            putIfNotBlank(properties, PROPERTY_BASE_STATION_ID, baseStationId);
            putIfNotBlank(properties, PROPERTY_NAME, getThingName());
            putIfNotBlank(properties, Thing.PROPERTY_SERIAL_NUMBER, serialNumber);
            putIfNotBlank(properties, PROPERTY_MODEL, model);
            putIfNotBlank(properties, "firmwareVersion", firmwareVersion);
            putIfNotBlank(properties, "hardwareVersion", hardwareVersion);
            return properties;
        }
    }

    public static class RachioValveState {
        public @Nullable Boolean matches;
        public @Nullable Boolean online;
        public @Nullable Boolean connected;
        public @Nullable Boolean flowDetected;
        public String flowDetectedText = "";
        public @Nullable Integer defaultRuntimeSeconds;

        public boolean getFlowDetected() {
            @Nullable
            Boolean flowDetected = this.flowDetected;
            if (flowDetected != null) {
                return flowDetected.booleanValue();
            }
            return Boolean.parseBoolean(flowDetectedText);
        }

        public boolean hasFlowDetected() {
            return flowDetected != null || !flowDetectedText.isBlank();
        }
    }

    public static class RachioValveDefaultRuntimeRequest {
        public String valveId;
        public int defaultRuntimeSeconds;

        public RachioValveDefaultRuntimeRequest(String valveId, int defaultRuntimeSeconds) {
            this.valveId = valveId;
            this.defaultRuntimeSeconds = defaultRuntimeSeconds;
        }
    }

    public static class RachioValveStartWateringRequest {
        public String valveId;
        public int durationSeconds;

        public RachioValveStartWateringRequest(String valveId, int durationSeconds) {
            this.valveId = valveId;
            this.durationSeconds = durationSeconds;
        }
    }

    public static class RachioValveStopWateringRequest {
        public String valveId;

        public RachioValveStopWateringRequest(String valveId) {
            this.valveId = valveId;
        }
    }

    public static RachioBaseStation parseBaseStation(String json) {
        @Nullable
        RachioBaseStation baseStation = parseObject(json, RachioBaseStation.class, "baseStation", "basestation", "data",
                "result");
        return baseStation != null ? baseStation : new RachioBaseStation();
    }

    public static RachioValve parseValve(String json) {
        @Nullable
        RachioValve valve = parseObject(json, RachioValve.class, "valve", "data", "result");
        return valve != null ? valve : new RachioValve();
    }

    private static <T> List<T> parseArray(String json, Class<T> valueType, String... arrayNames) {
        List<T> values = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (root.isJsonArray()) {
            addArrayEntries(values, root.getAsJsonArray(), valueType);
            return values;
        }
        if (!root.isJsonObject()) {
            return values;
        }
        JsonObject object = root.getAsJsonObject();
        for (String arrayName : arrayNames) {
            JsonElement arrayElement = object.get(arrayName);
            if (arrayElement != null && arrayElement.isJsonArray()) {
                addArrayEntries(values, arrayElement.getAsJsonArray(), valueType);
            }
        }
        return values;
    }

    private static <T> void addArrayEntries(List<T> values, JsonArray array, Class<T> valueType) {
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                @Nullable
                T value = GSON.fromJson(element, valueType);
                if (value != null) {
                    values.add(value);
                }
            }
        }
    }

    private static <T> @Nullable T parseObject(String json, Class<T> valueType, String... objectNames) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        for (String objectName : objectNames) {
            JsonElement nestedObject = object.get(objectName);
            if (nestedObject != null && nestedObject.isJsonObject()) {
                return GSON.fromJson(nestedObject, valueType);
            }
        }
        return GSON.fromJson(object, valueType);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static void putIfNotBlank(Map<String, String> properties, String key, String value) {
        if (!value.isBlank()) {
            properties.put(key, value);
        }
    }
}
