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
package org.openhab.binding.rachio.internal;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link RachioBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioBindingConstants {

    public static final String BINDING_ID = "rachio";
    public static final String BINDING_VENDOR = "Rachio";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_CLOUD = new ThingTypeUID(BINDING_ID, "cloud");
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BINDING_ID, "device");
    public static final ThingTypeUID THING_TYPE_ZONE = new ThingTypeUID(BINDING_ID, "zone");
    public static final ThingTypeUID THING_TYPE_SCHEDULE = new ThingTypeUID(BINDING_ID, "schedule");
    public static final ThingTypeUID THING_TYPE_FLEXSCHEDULE = new ThingTypeUID(BINDING_ID, "flexschedule");

    public static final Set<ThingTypeUID> SUPPORTED_BRIDGE_THING_TYPES_UIDS = Stream.of(THING_TYPE_CLOUD)
            .collect(Collectors.toSet());
    public static final Set<ThingTypeUID> SUPPORTED_DEVICE_THING_TYPES_UIDS = Stream
            .of(THING_TYPE_DEVICE, THING_TYPE_ZONE, THING_TYPE_SCHEDULE, THING_TYPE_FLEXSCHEDULE)
            .collect(Collectors.toSet());
    public static final Set<ThingTypeUID> SUPPORTED_ZONE_THING_TYPES_UIDS = Stream.of(THING_TYPE_ZONE)
            .collect(Collectors.toSet());
    public static final Set<ThingTypeUID> SUPPORTED_SCHEDULE_THING_TYPES_UIDS = Stream
            .of(THING_TYPE_SCHEDULE, THING_TYPE_FLEXSCHEDULE).collect(Collectors.toSet());
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Stream
            .concat(SUPPORTED_BRIDGE_THING_TYPES_UIDS.stream(), SUPPORTED_DEVICE_THING_TYPES_UIDS.stream())
            .collect(Collectors.toSet());

    // Config opntions (e.g. rachio.cfg)
    public static final String PARAM_APIKEY = "apikey";
    public static final String PARAM_POLLING_INTERVAL = "pollingInterval";
    public static final String PARAM_DEF_RUNTIME = "defaultRuntime";
    public static final String PARAM_CALLBACK_URL = "callbackUrl";
    public static final String PARAM_CALLBACK_USERNAME = "callbackUsername";
    public static final String PARAM_CALLBACK_PASSWORD = "callbackPassword";
    public static final String PARAM_CLEAR_CALLBACK = "clearAllCallbacks";
    public static final String PARAM_EVENT_HISTORY_LOOKBACK_HOURS = "eventHistoryLookbackHours";
    public static final String PARAM_FORECAST_UNITS = "forecastUnits";

    // List of non-standard Properties
    public static final String PROPERTY_IP_ADDRESS = "ipAddress";
    public static final String PROPERTY_IP_MASK = "ipMask";
    public static final String PROPERTY_IP_GW = "ipGateway";
    public static final String PROPERTY_IP_DNS1 = "ipDNS1";
    public static final String PROPERTY_IP_DNS2 = "ipDNS2";
    public static final String PROPERTY_WIFI_RSSI = "wifiSignal";
    public static final String PROPERTY_APIKEY = "apikey";
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_MODEL = "model";
    public static final String PROPERTY_EXT_ID = "externalId";
    public static final String PROPERTY_DEV_ID = "deviceId";
    public static final String PROPERTY_DEV_LAT = "latitude";
    public static final String PROPERTY_DEV_LONG = "longitude";
    public static final String PROPERTY_ZONE_ID = "zoneId";
    public static final String PROPERTY_SCHEDULE_RULE_ID = "scheduleRuleId";
    public static final String PROPERTY_FLEX_SCHEDULE_RULE_ID = "flexScheduleRuleId";
    public static final String PROPERTY_PERSON_ID = "personId";
    public static final String PROPERTY_PERSON_USER = "accounUserName";
    public static final String PROPERTY_PERSON_NAME = "accountFullName";
    public static final String PROPERTY_PERSON_EMAIL = "accountEMail";

    // List of all Device Channel ids
    public static final String CHANNEL_DEVICE_NAME = "name";
    public static final String CHANNEL_DEVICE_ACTIVE = "active";
    public static final String CHANNEL_DEVICE_ONLINE = "online";
    public static final String CHANNEL_DEVICE_PAUSED = "paused";
    public static final String CHANNEL_DEVICE_PAUSE_TIME = "pauseTime";
    public static final String CHANNEL_DEVICE_SLEEP_MODE = "sleepMode";
    public static final String CHANNEL_DEVICE_RUN = "run";
    public static final String CHANNEL_DEVICE_RUN_ZONES = "runZones";
    public static final String CHANNEL_DEVICE_RUN_TIME = "runTime";
    public static final String CHANNEL_DEVICE_STOP = "stop";
    public static final String CHANNEL_DEVICE_RAIN_DELAY = "rainDelay";
    public static final String CHANNEL_DEVICE_RAIN_STRIPPED = "rainSensorTripped";

    public static final String CHANNEL_CURRENT_SCHEDULE_ID = "currentScheduleId";
    public static final String CHANNEL_CURRENT_SCHEDULE_NAME = "currentScheduleName";
    public static final String CHANNEL_CURRENT_SCHEDULE_TYPE = "currentScheduleType";
    public static final String CHANNEL_CURRENT_SCHEDULE_START = "currentScheduleStartTime";
    public static final String CHANNEL_CURRENT_SCHEDULE_END = "currentScheduleEndTime";
    public static final String CHANNEL_CURRENT_SCHEDULE_DURATION = "currentScheduleDuration";
    public static final String CHANNEL_CURRENT_SCHEDULE_RUNNING = "currentScheduleRunning";
    public static final String CHANNEL_LAST_API_EVENT_TYPE = "lastApiEventType";
    public static final String CHANNEL_LAST_API_EVENT_TIME = "lastApiEventTime";
    public static final String CHANNEL_LAST_API_EVENT_SUMMARY = "lastApiEventSummary";
    public static final String CHANNEL_FORECAST_SUMMARY = "forecastSummary";
    public static final String CHANNEL_FORECAST_TODAY_HIGH = "forecastTodayHigh";
    public static final String CHANNEL_FORECAST_TODAY_LOW = "forecastTodayLow";
    public static final String CHANNEL_FORECAST_PRECIPITATION = "forecastPrecipitation";
    public static final String CHANNEL_FORECAST_PRECIPITATION_PROBABILITY = "forecastPrecipitationProbability";
    public static final String CHANNEL_FORECAST_WIND = "forecastWind";
    public static final String CHANNEL_FORECAST_UPDATED = "forecastUpdated";
    public static final String CHANNEL_LAST_SKIP_TYPE = "lastSkipType";
    public static final String CHANNEL_LAST_SKIP_SCHEDULE_ID = "lastSkipScheduleId";
    public static final String CHANNEL_LAST_SKIP_START = "lastSkipStartTime";
    public static final String CHANNEL_LAST_SKIP_REASON = "lastSkipReason";

    public static final String CHANNEL_SCHED_NAME = "scheduleName";
    public static final String CHANNEL_SCHED_INFO = "scheduleInfo";
    public static final String CHANNEL_SCHED_START = "scheduleStart";
    public static final String CHANNEL_SCHED_END = "scheduleEnd";

    // List of all Zone Channel ids
    public static final String CHANNEL_ZONE_NAME = "name";
    public static final String CHANNEL_ZONE_NUMBER = "number";
    public static final String CHANNEL_ZONE_ENABLED = "enabled";
    public static final String CHANNEL_ZONE_RUN = "run";
    public static final String CHANNEL_ZONE_RUN_TIME = "runTime";
    public static final String CHANNEL_ZONE_RUN_TOTAL = "runTotal";
    public static final String CHANNEL_ZONE_IMAGEURL = "imageUrl";
    public static final String CHANNEL_ZONE_MOISTURE_LEVEL = "moistureLevel";
    public static final String CHANNEL_ZONE_MOISTURE_PERCENT = "moisturePercent";

    public static final String CHANNEL_SCHEDULE_NAME = "name";
    public static final String CHANNEL_SCHEDULE_ENABLED = "enabled";
    public static final String CHANNEL_SCHEDULE_TYPE = "type";
    public static final String CHANNEL_SCHEDULE_START_TIME = "startTime";
    public static final String CHANNEL_SCHEDULE_LAST_RUN = "lastRun";
    public static final String CHANNEL_SCHEDULE_NEXT_RUN = "nextRun";
    public static final String CHANNEL_SCHEDULE_ZONES = "zones";
    public static final String CHANNEL_SCHEDULE_SEASONAL_ADJUSTMENT = "seasonalAdjustment";
    public static final String CHANNEL_SCHEDULE_START = "start";
    public static final String CHANNEL_SCHEDULE_SKIP = "skip";
    public static final String CHANNEL_SCHEDULE_SKIP_FORWARD_ZONE_RUN = "skipForwardZoneRun";

    public static final String CHANNEL_LAST_UPDATE = "lastUpdate";
    public static final String CHANNEL_LAST_EVENT = "lastEvent";
    public static final String CHANNEL_LAST_EVENTTS = "lastEventTime";

    // Default for config options / thing settings
    public static int DEFAULT_POLLING_INTERVAL_SEC = 120;
    public static int DEFAULT_ZONE_RUNTIME_SEC = 300;
    public static int DEFAULT_EVENT_HISTORY_LOOKBACK_HOURS = 24;
    public static int MAX_EVENT_HISTORY_LOOKBACK_HOURS = 168;
    public static final String DEFAULT_FORECAST_UNITS = "METRIC";
    public static final int HTTP_TIMOUT_MS = 15000;
    public static int BINDING_DISCOVERY_TIMEOUT_SEC = 60;

    // --------------- Rachio Cloud API
    public static final String APIURL_BASE = "https://api.rach.io/1/public/";
    public static final String APIURL_CLOUD_REST_BASE = "https://cloud-rest.rach.io";

    public static final String APIURL_GET_PERSON = "person/info"; // obtain personId
    public static final String APIURL_GET_PERSONID = "person"; // obtain personId
    public static final String APIURL_GET_DEVICE = "device"; // get device details, needs /<device id>
    public static final String APIURL_GET_DEVICE_CURRENT_SCHEDULE = "current_schedule";
    public static final String APIURL_GET_DEVICE_EVENT = "event";
    public static final String APIURL_GET_DEVICE_FORECAST = "forecast";

    public static final String APIURL_DEV_PUT_ON = "device/on"; // Enable device / all functions
    public static final String APIURL_DEV_PUT_OFF = "device/off"; // Disable device / all functions
    public static final String APIURL_DEV_PUT_STOP = "device/stop_water"; // stop watering (all zones)
    public static final String APIURL_DEV_PUT_RAIN_DELAY = "device/rain_delay"; // Rain delay device
    public static final String APIURL_DEV_PUT_PAUSE_ZONE_RUN = "device/pause_zone_run"; // Pause active zone run
    public static final String APIURL_DEV_PUT_RESUME_ZONE_RUN = "device/resume_zone_run"; // Resume active zone run
    public static final String APIURL_DEV_POST_WEBHOOK = "notification/webhook"; // deprecated
    public static final String APIURL_DEV_QUERY_WEBHOOK = "notification"; // deprecated
    public static final String APIURL_DEV_DELETE_WEBHOOK = "notification/webhook"; // deprecated

    // New WebhookService endpoints (cloud-rest.rach.io)
    public static final String WEBHOOK_QUERY_CONTROLLER_ID = "resource_id.irrigation_controller_id";
    public static final String WEBHOOK_CREATE = "/webhook/createWebhook";
    public static final String WEBHOOK_GET = "/webhook/getWebhook/";
    public static final String WEBHOOK_LIST = "/webhook/listWebhooks";
    public static final String WEBHOOK_UPDATE = "/webhook/updateWebhook";
    public static final String WEBHOOK_DELETE = "/webhook/deleteWebhook/";
    public static final String WEBHOOK_DELETE_ALL = "/webhook/deleteAllWebhooks";
    public static final String WEBHOOK_LIST_EVENT_TYPES = "/webhook/listWebhookEventTypes";

    public static final String APIURL_ZONE_PUT_START = "zone/start"; // start a zone
    public static final String APIURL_ZONE_PUT_MULTIPLE_START = "zone/start_multiple"; // start multiple zones
    public static final String APIURL_ZONE_PUT_ENABLE = "zone/enable"; // enable a zone
    public static final String APIURL_ZONE_PUT_DISABLE = "zone/disable"; // disable a zone
    public static final String APIURL_ZONE_PUT_MOISTURE_LEVEL = "zone/setMoistureLevel";
    public static final String APIURL_ZONE_PUT_MOISTURE_PERCENT = "zone/setMoisturePercent";

    public static final String APIURL_GET_SCHEDULE_RULE = "schedulerule";
    public static final String APIURL_SCHEDULE_RULE_PUT_START = "schedulerule/start";
    public static final String APIURL_SCHEDULE_RULE_PUT_SKIP = "schedulerule/skip";
    public static final String APIURL_SCHEDULE_RULE_PUT_SEASONAL_ADJUSTMENT = "schedulerule/seasonal_adjustment";
    public static final String APIURL_SCHEDULE_RULE_PUT_SKIP_FORWARD_ZONE_RUN = "schedulerule/skip_forward_zone_run";
    public static final String APIURL_GET_FLEX_SCHEDULE_RULE = "flexschedulerule";

    public static final String DEFAULT_IP_FILTER_LIST = "192.168.0.0/16;10.0.0.0/8;172.16.0.0/12";

    // WebHook event types (old numeric IDs - deprecated)
    public static final String WHE_DEVICE_STATUS = "5"; // "Device status event has occurred"
    public static final String WHE_RAIN_DELAY = "6"; // "A rain delay event has occurred"
    public static final String WEATHER_INTELLIGENCE = "7"; // A weather intelligence event has has occurred
    public static final String WHE_WATER_BUDGET = "8"; // A water budget event has occurred
    public static final String WHE_SCHEDULE_STATUS = "9";
    public static final String WHE_ZONE_STATUS = "10";
    public static final String WHE_RAIN_SENSOR_DETECTION = "11"; // physical rain sensor event has coccurred
    public static final String WHE_ZONE_DELTA = "12"; // A physical rain sensor event has occurred
    public static final String WHE_DELTA = "14"; // "An entity has been inserted, updated, or deleted"

    // New Webhook event types (string-based)
    public static final String EVENT_DEVICE_ZONE_RUN_STARTED = "DEVICE_ZONE_RUN_STARTED_EVENT";
    public static final String EVENT_DEVICE_ZONE_RUN_STOPPED = "DEVICE_ZONE_RUN_STOPPED_EVENT";
    public static final String EVENT_DEVICE_ZONE_RUN_COMPLETED = "DEVICE_ZONE_RUN_COMPLETED_EVENT";
    public static final String EVENT_DEVICE_ZONE_RUN_PAUSED = "DEVICE_ZONE_RUN_PAUSED_EVENT";
    public static final String EVENT_SCHEDULE_STARTED = "SCHEDULE_STARTED_EVENT";
    public static final String EVENT_SCHEDULE_STOPPED = "SCHEDULE_STOPPED_EVENT";
    public static final String EVENT_SCHEDULE_COMPLETED = "SCHEDULE_COMPLETED_EVENT";
    public static final String EVENT_RAIN_SKIP = "RAIN_SKIP_NOTIFICATION_EVENT";
    public static final String EVENT_CLIMATE_SKIP = "CLIMATE_SKIP_NOTIFICATION_EVENT";
    public static final String EVENT_FREEZE_SKIP = "FREEZE_SKIP_NOTIFICATION_EVENT";
    public static final String EVENT_WIND_SKIP = "WIND_SKIP_NOTIFICATION_EVENT";
    public static final String EVENT_NO_SKIP = "NO_SKIP_NOTIFICATION_EVENT";
    public static final String EVENT_RAIN_SENSOR_DETECTION_ON = "RAIN_SENSOR_DETECTION_ON_EVENT";
    public static final String EVENT_RAIN_SENSOR_DETECTION_OFF = "RAIN_SENSOR_DETECTION_OFF_EVENT";
    public static final String EVENT_RAIN_DELAY_ON = "RAIN_DELAY_ON_EVENT";
    public static final String EVENT_RAIN_DELAY_OFF = "RAIN_DELAY_OFF_EVENT";

    public static final String SERVLET_WEBHOOK_PATH = "/rachio/webhook";
    public static final String SERVLET_WEBHOOK_APPLICATION_JSON = "application/json";
    public static final String SERVLET_WEBHOOK_CHARSET = "utf-8";
    public static final String SERVLET_WEBHOOK_USER_AGENT = "Mozilla/5.0";

    public static final String SERVLET_IMAGE_PATH = "/rachio/images";
    public static final String SERVLET_IMAGE_MIME_TYPE = "image/png";
    public static final String SERVLET_IMAGE_URL_BASE = "https://prod-media-photo.rach.io/";

    public static final String RACHIO_JSON_RATE_LIMIT = "X-RateLimit-Limit";
    public static final String RACHIO_JSON_RATE_REMAINING = "X-RateLimit-Remaining";
    public static final String RACHIO_JSON_RATE_RESET = "X-RateLimit-Reset";
    public static final int RACHIO_RATE_LIMIT_WARNING = 200; // slow down polling
    public static final int RACHIO_RATE_LIMIT_CRITICAL = 100; // stop polling
    public static final int RACHIO_RATE_LIMIT_BLOCK = 20; // block api access

    public static final String AWS_IPADDR_DOWNLOAD_URL = "https://ip-ranges.amazonaws.com/ip-ranges.json";
    public static final String AWS_IPADDR_REGION_FILTER = "us-";
}
