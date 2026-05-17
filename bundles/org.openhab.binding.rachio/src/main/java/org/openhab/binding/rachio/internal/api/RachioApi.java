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
package org.openhab.binding.rachio.internal.api;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;
import static org.openhab.binding.rachio.internal.RachioUtils.*;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioBindingConstants;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioApiWebHookEntry;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioApiWebHookList;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioCloudPersonId;
import org.openhab.binding.rachio.internal.api.json.RachioApiGsonDTO.RachioCloudStatus;
import org.openhab.binding.rachio.internal.api.json.RachioDeviceGsonDTO.RachioCloudDevice;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.PRIORITY;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * The {@link RachioApi} implements the interface to the Rachio cloud service (using http).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioApi {
    private final Logger logger = LoggerFactory.getLogger(RachioApi.class);
    private static final String MD5_HASH_ALGORITHM = "MD5";
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String UTF8_CHAR_SET = "UTF-8";
    private static final int WEBHOOK_SIGNATURE_LENGTH_BYTES = 32;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    protected String apikey = "";
    protected String personId = "";
    protected String userName = "";
    protected String fullName = "";
    protected String email = "";
    @Nullable
    protected ThingUID bridgeUID = null;

    protected RachioApiResult lastApiResult = new RachioApiResult();
    private static final Map<String, ClientRateLimitManager> rateLimitManagers = new ConcurrentHashMap<>();
    private ClientRateLimitManager rateLimitManager = new ClientRateLimitManager(10, Duration.ofSeconds(30));

    private HashMap<String, RachioDevice> deviceList = new HashMap<String, RachioDevice>();
    private RachioHttp httpApi = new RachioHttp("");

    public RachioApi(String personId) {
        this.personId = personId;
    }

    public RachioApiResult getLastApiResult() {
        return lastApiResult;
    }

    protected void setApiResult(RachioApiResult result) {
        lastApiResult = result;
    }

    private void throttleIfNeeded(PRIORITY priority) throws RachioApiException {
        try {
            rateLimitManager.tryThrottle(priority);
        } catch (RateLimitThrottleException e) {
            String message = MessageFormat.format("RachioApi: {0}", e.toString());
            throw new RachioApiException(message, lastApiResult);
        }
    }

    private void updateRateLimit(@Nullable RachioApiResult result) {
        if (result == null) {
            return;
        }
        int rateRemaining = result.hasKnownRateRemaining() ? result.rateRemaining : -1;
        rateLimitManager.updateRateLimit(result.rateLimit, rateRemaining, result.rateReset);
    }

    private RachioApiResult recordApiResult(RachioApiResult result) {
        updateRateLimit(result);
        lastApiResult = result;
        return result;
    }

    private void recordApiException(RachioApiException e) {
        RachioApiResult result = e.getApiResult();
        updateRateLimit(result);
        lastApiResult = result;
    }

    private RachioApiResult httpGet(String url, @Nullable String params, PRIORITY priority) throws RachioApiException {
        throttleIfNeeded(priority);
        try {
            return recordApiResult(httpApi.httpGet(url, params));
        } catch (RachioApiException e) {
            recordApiException(e);
            throw e;
        }
    }

    private RachioApiResult httpPut(String url, String data, PRIORITY priority) throws RachioApiException {
        throttleIfNeeded(priority);
        try {
            return recordApiResult(httpApi.httpPut(url, data));
        } catch (RachioApiException e) {
            recordApiException(e);
            throw e;
        }
    }

    private RachioApiResult httpPost(String url, String data, PRIORITY priority) throws RachioApiException {
        throttleIfNeeded(priority);
        try {
            return recordApiResult(httpApi.httpPost(url, data));
        } catch (RachioApiException e) {
            recordApiException(e);
            throw e;
        }
    }

    private RachioApiResult httpDelete(String url, @Nullable String params, PRIORITY priority)
            throws RachioApiException {
        throttleIfNeeded(priority);
        try {
            return recordApiResult(httpApi.httpDelete(url, params));
        } catch (RachioApiException e) {
            recordApiException(e);
            throw e;
        }
    }

    public String getPersonId() {
        return personId;
    }

    public String getExternalId() {
        if (apikey.isEmpty() || bridgeUID == null) {
            return "";
        }
        String apikeyHash = getMD5Hash(apikey);
        String rawValue = "OH_RACHIO_EXTERNALID_" + bridgeUID.toString() + "_" + apikeyHash;
        return getMD5Hash(rawValue);
    }

    public List<String> getLegacyExternalIds() {
        if (apikey.isEmpty()) {
            return List.of();
        }
        List<String> legacyIds = new ArrayList<>();
        String apikeyHash = getMD5Hash(apikey);
        for (int legacySalt = 1; legacySalt <= 50; legacySalt++) {
            String hash = "OH_" + apikeyHash + "_" + legacySalt;
            legacyIds.add(getMD5Hash(hash));
        }
        return legacyIds;
    }

    public void initialize(String apikey, ThingUID bridgeUID) throws RachioApiException {
        initialize(apikey, bridgeUID, PRIORITY.MED);
    }

    public void initialize(String apikey, ThingUID bridgeUID, PRIORITY priority) throws RachioApiException {
        this.apikey = apikey;
        this.bridgeUID = bridgeUID;
        this.rateLimitManager = Objects.requireNonNull(rateLimitManagers.computeIfAbsent(apikey,
                key -> new ClientRateLimitManager(10, Duration.ofSeconds(30))));
        httpApi = new RachioHttp(this.apikey);
        if (!initializePersonId(priority) || !initializeDevices(bridgeUID, priority) || !initializeZones()) {
            throw new RachioApiException("API initialization failed!");
        }
    }

    public HashMap<String, RachioDevice> getDevices() {
        return deviceList;
    }

    @Nullable
    public RachioDevice bindDeviceByRachioId(ThingUID bridgeUID, ThingUID thingUID, String deviceId) {
        RachioDevice dev = getDeviceByRachioId(deviceId);
        if (dev != null) {
            dev.setUID(bridgeUID, thingUID);
            logger.debug("Mapped requested device UID '{}' to Rachio device '{}' using configured deviceId '{}'",
                    thingUID, dev.name, deviceId);
        } else {
            logger.debug("Unable to map requested device UID '{}' using configured deviceId '{}'", thingUID, deviceId);
        }
        return dev;
    }

    @Nullable
    public RachioDevice getDeviceByRachioId(@Nullable String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            return null;
        }

        RachioDevice device = deviceList.get(deviceId);
        if (device != null) {
            return device;
        }

        for (RachioDevice dev : deviceList.values()) {
            if (matchesIdentifierValue(deviceId, dev.id)) {
                return dev;
            }
        }
        return null;
    }

    @Nullable
    public RachioZone getZoneByRachioId(@Nullable String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return null;
        }

        for (RachioDevice dev : deviceList.values()) {
            for (RachioZone zone : dev.getZones().values()) {
                if (matchesIdentifierValue(zoneId, zone.id)) {
                    return zone;
                }
            }
        }
        return null;
    }

    @Nullable
    public RachioDevice getDeviceByZoneRachioId(@Nullable String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return null;
        }

        for (RachioDevice dev : deviceList.values()) {
            for (RachioZone zone : dev.getZones().values()) {
                if (matchesIdentifierValue(zoneId, zone.id)) {
                    return dev;
                }
            }
        }
        return null;
    }

    @Nullable
    public RachioDevice getDevByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID thingUID) {
        return getDevByUID(bridgeUID, thingUID, Collections.emptyMap(), Collections.emptyMap());
    }

    @Nullable
    public RachioDevice getDevByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID thingUID,
            Map<String, String> properties) {
        return getDevByUID(bridgeUID, thingUID, Collections.emptyMap(), properties);
    }

    @Nullable
    public RachioDevice getDevByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID thingUID,
            Map<String, @Nullable Object> configuration, Map<String, String> properties) {
        if (bridgeUID == null || thingUID == null) {
            logger.debug("getDevByUID: Unable map UID to device, bridgeUID={}, deviceUID={}", bridgeUID, thingUID);
            return null;
        }

        String configuredDeviceId = getConfigurationString(configuration, PROPERTY_DEV_ID);
        if (!configuredDeviceId.isBlank()) {
            RachioDevice dev = getDeviceByRachioId(configuredDeviceId);
            if (dev != null) {
                dev.setUID(bridgeUID, thingUID);
                logger.debug(
                        "getDevByUID: mapped requested device UID '{}' to Rachio device '{}' using configured deviceId '{}'",
                        thingUID, dev.name, configuredDeviceId);
                return dev;
            }

            logger.debug("getDevByUID: Unable map UID '{}' to device using configured deviceId '{}'", thingUID,
                    configuredDeviceId);
            return null;
        }

        for (HashMap.Entry<String, RachioDevice> entry : deviceList.entrySet()) {
            RachioDevice dev = entry.getValue();
            @Nullable
            String matchedProperty = getMatchingDeviceProperty(dev, properties);
            if (matchedProperty != null) {
                dev.setUID(bridgeUID, thingUID);
                logger.debug(
                        "getDevByUID: mapped requested device UID '{}' to Rachio device '{}' using Thing property '{}'",
                        thingUID, dev.name, matchedProperty);
                return dev;
            }
        }

        for (HashMap.Entry<String, RachioDevice> entry : deviceList.entrySet()) {
            RachioDevice dev = entry.getValue();
            @Nullable
            ThingUID expectedUID = buildExpectedThingUID(THING_TYPE_DEVICE, bridgeUID, dev.getThingID());
            logger.trace("getDevByUID: requested bridge={}, requested device={}, cached bridge={}, cached device={}, "
                    + "candidate device={}", bridgeUID, thingUID, dev.bridgeUID, dev.devUID, expectedUID);
            if (expectedUID != null && matchesThingUID(expectedUID, thingUID)) {
                dev.setUID(bridgeUID, thingUID);
                logger.trace("Device '{}' found by canonical UID '{}'.", dev.name, expectedUID);
                return dev;
            }
        }

        logger.debug("getDevByUID: Unable map UID to device, bridgeUID={}, deviceUID={}", bridgeUID, thingUID);
        return null;
    }

    @Nullable
    public RachioZone getZoneByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID zoneUID) {
        return getZoneByUID(bridgeUID, zoneUID, Collections.emptyMap(), Collections.emptyMap());
    }

    @Nullable
    public RachioZone getZoneByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID zoneUID,
            Map<String, String> properties) {
        return getZoneByUID(bridgeUID, zoneUID, Collections.emptyMap(), properties);
    }

    @Nullable
    public RachioZone getZoneByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID zoneUID,
            Map<String, @Nullable Object> configuration, Map<String, String> properties) {
        if (bridgeUID == null || zoneUID == null) {
            logger.debug("getZoneByUID: Unable map UID to zone, bridgeUID={}, zoneUID={}", bridgeUID, zoneUID);
            return null;
        }

        String configuredZoneId = getConfigurationString(configuration, PROPERTY_ZONE_ID);
        if (!configuredZoneId.isBlank()) {
            RachioDevice dev = getDeviceByZoneRachioId(configuredZoneId);
            RachioZone zone = getZoneByRachioId(configuredZoneId);
            if (dev != null && zone != null) {
                bindZoneUIDs(dev, zone, bridgeUID, zoneUID);
                logger.debug(
                        "getZoneByUID: mapped requested zone UID '{}' to Rachio zone '{}' using configured zoneId '{}'",
                        zoneUID, zone.name, configuredZoneId);
                return zone;
            }

            logger.debug("getZoneByUID: Unable map UID '{}' to zone using configured zoneId '{}'", zoneUID,
                    configuredZoneId);
            return null;
        }

        for (HashMap.Entry<String, RachioDevice> de : deviceList.entrySet()) {
            RachioDevice dev = de.getValue();
            @Nullable
            ThingUID expectedDevUID = buildExpectedThingUID(THING_TYPE_DEVICE, bridgeUID, dev.getThingID());
            if (expectedDevUID == null) {
                logger.trace("getZoneByUID: Skip device '{}' because no valid device Thing UID can be built", dev.name);
                continue;
            }

            HashMap<String, RachioZone> zoneList = dev.getZones();
            for (HashMap.Entry<String, RachioZone> ze : zoneList.entrySet()) {
                RachioZone zone = ze.getValue();
                @Nullable
                String matchedProperty = getMatchingZoneProperty(zone, properties);
                if (matchedProperty != null) {
                    bindZoneUIDs(dev, zone, bridgeUID, zoneUID);
                    logger.debug(
                            "getZoneByUID: mapped requested zone UID '{}' to Rachio zone '{}' using Thing property '{}'",
                            zoneUID, zone.name, matchedProperty);
                    return zone;
                }
            }
        }

        for (HashMap.Entry<String, RachioDevice> de : deviceList.entrySet()) {
            RachioDevice dev = de.getValue();
            @Nullable
            ThingUID expectedDevUID = buildExpectedThingUID(THING_TYPE_DEVICE, bridgeUID, dev.getThingID());
            if (expectedDevUID == null) {
                logger.trace("getZoneByUID: Skip device '{}' because no valid device Thing UID can be built", dev.name);
                continue;
            }

            HashMap<String, RachioZone> zoneList = dev.getZones();
            for (HashMap.Entry<String, RachioZone> ze : zoneList.entrySet()) {
                RachioZone zone = ze.getValue();
                @Nullable
                ThingUID expectedZoneUID = buildExpectedThingUID(THING_TYPE_ZONE, bridgeUID, zone.getThingID());
                logger.trace(
                        "getZoneByUID: requested bridge={}, requested zone={}, cached device={}, cached zone={}, "
                                + "candidate device={}, candidate zone={}",
                        bridgeUID, zoneUID, zone.getDevUID(), zone.getUID(), expectedDevUID, expectedZoneUID);
                if (expectedZoneUID != null && matchesThingUID(expectedZoneUID, zoneUID)) {
                    bindZoneUIDs(dev, zone, bridgeUID, zoneUID);
                    logger.trace("Zone '{}' found by canonical UID '{}'.", zone.name, expectedZoneUID);
                    return zone;
                }
            }
        }

        logger.debug("getZoneByUID: Unable map UID to zone, bridgeUID={}, zoneUID={}", bridgeUID, zoneUID);
        return null;
    }

    private void bindZoneUIDs(RachioDevice dev, RachioZone zone, ThingUID bridgeUID, ThingUID zoneUID) {
        @Nullable
        ThingUID expectedDevUID = buildExpectedThingUID(THING_TYPE_DEVICE, bridgeUID, dev.getThingID());
        if (expectedDevUID != null) {
            dev.setUID(bridgeUID, expectedDevUID);
        }
        zone.setUID(expectedDevUID, zoneUID);
    }

    private @Nullable String getMatchingDeviceProperty(RachioDevice dev, Map<String, String> properties) {
        if (matchesProperty(properties, PROPERTY_DEV_ID, dev.id)) {
            return PROPERTY_DEV_ID;
        }
        if (matchesProperty(properties, Thing.PROPERTY_MAC_ADDRESS, dev.macAddress)) {
            return Thing.PROPERTY_MAC_ADDRESS;
        }
        if (matchesProperty(properties, Thing.PROPERTY_SERIAL_NUMBER, dev.serialNumber)) {
            return Thing.PROPERTY_SERIAL_NUMBER;
        }
        return null;
    }

    private @Nullable String getMatchingZoneProperty(RachioZone zone, Map<String, String> properties) {
        if (matchesProperty(properties, PROPERTY_ZONE_ID, zone.id)) {
            return PROPERTY_ZONE_ID;
        }
        return null;
    }

    private String getConfigurationString(Map<String, @Nullable Object> configuration, String parameterName) {
        @Nullable
        Object configValue = getConfigurationValue(configuration, parameterName);
        return configValue != null ? configValue.toString().trim() : "";
    }

    private @Nullable Object getConfigurationValue(Map<String, @Nullable Object> configuration, String parameterName) {
        @Nullable
        Object value = configuration.get(parameterName);
        if (value != null) {
            return value;
        }

        for (Map.Entry<String, @Nullable Object> entry : configuration.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(parameterName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesProperty(Map<String, String> properties, String propertyName,
            @Nullable String expectedValue) {
        @Nullable
        String actualValue = properties.get(propertyName);
        return matchesIdentifierValue(actualValue, expectedValue);
    }

    private boolean matchesIdentifierValue(@Nullable String actualValue, @Nullable String expectedValue) {
        return actualValue != null && !actualValue.isBlank() && expectedValue != null && !expectedValue.isBlank()
                && actualValue.equalsIgnoreCase(expectedValue);
    }

    private @Nullable ThingUID buildExpectedThingUID(ThingTypeUID thingTypeUID, ThingUID bridgeUID,
            @Nullable String thingId) {
        if (thingId == null || thingId.isBlank()) {
            return null;
        }
        try {
            return new ThingUID(thingTypeUID, bridgeUID, thingId);
        } catch (IllegalArgumentException e) {
            logger.trace("Unable to build Rachio Thing UID for thing type '{}', bridge '{}', thing id '{}'",
                    thingTypeUID, bridgeUID, thingId);
            return null;
        }
    }

    private boolean matchesThingUID(ThingUID expectedUID, ThingUID requestedUID) {
        return expectedUID.equals(requestedUID)
                || expectedUID.getAsString().equalsIgnoreCase(requestedUID.getAsString());
    }

    private Boolean initializePersonId(PRIORITY priority) throws RachioApiException {
        if (!personId.isEmpty()) {
            logger.trace("Using cached personId ('{}').", personId);
            return true;
        }

        lastApiResult = httpGet(APIURL_BASE + APIURL_GET_PERSON, null, priority);
        Gson gson = new Gson();
        RachioCloudPersonId pid = gson.fromJson(lastApiResult.resultString, RachioCloudPersonId.class);
        personId = pid.id;
        logger.debug("Using personId '{}'", personId);
        if (lastApiResult.isRateLimitCritical()) {
            String errorMessage = MessageFormat.format(
                    "Rachio Cloud API Rate Limit is critical ({0} of {1}), reset at {2}", lastApiResult.rateRemaining,
                    lastApiResult.rateLimit, lastApiResult.rateReset);
            throw new RachioApiException(errorMessage, lastApiResult);
        }
        return true;
    }

    public String getUserInfo() {
        return !userName.isEmpty() ? fullName + "(" + userName + ", " + email + ")" : "";
    }

    public void stopWatering(String deviceId) throws RachioApiException {
        logger.debug("Stop watering for device '{}'", deviceId);
        httpPut(APIURL_BASE + APIURL_DEV_PUT_STOP, "{ \"id\" : \"" + deviceId + "\" }", PRIORITY.HI);
    }

    public void enableDevice(String deviceId) throws RachioApiException {
        logger.debug("Enable device '{}'.", deviceId);
        httpPut(APIURL_BASE + APIURL_DEV_PUT_ON, "{ \"id\" : \"" + deviceId + "\" }", PRIORITY.HI);
    }

    public void disableDevice(String deviceId) throws RachioApiException {
        logger.debug("Disable device '{}'.", deviceId);
        httpPut(APIURL_BASE + APIURL_DEV_PUT_OFF, "{ \"id\" : \"" + deviceId + "\" }", PRIORITY.HI);
    }

    public void rainDelay(String deviceId, Integer delay) throws RachioApiException {
        logger.debug("Start dain relay for device '{}'.", deviceId);
        httpPut(APIURL_BASE + APIURL_DEV_PUT_RAIN_DELAY,
                "{ \"id\" : \"" + deviceId + "\", \"duration\" : " + delay + " }", PRIORITY.HI);
    }

    public void pauseZoneRun(String deviceId, int duration) throws RachioApiException {
        logger.debug("Pause active zone run for device '{}' for {} sec.", deviceId, duration);
        httpPut(APIURL_BASE + APIURL_DEV_PUT_PAUSE_ZONE_RUN,
                "{ \"id\" : \"" + deviceId + "\", \"duration\" : " + duration + " }", PRIORITY.HI);
    }

    public void resumeZoneRun(String deviceId) throws RachioApiException {
        logger.debug("Resume active zone run for device '{}'.", deviceId);
        httpPut(APIURL_BASE + APIURL_DEV_PUT_RESUME_ZONE_RUN, "{ \"id\" : \"" + deviceId + "\" }", PRIORITY.HI);
    }

    public void runMultilpeZones(String zoneListJson) throws RachioApiException {
        logger.debug("Start multiple zones '{}'.", zoneListJson);
        httpPut(APIURL_BASE + APIURL_ZONE_PUT_MULTIPLE_START, zoneListJson, PRIORITY.HI);
    }

    public void runZone(String zoneId, int duration) throws RachioApiException {
        logger.debug("Start zone '{}' for {} sec.", zoneId, duration);
        httpPut(APIURL_BASE + APIURL_ZONE_PUT_START, "{ \"id\" : \"" + zoneId + "\", \"duration\" : " + duration + " }",
                PRIORITY.HI);
    }

    public void enableZone(String zoneId) throws RachioApiException {
        logger.debug("Enable zone '{}'.", zoneId);
        httpPut(APIURL_BASE + APIURL_ZONE_PUT_ENABLE, "{ \"id\" : \"" + zoneId + "\" }", PRIORITY.HI);
    }

    public void disableZone(String zoneId) throws RachioApiException {
        logger.debug("Disable zone '{}'.", zoneId);
        httpPut(APIURL_BASE + APIURL_ZONE_PUT_DISABLE, "{ \"id\" : \"" + zoneId + "\" }", PRIORITY.HI);
    }

    public void getDeviceInfo(String deviceId) throws RachioApiException {
        httpGet(APIURL_BASE + APIURL_GET_DEVICE + "/" + deviceId, null, PRIORITY.MED);
    }

    public void registerWebHook(String deviceId, String callbackUrl, String callbackUsername, String callbackPassword,
            @Nullable String externalId, Boolean clearAllCallbacks) throws RachioApiException {
        logger.debug("Register webhook for device '{}', externalId={}, clearAllCallbacks={}", deviceId, externalId,
                clearAllCallbacks);

        String registrationUrl;
        try {
            registrationUrl = buildWebhookRegistrationUrl(callbackUrl, callbackUsername, callbackPassword);
        } catch (RachioApiException e) {
            logger.warn("Failed to build callback URL for device '{}': {}", deviceId, e.getMessage());
            throw e;
        }

        logger.debug("Register WebHook for controller '{}'", deviceId);
        List<String> eventTypes = getIrrigationControllerEventTypes();
        try {
            String json = httpGet(APIURL_CLOUD_REST_BASE + WEBHOOK_LIST,
                    WEBHOOK_QUERY_CONTROLLER_ID + "=" + urlEncode(deviceId), PRIORITY.MED).resultString;
            boolean matchingWebhookExists = deleteExistingWebHooks(json, deviceId, registrationUrl,
                    externalId != null ? externalId : "", getKnownExternalIds(externalId), clearAllCallbacks,
                    eventTypes);
            if (matchingWebhookExists) {
                logger.debug("Retain existing matching webhook for controller '{}'; createWebhook is not needed",
                        deviceId);
                return;
            }
        } catch (RuntimeException e) {
            logger.debug("Deleting WebHook(s) failed: {}", e.getMessage());
        }

        Map<String, Object> jsonData = Map.of("resourceId", Map.of("irrigationControllerId", deviceId), "externalId",
                externalId != null ? externalId : "", "url", registrationUrl, "eventTypes", eventTypes);
        try {
            httpPost(APIURL_CLOUD_REST_BASE + WEBHOOK_CREATE, new Gson().toJson(jsonData), PRIORITY.HI);
        } catch (RachioApiException e) {
            throw sanitizeWebhookRegistrationException(e, registrationUrl);
        }
    }

    /**
     * Build the URL sent to Rachio when creating a webhook. Rachio enables webhook Basic Authentication by accepting a
     * URL with an encoded userinfo section and then sending those credentials as an Authorization header.
     */
    private String buildWebhookRegistrationUrl(String callbackUrl, String callbackUsername, String callbackPassword)
            throws RachioApiException {
        if (callbackUrl.isBlank()) {
            throw new RachioApiException("Webhook callback URL is not configured.");
        }

        boolean usernameConfigured = !callbackUsername.isEmpty();
        boolean passwordConfigured = !callbackPassword.isEmpty();
        if (usernameConfigured != passwordConfigured) {
            throw new RachioApiException(
                    "Webhook Basic Auth configuration is incomplete: both callbackUsername and callbackPassword must be provided together.");
        }

        String trimmedCallbackUrl = callbackUrl.trim();
        if (!usernameConfigured) {
            URI callbackUri = parseWebhookCallbackUri(trimmedCallbackUrl, true);
            return callbackUri.toASCIIString();
        }

        URI callbackUri;
        try {
            callbackUri = parseWebhookCallbackUri(trimmedCallbackUrl, true);
        } catch (RachioApiException e) {
            String callbackUrlWithoutUserInfo = stripPotentialEmbeddedUserInfo(trimmedCallbackUrl);
            if (callbackUrlWithoutUserInfo.equals(trimmedCallbackUrl)) {
                throw e;
            }
            logger.debug(
                    "Callback URL contains embedded credentials, but explicit callbackUsername/callbackPassword fields are configured; using the explicit fields.");
            callbackUri = parseWebhookCallbackUri(callbackUrlWithoutUserInfo, true);
        }

        String rawAuthority = callbackUri.getRawAuthority();
        if (rawAuthority == null) {
            throw new RachioApiException("Invalid callback URL format: missing URL authority.");
        }

        String authorityWithoutUserInfo = rawAuthority;
        int userInfoSeparator = rawAuthority.lastIndexOf('@');
        if (userInfoSeparator >= 0) {
            logger.debug(
                    "Callback URL contains embedded credentials, but explicit callbackUsername/callbackPassword fields are configured; using the explicit fields.");
            authorityWithoutUserInfo = rawAuthority.substring(userInfoSeparator + 1);
        }

        if (authorityWithoutUserInfo.isEmpty() || authorityWithoutUserInfo.contains("@")) {
            throw new RachioApiException("Invalid callback URL format: invalid URL authority.");
        }

        String encodedUserInfo = encodeURIComponent(callbackUsername) + ":" + encodeURIComponent(callbackPassword);
        String registrationUrl = buildUriString(callbackUri, encodedUserInfo + "@" + authorityWithoutUserInfo);
        return parseWebhookCallbackUri(registrationUrl, true).toASCIIString();
    }

    private URI parseWebhookCallbackUri(String callbackUrl, boolean requireValidHost) throws RachioApiException {
        try {
            URI uri = new URI(callbackUrl);
            if (!uri.isAbsolute() || uri.getRawAuthority() == null) {
                throw new RachioApiException("Invalid callback URL format: expected an absolute URL with a host.");
            }
            if (requireValidHost && uri.getHost() == null) {
                if (uri.getRawAuthority().contains("@")) {
                    throw new RachioApiException("Invalid callback URL format: malformed embedded credentials.");
                }
                throw new RachioApiException("Invalid callback URL format: expected a valid URL host.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new RachioApiException("Invalid callback URL format: " + e.getReason());
        }
    }

    private String stripPotentialEmbeddedUserInfo(String callbackUrl) {
        int authorityStart = callbackUrl.indexOf("://");
        if (authorityStart < 0) {
            return callbackUrl;
        }

        authorityStart += 3;
        int userInfoSeparator = callbackUrl.lastIndexOf('@');
        if (userInfoSeparator < authorityStart) {
            return callbackUrl;
        }
        return callbackUrl.substring(0, authorityStart) + callbackUrl.substring(userInfoSeparator + 1);
    }

    private String buildUriString(URI uri, String authority) {
        StringBuilder url = new StringBuilder();
        url.append(uri.getScheme()).append("://").append(authority);

        String path = uri.getRawPath();
        if (path != null) {
            url.append(path);
        }
        String query = uri.getRawQuery();
        if (query != null) {
            url.append("?").append(query);
        }
        String fragment = uri.getRawFragment();
        if (fragment != null) {
            url.append("#").append(fragment);
        }
        return url.toString();
    }

    private String sanitizeCallbackUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(url);
            if (uri.getRawUserInfo() == null) {
                return uri.toASCIIString();
            }
            URI sanitizedUri = new URI(uri.getScheme(), "***:***", uri.getHost(), uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment());
            return sanitizedUri.toASCIIString();
        } catch (RuntimeException | URISyntaxException e) {
            return "<redacted-callback-url>";
        }
    }

    private RachioApiException sanitizeWebhookRegistrationException(RachioApiException e, String registrationUrl) {
        String sanitizedUrl = sanitizeCallbackUrl(registrationUrl);
        RachioApiResult result = e.getApiResult();
        result.resultString = result.resultString.replace(registrationUrl, sanitizedUrl);

        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = "Rachio webhook registration failed";
        } else {
            message = message.replace(registrationUrl, sanitizedUrl);
        }
        return new RachioApiException(message, result);
    }

    /**
     * Encodes a URI component according to RFC 3986.
     * Unreserved characters (A-Z, a-z, 0-9, -, ., _, ~) are not encoded.
     * All other characters are percent-encoded.
     *
     * @param component the component to encode
     * @return the encoded component
     */
    private String encodeURIComponent(String component) {
        StringBuilder result = new StringBuilder();
        for (byte b : component.getBytes(StandardCharsets.UTF_8)) {
            int value = b & 0xFF;
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')
                    || value == '-' || value == '.' || value == '_' || value == '~') {
                result.append((char) value);
            } else {
                result.append('%').append(HEX_DIGITS[value >> 4]).append(HEX_DIGITS[value & 0x0F]);
            }
        }
        return result.toString();
    }

    private Collection<String> getKnownExternalIds(@Nullable String externalId) {
        if (externalId == null) {
            externalId = "";
        }
        List<String> knownExternalIds = new ArrayList<>(getLegacyExternalIds());
        if (!externalId.isBlank() && !knownExternalIds.contains(externalId)) {
            knownExternalIds.add(externalId);
        }
        return knownExternalIds;
    }

    private List<String> getIrrigationControllerEventTypes() {
        List<String> eventTypes = new ArrayList<>(List.of(EVENT_DEVICE_ZONE_RUN_STARTED, EVENT_DEVICE_ZONE_RUN_STOPPED,
                EVENT_DEVICE_ZONE_RUN_COMPLETED, EVENT_DEVICE_ZONE_RUN_PAUSED, EVENT_SCHEDULE_STARTED,
                EVENT_SCHEDULE_STOPPED, EVENT_SCHEDULE_COMPLETED, EVENT_RAIN_SKIP, EVENT_CLIMATE_SKIP,
                EVENT_FREEZE_SKIP, EVENT_WIND_SKIP, EVENT_NO_SKIP));

        List<String> supportedEventTypes = getSupportedWebhookEventTypes();
        if (supportedEventTypes.contains(EVENT_RAIN_SENSOR_DETECTION_ON)) {
            eventTypes.add(EVENT_RAIN_SENSOR_DETECTION_ON);
        }
        if (supportedEventTypes.contains(EVENT_RAIN_SENSOR_DETECTION_OFF)) {
            eventTypes.add(EVENT_RAIN_SENSOR_DETECTION_OFF);
        }
        if (supportedEventTypes.contains(EVENT_RAIN_DELAY_ON)) {
            eventTypes.add(EVENT_RAIN_DELAY_ON);
        }
        if (supportedEventTypes.contains(EVENT_RAIN_DELAY_OFF)) {
            eventTypes.add(EVENT_RAIN_DELAY_OFF);
        }

        return eventTypes;
    }

    private List<String> getSupportedWebhookEventTypes() {
        try {
            String json = httpGet(APIURL_CLOUD_REST_BASE + WEBHOOK_LIST_EVENT_TYPES, null, PRIORITY.MED).resultString;
            return parseWebhookEventTypeList(json);
        } catch (RachioApiException e) {
            logger.debug("Unable to query supported webhook event types: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> parseWebhookEventTypeList(String json) {
        Gson gson = new Gson();
        JsonElement root = JsonParser.parseString(json);
        JsonArray entries;
        if (root.isJsonArray()) {
            entries = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonElement eventTypes = root.getAsJsonObject().get("eventTypes");
            if ((eventTypes == null) || !eventTypes.isJsonArray()) {
                eventTypes = root.getAsJsonObject().get("data");
            }
            if ((eventTypes == null) || !eventTypes.isJsonArray()) {
                return new ArrayList<>();
            }
            entries = eventTypes.getAsJsonArray();
        } else {
            return new ArrayList<>();
        }

        List<String> supportedTypes = new ArrayList<>();
        for (JsonElement entry : entries) {
            if (entry == null || !entry.isJsonPrimitive()) {
                continue;
            }
            supportedTypes.add(entry.getAsString());
        }
        return supportedTypes;
    }

    private boolean deleteExistingWebHooks(String json, String deviceId, String callbackUrl, String expectedExternalId,
            Collection<String> externalIds, Boolean clearAllCallbacks, List<String> expectedEventTypes) {
        boolean deleteAll = Boolean.TRUE.equals(clearAllCallbacks);
        boolean matchingWebhookRetained = false;
        List<RachioApiWebHookEntry> webhooks = parseWebHookList(json);
        logger.debug("Registered webhook count for controller '{}': {}", deviceId, webhooks.size());
        for (RachioApiWebHookEntry whe : webhooks) {
            logger.debug("WebHook: id='{}', url='{}', externalId='{}', controllerId='{}'", whe.id,
                    sanitizeCallbackUrl(whe.url), whe.externalId,
                    whe.resourceId == null ? null : whe.resourceId.irrigationControllerId);
            boolean matchesExternalId = externalIds.stream().anyMatch(id -> Objects.equals(whe.externalId, id));
            boolean matchesExpectedWebhook = webhookMatchesExpected(whe, deviceId, callbackUrl, expectedExternalId,
                    expectedEventTypes);
            if (deleteAll) {
                try {
                    logger.debug("Delete existing webhook '{}' for controller '{}' because clearAllCallbacks=true",
                            whe.id, deviceId);
                    httpDelete(APIURL_CLOUD_REST_BASE + WEBHOOK_DELETE + whe.id, null, PRIORITY.MED);
                } catch (RachioApiException e) {
                    logger.debug("Deleting WebHook '{}' failed: {}", whe.id, e.getMessage());
                }
            } else if (matchesExpectedWebhook && !matchingWebhookRetained) {
                matchingWebhookRetained = true;
                logger.debug("Retain existing matching webhook '{}' for controller '{}'", whe.id, deviceId);
            } else if (Objects.equals(whe.url, callbackUrl) || matchesExternalId) {
                try {
                    logger.debug(
                            "Delete stale or duplicate webhook '{}' for controller '{}' because it matches this binding instance",
                            whe.id, deviceId);
                    httpDelete(APIURL_CLOUD_REST_BASE + WEBHOOK_DELETE + whe.id, null, PRIORITY.MED);
                } catch (RachioApiException e) {
                    logger.debug("Deleting WebHook '{}' failed: {}", whe.id, e.getMessage());
                }
            } else {
                logger.debug("Retain existing webhook '{}' for controller '{}'; not owned by this binding instance",
                        whe.id, deviceId);
            }
        }
        return matchingWebhookRetained;
    }

    private boolean webhookMatchesExpected(RachioApiWebHookEntry webhook, String deviceId, String callbackUrl,
            String expectedExternalId, List<String> expectedEventTypes) {
        return Objects.equals(webhook.url, callbackUrl) && Objects.equals(webhook.externalId, expectedExternalId)
                && webhookResourceMatches(webhook, deviceId)
                && webhookEventTypesMatch(webhook.eventTypes, expectedEventTypes);
    }

    private boolean webhookResourceMatches(RachioApiWebHookEntry webhook, String deviceId) {
        return webhook.resourceId != null && Objects.equals(webhook.resourceId.irrigationControllerId, deviceId);
    }

    private boolean webhookEventTypesMatch(@Nullable List<String> actualEventTypes, List<String> expectedEventTypes) {
        if (actualEventTypes == null || actualEventTypes.isEmpty()) {
            return true;
        }
        return actualEventTypes.size() == expectedEventTypes.size() && actualEventTypes.containsAll(expectedEventTypes)
                && expectedEventTypes.containsAll(actualEventTypes);
    }

    private List<RachioApiWebHookEntry> parseWebHookList(String json) {
        Gson gson = new Gson();
        JsonElement root = JsonParser.parseString(json);
        JsonArray entries;
        if (root.isJsonArray()) {
            entries = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonElement webhooks = root.getAsJsonObject().get("webhooks");
            if ((webhooks == null) || !webhooks.isJsonArray()) {
                webhooks = root.getAsJsonObject().get("data");
            }
            if ((webhooks == null) || !webhooks.isJsonArray()) {
                @Nullable
                RachioApiWebHookList list = gson.fromJson(root, RachioApiWebHookList.class);
                return list != null ? list.webhooks : new ArrayList<>();
            }
            entries = webhooks.getAsJsonArray();
        } else {
            return new ArrayList<>();
        }

        List<RachioApiWebHookEntry> webhooks = new ArrayList<>();
        for (@Nullable
        JsonElement entry : entries) {
            if (entry == null) {
                continue;
            }
            @Nullable
            RachioApiWebHookEntry webhook = gson.fromJson(entry, RachioApiWebHookEntry.class);
            if (webhook != null) {
                webhooks.add(webhook);
            }
        }
        return webhooks;
    }

    private Boolean initializeDevices(ThingUID BridgeUID, PRIORITY priority) throws RachioApiException {
        String json = "";
        if (httpApi == null) {
            logger.debug("RachioApi.initializeDevices: httpAPI not initialized");
            return false;
        }
        json = httpGet(APIURL_BASE + APIURL_GET_PERSONID + "/" + personId, null, priority).resultString;
        logger.trace("Initialize from JSON='{}'", json);

        Gson gson = new Gson();
        RachioCloudStatus cloudStatus = gson.fromJson(json, RachioCloudStatus.class);
        userName = cloudStatus.username;
        fullName = cloudStatus.fullName;
        email = cloudStatus.email;

        deviceList = new HashMap<String, RachioDevice>(); // discard current list
        for (int i = 0; i < cloudStatus.devices.size(); i++) {
            RachioCloudDevice device = cloudStatus.devices.get(i);
            if (!device.deleted) {
                deviceList.put(device.id, new RachioDevice(device));
                logger.trace("Device '{}' initialized, {} zones.", device.name, device.zones.size());
            }
        }
        return true;
    }

    public Boolean initializeZones() {
        return true;
    }

    public Map<String, String> fillProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
        properties.put(RachioBindingConstants.PROPERTY_APIKEY, apikey);
        properties.put(RachioBindingConstants.PROPERTY_PERSON_ID, personId);
        properties.put(RachioBindingConstants.PROPERTY_PERSON_USER, userName);
        properties.put(RachioBindingConstants.PROPERTY_PERSON_NAME, fullName);
        properties.put(RachioBindingConstants.PROPERTY_PERSON_EMAIL, email);
        return properties;
    }

    /**
     * Given a string, return the MD5 hash of the String.
     *
     * @param unhashed The string contents to be hashed.
     * @return MD5 Hashed value of the String. Null if there is a problem hashing the String.
     */
    protected static String getMD5Hash(String unhashed) {
        try {
            byte[] bytesOfMessage = unhashed.getBytes(UTF8_CHAR_SET);

            MessageDigest md5 = MessageDigest.getInstance(MD5_HASH_ALGORITHM);

            byte[] hash = md5.digest(bytesOfMessage);

            StringBuilder sb = new StringBuilder(2 * hash.length);

            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }

            String digest = sb.toString();

            return digest;
        } catch (RuntimeException | UnsupportedEncodingException | NoSuchAlgorithmException e) {
            // logger.warn("Unexpected exception while generating MD5: {} ({})", e.getMessage(), e.getClass());
            return "";
        }
    }

    public static boolean isValidWebHookSignature(@Nullable String signature, byte[] requestBody, String apikey) {
        if (signature == null || apikey.isEmpty()) {
            return false;
        }

        byte[] signatureBytes = decodeWebHookSignature(signature);
        if (signatureBytes.length == 0) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(new SecretKeySpec(apikey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM));
            return MessageDigest.isEqual(mac.doFinal(requestBody), signatureBytes);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] decodeWebHookSignature(String signature) {
        String hexSignature = signature.trim();
        if (hexSignature.regionMatches(true, 0, "sha256=", 0, 7)) {
            hexSignature = hexSignature.substring(7);
        }
        if (hexSignature.length() != WEBHOOK_SIGNATURE_LENGTH_BYTES * 2) {
            return new byte[0];
        }

        byte[] bytes = new byte[WEBHOOK_SIGNATURE_LENGTH_BYTES];
        for (int i = 0; i < hexSignature.length(); i += 2) {
            int high = Character.digit(hexSignature.charAt(i), 16);
            int low = Character.digit(hexSignature.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return new byte[0];
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    @SuppressWarnings("rawtypes")
    public static void copyMatchingFields(Object fromObj, Object toObj) {
        Class fromClass = fromObj.getClass();
        Class toClass = toObj.getClass();

        Field[] fields = fromClass.getFields(); // .getDeclaredFields();
        for (Field f : fields) {
            try {
                String fname = f.getName();
                Field t = toClass.getSuperclass().getDeclaredField(fname);

                if (t.getType() == f.getType()) {
                    // extend this if to copy more immutable types if interested
                    if (t.getType() == String.class || t.getType() == int.class || t.getType() == long.class
                            || t.getType() == double.class || t.getType() == char.class || t.getType() == boolean.class
                            || t.getType() == Double.class || t.getType() == Integer.class || t.getType() == Long.class
                            || t.getType() == Character.class || t.getType() == Boolean.class) {
                        f.setAccessible(true);
                        t.setAccessible(true);
                        t.set(toObj, f.get(fromObj));
                    } else if (t.getType() == Date.class) {
                        // dates are not immutable, so clone non-null dates into the destination object
                        Date d = (Date) f.get(fromObj);
                        f.setAccessible(true);
                        t.setAccessible(true);
                        t.set(toObj, d != null ? d.clone() : null);
                    } else if (t.getType() == java.util.ArrayList.class) {
                        // dates are not immutable, so clone non-null dates into the destination object
                        ArrayList a = (ArrayList) f.get(fromObj);
                        f.setAccessible(true);
                        t.setAccessible(true);
                        t.set(toObj, a != null ? a.clone() : null);
                    } else {
                        // logger.debug("RachioApiInternal: Unable to update field '{}', '{}'", t.getName(),
                        // t.getType());
                    }
                }
            } catch (NoSuchFieldException ex) {
                // skip it
            } catch (IllegalAccessException ex) {
                // Unable to copy field
            }
        }
    }
}
