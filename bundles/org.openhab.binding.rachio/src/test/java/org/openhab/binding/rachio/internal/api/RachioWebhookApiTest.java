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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.EVENT_DEVICE_ZONE_RUN_STARTED;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.EVENT_SCHEDULE_STARTED;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.WEBHOOK_QUERY_CONTROLLER_ID;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioApiWebHookEntry;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioApiWebHookResourceId;
import org.openhab.binding.rachio.internal.api.webhook.RachioWebhookResourceType;
import org.openhab.binding.rachio.internal.api.webhook.RachioWebhookTarget;

/**
 * Tests generic WebhookService API helpers.
 */
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
