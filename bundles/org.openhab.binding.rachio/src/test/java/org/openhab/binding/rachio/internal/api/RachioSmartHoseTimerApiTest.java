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
package org.openhab.binding.rachio.internal.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.openhab.binding.rachio.internal.api.json.RachioSmartHoseTimerGsonDTO;
import org.openhab.binding.rachio.internal.api.json.RachioSmartHoseTimerGsonDTO.RachioBaseStationListResponse;
import org.openhab.binding.rachio.internal.api.json.RachioSmartHoseTimerGsonDTO.RachioValve;
import org.openhab.binding.rachio.internal.api.json.RachioSmartHoseTimerGsonDTO.RachioValveListResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests Smart Hose Timer API payload and DTO helpers.
 */
class RachioSmartHoseTimerApiTest {
    @Test
    void baseStationListResponseParsesWrappedList() {
        String json = """
                {
                  "baseStations": [
                    {"id":"base-station-id","name":"Hub","serialNumber":"BS123","online":true}
                  ]
                }
                """;

        RachioBaseStationListResponse response = RachioBaseStationListResponse.fromJson(json);

        assertThat(response.baseStations.size(), is(1));
        assertThat(response.baseStations.get(0).id, is("base-station-id"));
        assertThat(response.baseStations.get(0).getThingName(), is("Hub"));
        assertThat(response.baseStations.get(0).isOnline(), is(true));
    }

    @Test
    void valveListResponseParsesValveStateMatches() {
        String json = """
                {
                  "valves": [
                    {
                      "id":"valve-id",
                      "baseStationId":"base-station-id",
                      "name":"Garden",
                      "serialNumber":"V123",
                      "defaultRuntimeSeconds":600,
                      "batteryLevel":87,
                      "state":{"matches":false,"flowDetected":true}
                    }
                  ]
                }
                """;

        RachioValveListResponse response = RachioValveListResponse.fromJson(json);

        assertThat(response.valves.size(), is(1));
        RachioValve valve = response.valves.get(0);
        assertThat(valve.id, is("valve-id"));
        assertThat(valve.baseStationId, is("base-station-id"));
        assertThat(valve.getDefaultRuntimeSeconds(), is(600));
        assertThat(valve.stateMatches(), is(false));
        assertThat(valve.flowDetected(), is(true));
    }

    @Test
    void getValveResponseParsesNestedValveObject() {
        String json = """
                {
                  "valve": {
                    "id":"valve-id",
                    "name":"Front Yard",
                    "valveState":{"matches":true}
                  }
                }
                """;

        RachioValve valve = RachioSmartHoseTimerGsonDTO.parseValve(json);

        assertThat(valve.id, is("valve-id"));
        assertThat(valve.getThingName(), is("Front Yard"));
        assertThat(valve.stateMatches(), is(true));
    }

    @Test
    void setDefaultRuntimePayloadContainsDocumentedFields() {
        JsonObject json = JsonParser.parseString(RachioApi.buildValveDefaultRuntimePayload("valve-id", 900))
                .getAsJsonObject();

        assertThat(json.get("valveId").getAsString(), is("valve-id"));
        assertThat(json.get("defaultRuntimeSeconds").getAsInt(), is(900));
    }

    @Test
    void startWateringPayloadContainsDocumentedFields() {
        JsonObject json = JsonParser.parseString(RachioApi.buildValveStartWateringPayload("valve-id", 300))
                .getAsJsonObject();

        assertThat(json.get("valveId").getAsString(), is("valve-id"));
        assertThat(json.get("durationSeconds").getAsInt(), is(300));
    }

    @Test
    void stopWateringPayloadContainsValveId() {
        JsonObject json = JsonParser.parseString(RachioApi.buildValveStopWateringPayload("valve-id")).getAsJsonObject();

        assertThat(json.get("valveId").getAsString(), is("valve-id"));
    }
}
