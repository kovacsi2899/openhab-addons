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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

/**
 * Tests zone channel declarations and simple state conversions.
 */
class RachioZoneChannelTest {
    @Test
    void zoneThingDeclaresTelemetryAndImageChannels() throws IOException, URISyntaxException {
        String xml = Files.readString(
                Path.of(Objects.requireNonNull(getClass().getResource("/OH-INF/thing/zone.xml")).toURI()),
                StandardCharsets.UTF_8);

        assertThat(xml, containsString("id=\"availableWater\""));
        assertThat(xml, containsString("id=\"depthOfWater\""));
        assertThat(xml, containsString("id=\"saturatedDepthOfWater\""));
        assertThat(xml, containsString("id=\"managementAllowedDepletion\""));
        assertThat(xml, containsString("id=\"rootZoneDepth\""));
        assertThat(xml, containsString("id=\"efficiency\""));
        assertThat(xml, containsString("id=\"yardAreaSquareFeet\""));
        assertThat(xml, containsString("id=\"lastWateredDate\""));
        assertThat(xml, containsString("id=\"fixedRuntime\""));
        assertThat(xml, containsString("id=\"maxRuntime\""));
        assertThat(xml, containsString("id=\"runtimeNoMultiplier\""));
        assertThat(xml, containsString("id=\"scheduleDataModified\""));
        assertThat(xml, containsString("id=\"image\" typeId=\"zone_image\""));
        assertThat(xml, containsString("<item-type>Image</item-type>"));
    }

    @Test
    void deviceThingDeclaresActiveZoneChannels() throws IOException, URISyntaxException {
        String xml = Files.readString(
                Path.of(Objects.requireNonNull(getClass().getResource("/OH-INF/thing/device.xml")).toURI()),
                StandardCharsets.UTF_8);

        assertThat(xml, containsString("id=\"activeZoneNumber\""));
        assertThat(xml, containsString("id=\"activeZoneName\""));
        assertThat(xml, containsString("id=\"activeZoneId\""));
    }

    @Test
    void missingLastWateredDateIsPublishedAsNull() {
        assertThat(RachioZoneHandler.epochMillisOrNull(-1), is(UnDefType.NULL));
    }

    @Test
    void validLastWateredDateIsPublishedAsDateTime() {
        State state = RachioZoneHandler.epochMillisOrNull(1_523_129_743_000L);

        assertThat(state, instanceOf(DateTimeType.class));
    }
}
