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
import java.util.List;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.xml.sax.SAXException;

/**
 * Tests zone channel declarations and simple state conversions.
 *
 * @author openHAB Contributors - Initial contribution
 */
@NonNullByDefault
class RachioZoneChannelTest {
    @Test
    void thingXmlFilesAreWellFormed()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        for (String file : List.of("cloud.xml", "device.xml", "zone.xml", "schedule.xml", "flexschedule.xml",
                "basestation.xml", "valve.xml", "valveprogram.xml")) {
            factory.newDocumentBuilder().parse(resource(file).toFile());
        }
    }

    @Test
    void zoneThingDeclaresTelemetryAndImageChannels() throws IOException, URISyntaxException {
        String xml = readThingXml("zone.xml");

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
        String xml = readThingXml("device.xml");

        assertThat(xml, containsString("id=\"activeZoneNumber\""));
        assertThat(xml, containsString("id=\"activeZoneName\""));
        assertThat(xml, containsString("id=\"activeZoneId\""));
    }

    @Test
    void quantityChannelItemTypesAreDeclared() throws IOException, URISyntaxException {
        String zoneXml = readThingXml("zone.xml");
        String deviceXml = readThingXml("device.xml");
        String valveXml = readThingXml("valve.xml");
        String scheduleXml = readThingXml("schedule.xml");
        String valveProgramXml = readThingXml("valveprogram.xml");

        assertThat(zoneXml, containsString("<item-type unitHint=\"s\">Number:Time</item-type>"));
        assertThat(zoneXml, containsString("<item-type unitHint=\"in\">Number:Length</item-type>"));
        assertThat(zoneXml, containsString("<item-type unitHint=\"ft²\">Number:Area</item-type>"));
        assertThat(zoneXml, containsString("<item-type unitHint=\"mm\">Number:Length</item-type>"));
        assertThat(deviceXml, containsString("<item-type>Number:Temperature</item-type>"));
        assertThat(deviceXml, containsString("<item-type>Number:Length</item-type>"));
        assertThat(deviceXml, containsString("<item-type unitHint=\"%\">Number:Dimensionless</item-type>"));
        assertThat(deviceXml, containsString("<item-type>Number:Speed</item-type>"));
        assertThat(valveXml, containsString("<item-type unitHint=\"%\">Number:Dimensionless</item-type>"));
        assertThat(scheduleXml, containsString("<item-type unitHint=\"1\">Number:Dimensionless</item-type>"));
        assertThat(valveProgramXml, containsString("<item-type unitHint=\"s\">Number:Time</item-type>"));
        assertThat(valveProgramXml, containsString("<item-type unitHint=\"d\">Number:Time</item-type>"));
    }

    @Test
    void semanticTagsAreDeclaredForReviewReadyDefaults() throws IOException, URISyntaxException {
        String zoneXml = readThingXml("zone.xml");
        String valveXml = readThingXml("valve.xml");
        String cloudXml = readThingXml("cloud.xml");

        assertThat(zoneXml, containsString("<semantic-equipment-tag>Irrigation</semantic-equipment-tag>"));
        assertThat(zoneXml, containsString("<tag>Measurement</tag>"));
        assertThat(zoneXml, containsString("<tag>Water</tag>"));
        assertThat(valveXml, containsString("<tag>StateOfCharge</tag>"));
        assertThat(cloudXml, containsString("<semantic-equipment-tag>WebService</semantic-equipment-tag>"));
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

    private String readThingXml(String fileName) throws IOException, URISyntaxException {
        return Files.readString(resource(fileName), StandardCharsets.UTF_8);
    }

    private Path resource(String fileName) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass().getResource("/OH-INF/thing/" + fileName)).toURI());
    }
}
