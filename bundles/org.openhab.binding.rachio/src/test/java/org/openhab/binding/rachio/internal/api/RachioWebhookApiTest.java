/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.EVENT_DEVICE_ZONE_RUN_STARTED;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.EVENT_SCHEDULE_STARTED;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.EVENT_VALVE_RUN_END;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.EVENT_VALVE_RUN_START;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.WEBHOOK_QUERY_CONTROLLER_ID;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.WEBHOOK_QUERY_VALVE_ID;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioApiWebHookEntry;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioApiWebHookResourceId;
import org.openhab.binding.rachio.internal.api.webhook.RachioWebhookResourceType;
import org.openhab.binding.rachio.internal.api.webhook.RachioWebhookTarget;

/**
 * Tests generic WebhookService API helpers.
 *
 * @author openHAB Contributors - Initial contribution
 */
@SuppressWarnings({ "null" })
class RachioWebhookApiTest {
    @Test
    void eventTypeListParsesStringAndObjectEntries() {
        String json = """
                {
                  "eventTypes": [
                    "DEVICE_ZONE_RUN_STARTED_EVENT",
                    {"eventType":"SCHEDULE_STARTED_EVENT"}
                  ]
                }
                """;

        List<String> eventTypes = RachioApi.parseWebhookEventTypeList(json);

        assertThat(eventTypes, contains(EVENT_DEVICE_ZONE_RUN_STARTED, EVENT_SCHEDULE_STARTED));
    }

    @Test
    void eventTypeMapParsesGroupedResourceResponse() {
        String json = """
                {
                  "eventTypes": [
                    {
                      "resourceType": "VALVE",
                      "eventTypes": [
                        "VALVE_RUN_START_EVENT",
                        "VALVE_RUN_END_EVENT"
                      ]
                    },
                    {
                      "resourceType": "PROGRAM",
                      "eventTypes": [
                        "PROGRAM_RAIN_SKIP_CREATED_EVENT",
                        "PROGRAM_RAIN_SKIP_CANCELED_EVENT"
                      ]
                    },
                    {
                      "resourceType": "LIGHTING_CONTROLLER",
                      "eventTypes": [
                        "LIGHTING_ZONE_STATE_CHANGE_EVENT"
                      ]
                    },
                    {
                      "resourceType": "IRRIGATION_CONTROLLER",
                      "eventTypes": [
                        "SCHEDULE_STARTED_EVENT",
                        "SCHEDULE_STOPPED_EVENT"
                      ]
                    }
                  ]
                }
                """;

        Map<RachioWebhookResourceType, Set<String>> eventTypesByResourceType = RachioApi.parseWebhookEventTypeMap(json);

        assertThat(Objects.requireNonNull(eventTypesByResourceType.get(RachioWebhookResourceType.VALVE)).size(), is(2));
        assertThat(Objects.requireNonNull(eventTypesByResourceType.get(RachioWebhookResourceType.PROGRAM)).size(),
                is(2));
        assertThat(Objects.requireNonNull(eventTypesByResourceType.get(RachioWebhookResourceType.LIGHTING_CONTROLLER))
                .size(), is(1));
        assertThat(Objects.requireNonNull(eventTypesByResourceType.get(RachioWebhookResourceType.IRRIGATION_CONTROLLER))
                .size(), is(2));
        assertThat(
                Objects.requireNonNull(eventTypesByResourceType.get(RachioWebhookResourceType.IRRIGATION_CONTROLLER)),
                containsInAnyOrder("SCHEDULE_STARTED_EVENT", "SCHEDULE_STOPPED_EVENT"));
    }

