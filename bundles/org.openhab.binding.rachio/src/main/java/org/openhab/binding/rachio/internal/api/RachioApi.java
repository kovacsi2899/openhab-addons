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
    public RachioDevice getDevByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID thingUID) {
        for (HashMap.Entry<String, RachioDevice> entry : deviceList.entrySet()) {
            RachioDevice dev = entry.getValue();
            logger.trace("getDevByUID: bridge {} / {}, device {} / {}", bridgeUID, dev.bridgeUID, thingUID, dev.devUID);
            if (dev.bridgeUID.equals(bridgeUID) && dev.getUID().equals(thingUID)) {
                logger.trace("Device '{}' found.", dev.name);
                return dev;
            }
        }
        logger.debug("getDevByUID: Unable map UID to device");
        return null;
    }

    @Nullable
    public RachioZone getZoneByUID(@Nullable ThingUID bridgeUID, @Nullable ThingUID zoneUID) {
        HashMap<String, RachioDevice> deviceList = getDevices();
        if (deviceList == null) {
            return null;
        }
        for (HashMap.Entry<String, RachioDevice> de : deviceList.entrySet()) {
            RachioDevice dev = de.getValue();

            HashMap<String, RachioZone> zoneList = dev.getZones();
            for (HashMap.Entry<String, RachioZone> ze : zoneList.entrySet()) {
                RachioZone zone = ze.getValue();
                if (zone.getUID().equals(zoneUID)) {
                    return zone;
                }
            }
        }
        return null;
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

    public void registerWebHook(String deviceId, String callbackUrl, @Nullable String externalId,
            Boolean clearAllCallbacks) throws RachioApiException {
        logger.debug("Register webhook for device '{}', externalId={}, clearAllCallbacks={}", deviceId, externalId,
                clearAllCallbacks);

        String encodedUrl;
        try {
            encodedUrl = encodeCallbackUrl(callbackUrl);
        } catch (RachioApiException e) {
            logger.warn("Failed to encode callback URL for device '{}'; callback URL is malformed", deviceId);
            throw e;
        }

        logger.debug("Register WebHook for controller '{}'", deviceId);
        try {
            String json = httpGet(APIURL_CLOUD_REST_BASE + WEBHOOK_LIST,
                    WEBHOOK_QUERY_CONTROLLER_ID + "=" + urlEncode(deviceId), PRIORITY.MED).resultString;
            deleteExistingWebHooks(json, deviceId, encodedUrl, getKnownExternalIds(externalId), clearAllCallbacks);
        } catch (RuntimeException e) {
            logger.debug("Deleting WebHook(s) failed: {}", e.getMessage());
        }

        Map<String, Object> jsonData = Map.of("resourceId", Map.of("irrigationControllerId", deviceId), "externalId",
                externalId != null ? externalId : "", "url", encodedUrl, "eventTypes",
                getIrrigationControllerEventTypes());
        httpPost(APIURL_CLOUD_REST_BASE + WEBHOOK_CREATE, new Gson().toJson(jsonData), PRIORITY.HI);
    }

    /**
     * Encodes the callback URL to ensure proper URL encoding of user credentials.
     * Uses RFC 3986 compliant encoding for the userinfo portion only.
     *
     * @param callbackUrl the raw callback URL
     * @return the URL with properly encoded userinfo
     * @throws RachioApiException if the URL is malformed
     */
    private String encodeCallbackUrl(String callbackUrl) throws RachioApiException {
        try {
            URI uri = new URI(callbackUrl);

            // If userinfo exists, encode it properly
            if (uri.getUserInfo() != null) {
                String encodedUserInfo = encodeUserInfo(uri.getUserInfo());

                // Reconstruct URI with encoded userinfo
                uri = new URI(uri.getScheme(), encodedUserInfo, uri.getHost(), uri.getPort(), uri.getPath(),
                        uri.getQuery(), uri.getFragment());
            }

            return uri.toASCIIString();
        } catch (URISyntaxException e) {
            throw new RachioApiException("Invalid callback URL format: " + e.getReason());
        }
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

    /**
     * Encodes userinfo (username:password) according to RFC 3986.
     * Only encodes characters that are not unreserved in the userinfo context.
     *
     * @param userInfo the raw userinfo string
     * @return the properly encoded userinfo
     */
    private String encodeUserInfo(String userInfo) {
        // Basic Auth user-info uses the first colon as separator; later colons are part of the password.
        int firstColon = userInfo.indexOf(':');
        if (firstColon > 0) {
            String username = userInfo.substring(0, firstColon);
            String password = userInfo.substring(firstColon + 1);
            return encodeURIComponent(username) + ":" + encodeURIComponent(password);
        }
        // Username only (no password)
        return encodeURIComponent(userInfo);
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
        // RFC 3986 unreserved characters: A-Z a-z 0-9 - . _ ~
        // Encode everything else as %XX
        StringBuilder result = new StringBuilder();
        for (char c : component.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.'
                    || c == '_' || c == '~') {
                result.append(c);
            } else {
                result.append('%').append(String.format("%02X", (int) c));
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

    private void deleteExistingWebHooks(String json, String deviceId, String callbackUrl,
            Collection<String> externalIds, Boolean clearAllCallbacks) {
        boolean deleteAll = Boolean.TRUE.equals(clearAllCallbacks);
        List<RachioApiWebHookEntry> webhooks = parseWebHookList(json);
        logger.debug("Registered webhook count for controller '{}': {}", deviceId, webhooks.size());
        for (RachioApiWebHookEntry whe : webhooks) {
            logger.debug("WebHook: id='{}', url='{}', externalId='{}', controllerId='{}'", whe.id,
                    sanitizeCallbackUrl(whe.url), whe.externalId,
                    whe.resourceId == null ? null : whe.resourceId.irrigationControllerId);
            boolean matchesExternalId = externalIds.stream().anyMatch(id -> Objects.equals(whe.externalId, id));
            if (deleteAll) {
                try {
                    logger.debug("Delete existing webhook '{}' for controller '{}' because clearAllCallbacks=true",
                            whe.id, deviceId);
                    httpDelete(APIURL_CLOUD_REST_BASE + WEBHOOK_DELETE + whe.id, null, PRIORITY.MED);
                } catch (RachioApiException e) {
                    logger.debug("Deleting WebHook '{}' failed: {}", whe.id, e.getMessage());
                }
            } else if (Objects.equals(whe.url, callbackUrl) || matchesExternalId) {
                try {
                    logger.debug(
                            "Delete duplicate webhook '{}' for controller '{}' because it matches this binding instance",
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
