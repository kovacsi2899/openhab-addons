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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            factory.newDocumentBuilder().parse(resource("/OH-INF/thing/" + file).toFile());
        }
    }

    @Test
    void updateXmlDeclaresQuantityChannelMigrations()
            throws IOException, ParserConfigurationException, SAXException, URISyntaxException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(resource("/OH-INF/update/update.xml").toFile());

        String updateXml = readResource("/OH-INF/update/update.xml");

        assertUpdateThingTypesAreUnique(updateXml);
        assertThingTypeVersion("device.xml");
        assertThingTypeVersion("zone.xml");
        assertThingTypeVersion("schedule.xml");
        assertThingTypeVersion("flexschedule.xml");
        assertThingTypeVersion("valve.xml");
        assertThingTypeVersion("valveprogram.xml");

        assertUpdateChannel(updateXml, "pauseTime", "dev_pauseTime");
        assertUpdateChannel(updateXml, "runTime", "dev_runTime");
        assertUpdateChannel(updateXml, "rainDelay", "dev_rainDelay");
        assertUpdateChannel(updateXml, "currentScheduleDuration", "dev_currentScheduleDuration");
        assertUpdateChannel(updateXml, "forecastTodayHigh", "dev_forecastTodayHigh");
        assertUpdateChannel(updateXml, "forecastTodayLow", "dev_forecastTodayLow");
        assertUpdateChannel(updateXml, "forecastPrecipitation", "dev_forecastPrecipitation");
        assertUpdateChannel(updateXml, "forecastPrecipitationProbability", "dev_forecastPrecipitationProbability");
        assertUpdateChannel(updateXml, "forecastWind", "dev_forecastWind");
        assertUpdateChannel(updateXml, "runTime", "zone_runTime");
        assertUpdateChannel(updateXml, "runTotal", "zone_runTotal");
        assertUpdateChannel(updateXml, "availableWater", "zone_availableWater");
        assertUpdateChannel(updateXml, "depthOfWater", "zone_depthOfWater");
        assertUpdateChannel(updateXml, "saturatedDepthOfWater", "zone_saturatedDepthOfWater");
        assertUpdateChannel(updateXml, "managementAllowedDepletion", "zone_managementAllowedDepletion");
        assertUpdateChannel(updateXml, "rootZoneDepth", "zone_rootZoneDepth");
        assertUpdateChannel(updateXml, "efficiency", "zone_efficiency");
        assertUpdateChannel(updateXml, "yardAreaSquareFeet", "zone_yardAreaSquareFeet");
        assertUpdateChannel(updateXml, "fixedRuntime", "zone_fixedRuntime");
        assertUpdateChannel(updateXml, "maxRuntime", "zone_maxRuntime");
        assertUpdateChannel(updateXml, "runtimeNoMultiplier", "zone_runtimeNoMultiplier");
        assertUpdateChannel(updateXml, "moistureLevel", "zone_moistureLevel");
        assertUpdateChannel(updateXml, "moisturePercent", "zone_moisturePercent");
        assertUpdateChannel(updateXml, "seasonalAdjustment", "schedule_seasonalAdjustment");
        assertUpdateChannel(updateXml, "runTime", "valve_runTime");
        assertUpdateChannel(updateXml, "defaultRuntime", "valve_defaultRuntime");
        assertUpdateChannel(updateXml, "batteryLevel", "valve_batteryLevel");
        assertUpdateChannel(updateXml, "nextPlannedRunDuration", "valve_nextPlannedRunDuration");
        assertUpdateChannel(updateXml, "lastCompletedRunDuration", "valve_lastCompletedRunDuration");
        assertUpdateChannel(updateXml, "duration", "valveprogram_duration");
        assertUpdateChannel(updateXml, "intervalDays", "valveprogram_intervalDays");
        assertUpdateChannel(updateXml, "seasonalAdjustment", "valveprogram_seasonalAdjustment");
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
        assertThat(deviceXml, containsString("<item-type unitHint=\"one\">Number:Dimensionless</item-type>"));
        assertThat(deviceXml, containsString("<item-type>Number:Speed</item-type>"));
        assertThat(valveXml, containsString("<item-type unitHint=\"%\">Number:Dimensionless</item-type>"));
        assertThat(scheduleXml, containsString("<item-type unitHint=\"one\">Number:Dimensionless</item-type>"));
        assertThat(valveProgramXml, containsString("<item-type unitHint=\"s\">Number:Time</item-type>"));
        assertThat(valveProgramXml, containsString("<item-type unitHint=\"d\">Number:Time</item-type>"));
    }

    @Test
    void fixedUnitQuantityChannelsDeclareUnitHints() throws IOException, URISyntaxException {
        for (String file : List.of("zone.xml", "schedule.xml", "valve.xml", "valveprogram.xml")) {
            assertAllQuantityChannelsHaveUnitHint(readThingXml(file));
        }

        String deviceXml = readThingXml("device.xml");
        assertAllQuantityChannelsHaveUnitHint(deviceXml.replace("<item-type>Number:Temperature</item-type>", "")
                .replace("<item-type>Number:Length</item-type>", "")
                .replace("<item-type>Number:Speed</item-type>", ""));
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
        return readResource("/OH-INF/thing/" + fileName);
    }

    private String readResource(String resourcePath) throws IOException, URISyntaxException {
        return Files.readString(resource(resourcePath), StandardCharsets.UTF_8);
    }

    private Path resource(String resourcePath) throws URISyntaxException {
        return Path.of(Objects.requireNonNull(getClass().getResource(resourcePath)).toURI());
    }

    private void assertAllQuantityChannelsHaveUnitHint(String xml) {
        Matcher matcher = Pattern.compile("<item-type(?![^>]*unitHint)[^>]*>Number:[^<]+</item-type>").matcher(xml);
        assertThat(matcher.find(), is(false));
    }

    private void assertThingTypeVersion(String fileName) throws IOException, URISyntaxException {
        assertThat(readThingXml(fileName), containsString("<property name=\"thingTypeVersion\">1</property>"));
    }

    private void assertUpdateChannel(String xml, String channelId, String typeId) {
        assertThat(xml, containsString("<update-channel id=\"" + channelId + "\">"));
        assertThat(xml, containsString("<type>rachio:" + typeId + "</type>"));
    }

    private void assertUpdateThingTypesAreUnique(String xml) {
        Matcher matcher = Pattern.compile("<thing-type uid=\"([^\"]+)\">").matcher(xml);
        Set<String> seenThingTypes = new HashSet<>();
        while (matcher.find()) {
            assertThat("Duplicate update thing type " + matcher.group(1), seenThingTypes.add(matcher.group(1)),
                    is(true));
        }
        Set<String> expectedThingTypes = Set.of("rachio:device", "rachio:zone", "rachio:schedule",
                "rachio:flexschedule", "rachio:valve", "rachio:valveprogram");
        assertThat(seenThingTypes.size(), is(expectedThingTypes.size()));
        assertThat(seenThingTypes.containsAll(expectedThingTypes), is(true));
    }
}