    @Test
    void irrigationTargetBuildsResourceAwareListQueryAndPayload() {
        RachioWebhookTarget target = RachioWebhookTarget.irrigationController("device id",
                List.of(EVENT_DEVICE_ZONE_RUN_STARTED));

        assertThat(target.buildListQuery(), is(WEBHOOK_QUERY_CONTROLLER_ID + "=device+id"));
        Map<String, Object> payload = target.buildCreatePayload("https://host/rachio/webhook", "external-id");

        assertThat(payload, hasEntry("externalId", "external-id"));
        assertThat(payload, hasEntry("url", "https://host/rachio/webhook"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceId = (Map<String, Object>) payload.get("resourceId");
        assertThat(resourceId, hasEntry("irrigationControllerId", "device id"));
    }

    @Test
    void valveTargetBuildsResourceAwareListQueryAndPayload() {
        RachioWebhookTarget target = new RachioWebhookTarget("valve id", RachioWebhookResourceType.VALVE,
                List.of(EVENT_VALVE_RUN_START, EVENT_VALVE_RUN_END));

        assertThat(target.buildListQuery(), is(WEBHOOK_QUERY_VALVE_ID + "=valve+id"));
        Map<String, Object> payload = target.buildCreatePayload("https://host/rachio/webhook", "external-id");

        @SuppressWarnings("unchecked")
        Map<String, Object> resourceId = (Map<String, Object>) payload.get("resourceId");
        assertThat(resourceId, hasEntry("valveId", "valve id"));
    }

    @Test
    void exactWebhookMatchIsRetained() {
        RachioWebhookTarget target = RachioWebhookTarget.irrigationController("device-id",
                List.of(EVENT_DEVICE_ZONE_RUN_STARTED, EVENT_SCHEDULE_STARTED));
        RachioApiWebHookEntry webhook = webhook("https://host/rachio/webhook", "external-id", "device-id",
                List.of(EVENT_SCHEDULE_STARTED, EVENT_DEVICE_ZONE_RUN_STARTED));

        assertThat(target.matches(webhook, "https://host/rachio/webhook", "external-id"), is(true));
    }

    @Test
    void mismatchedWebhookIsNotAnExactMatch() {
        RachioWebhookTarget target = RachioWebhookTarget.irrigationController("device-id",
                List.of(EVENT_DEVICE_ZONE_RUN_STARTED, EVENT_SCHEDULE_STARTED));
        RachioApiWebHookEntry webhook = webhook("https://host/rachio/webhook", "external-id", "other-device",
                List.of(EVENT_SCHEDULE_STARTED, EVENT_DEVICE_ZONE_RUN_STARTED));

        assertThat(target.matches(webhook, "https://host/rachio/webhook", "external-id"), is(false));
    }

    @Test
    void unrelatedResourceTypeDoesNotMatchTarget() {
        RachioWebhookTarget target = new RachioWebhookTarget("valve-id", RachioWebhookResourceType.VALVE,
                List.of("VALVE_RUN_STARTED_EVENT"));
        RachioApiWebHookEntry webhook = webhook("https://host/rachio/webhook", "external-id", "device-id",
                List.of("VALVE_RUN_STARTED_EVENT"));

        assertThat(target.resourceMatches(webhook), is(false));
    }

    @Test
    void irrigationTargetAcceptsIrrigationEvents() {
        RachioWebhookTarget target = RachioWebhookTarget.irrigationController("device-id",
                List.of(EVENT_DEVICE_ZONE_RUN_STARTED, EVENT_SCHEDULE_STARTED));

        assertThat(target.getUnsupportedEventTypes(Set.of(EVENT_DEVICE_ZONE_RUN_STARTED, EVENT_SCHEDULE_STARTED))
                .isEmpty(), is(true));
    }

    @Test
    void valveTargetAcceptsValveEvents() {
        RachioWebhookTarget target = new RachioWebhookTarget("valve-id", RachioWebhookResourceType.VALVE,
                List.of(EVENT_VALVE_RUN_START, EVENT_VALVE_RUN_END));

        assertThat(target.getUnsupportedEventTypes(Set.of(EVENT_VALVE_RUN_START, EVENT_VALVE_RUN_END)).isEmpty(),
                is(true));
    }

    @Test
    void invalidResourceEventCombinationIsDetectedAndFiltered() {
        RachioWebhookTarget target = new RachioWebhookTarget("valve-id", RachioWebhookResourceType.VALVE,
                List.of("VALVE_RUN_START_EVENT", EVENT_SCHEDULE_STARTED));
        Set<String> supportedValveEvents = Set.of("VALVE_RUN_START_EVENT", "VALVE_RUN_END_EVENT");

        assertThat(target.getUnsupportedEventTypes(supportedValveEvents), contains(EVENT_SCHEDULE_STARTED));
        assertThat(target.filterEventTypes(supportedValveEvents).getEventTypeList(), contains("VALVE_RUN_START_EVENT"));
    }

    private RachioApiWebHookEntry webhook(String url, String externalId, String irrigationControllerId,
            List<String> eventTypes) {
        RachioApiWebHookEntry webhook = new RachioApiWebHookEntry();
        webhook.url = url;
        webhook.externalId = externalId;
        RachioApiWebHookResourceId resourceId = new RachioApiWebHookResourceId();
        resourceId.irrigationControllerId = irrigationControllerId;
        webhook.resourceId = resourceId;
        webhook.eventTypes.addAll(eventTypes);
        return webhook;
    }
}
