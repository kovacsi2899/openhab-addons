# Rachio Sprinkler Binding

This binding integrates your Rachio sprinkler system into openHAB and allows to retrieve status information and control some functions like running zones, stop watering etc. 

The binding uses the Rachio Cloud API, so you need an account. 
You need to get an API key before the binding can discover your devices.
Go to [Rachio Web App](https://rachio.com->login), click on Account Settings in the left navigation.
At the bottom you'll find a link "Get API key".

To receive events from the Rachio cloud service e.g. start/stop zones & skip watering, configure a public HTTPS callback URL that can reach your openHAB instance.

The device setup is read from the Rachio online service, when a Rachio Cloud Connector thing is configured, and therefore it shares the same items as the Smartphone and Web Apps, so there is no special setup required.
In fact all Apps (including this binding) control the same device.
The binding implements monitoring and control functions, but no configuration etc. 
To change configuration you could use the Rachio smartphone app or website.

Once the binding is able to connect to the Cloud API it will start the auto-discovery of all controller and zones under this account. 
As a result the following things are created

- 1 cloud per account (binding supports multiple accounts(
- 1 device for very controller (binding supports multiple controllers)
- n zones for each zone on any controller

Example: 2 controllers with 8 zones each under the same account creates 19 things (1xbridge, 2xdevice, 16xzone). 

## Supported Things

The Cloud API Connector is represented by a Bridge Thing. 
All devices are connected to this thing, all zones to the corresponding device. 

|Thing |Description                                                                                                            |
|:-----|:----------------------------------------------------------------------------------------------------------------------|
|cloud |Each Rachio account is represented by a `cloud` thing. The binding supports multiple accounts at the same time.          |
|device|Each sprinkler controller is represented by a `device` thing, which links to the cloud thing.                             |
|zone  |Each zone for each controller creates a `zone` thing, which links to the device thing (and  directly to the bridge thing)|
|schedule|Each fixed schedule rule can be represented by a `schedule` thing. Discovery creates these when schedule IDs are present in the controller response.|
|flexschedule|Each flex schedule rule can be represented by a read-only `flexschedule` thing when flex schedule IDs are present in the controller response.|

###  Configuration

**Option A: Using openHAB UI**

- Go to Inbox and press the + button.
- Click Add Manually at the end of the list, this will open a list of addable things.
- Select the Rachio Binding
- Select Rachio Cloud Connector thing
- Enter at least the api key, other settings are optional

To receive events from the Rachio Cloud service set the callbackUrl to a public HTTPS URL that forwards to `/rachio/webhook`, for example `https://host.example.org/rachio/webhook`.
- save

Now the binding is able to connect to the cloud and start discovery devices and zones.

**Option B: Using .things file**

Create conf/things/rachio.things and fill in the parameters:

```
Bridge rachio:cloud:1 [
    apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxx",
    pollingInterval=180,
    defaultRuntime=120,
    eventHistoryLookbackHours=24,
    forecastUnits="METRIC",
    callbackUrl="https://home.myopenhab.org/rachio/webhook",
    callbackUsername="user@example.com",
    callbackPassword="raw-password-with-special-characters",
    clearAllCallbacks=false
]
{
}
```

|Parameter        |Description                                                                                                                 |
|:----------------|:---------------------------------------------------------------------------------------------------------------------------|
|apikey           |This is a token required to access the Rachio Cloud account. See Discovery on information how to get that code.|
|pollingInterval  |Specifies the delay between two status polls. Usually something like 10 minutes should be enough to have a regular status update when the interfaces is configured. If you don't want/can use events a smaller delay might be interesting to get quicker responses on running zones etc.|
|                 |Important: Please make sure to use an interval > 90sec. Rachio allows 3,500 API requests per day, and the limit resets at midnight UTC. This means if you are accessing the API too frequently your account can get blocked until the next reset.|
|defaultRuntime   |You could run zones in 2 different ways:|
|                 |1. Just by pushing the button in your UI. The zone will start watering for &lt;defaultRuntime&gt; seconds.| 
|                 |2. Setting the zone's channel runTime to &lt;n&gt; seconds and then starting the zone. This will start the zone for &lt;n&gt; seconds. Usually this variant required a OH rule setting the runTime and then sending a ON to the run channel.|
|eventHistoryLookbackHours|Hours of recent controller event history to retrieve. Set to 0 to disable event history polling.|
|forecastUnits    |Units for the Rachio forecast endpoint: `METRIC` or `US`.|
|callbackUrl      |Public HTTPS URL that forwards to `/rachio/webhook`. In the recommended Basic Auth setup, do not include credentials in this URL. For openHAB Cloud / myopenHAB.org, use `https://home.myopenhab.org/rachio/webhook`. For direct reverse proxies without Basic Auth, use `https://yourhost.example.org/rachio/webhook`.|
|callbackUsername |Optional HTTP Basic Auth username for the webhook endpoint, for example your myopenHAB.org email address. Enter the raw value; the binding percent-encodes it before registering the webhook with Rachio.|
|callbackPassword |Optional HTTP Basic Auth password for the webhook endpoint. Enter the raw value, including special characters such as `@`, `?`, `#`, or `/`; the binding percent-encodes it before registering the webhook with Rachio.|
|clearAllCallbacks|The binding dynamically registers the callback. It also supports multiple applications registered to receive events, e.g. a 2nd OH device with the binding providing the same functionality. If for any reason your device setup changes (e.g. new ip address) you need to clear the registered URL once to avoid the "old URL" still receiving events. This also allows to move for a test setup to the regular setup.|

The bridge thing doesn't have any channels.

### Adding Controllers and Zones

Recommended: use Inbox discovery.

1. Add and configure the Rachio Cloud bridge.
2. Run Scan / Inbox discovery.
3. Accept the discovered controller and zone Things.

Discovery creates stable openHAB Thing UIDs and provides the real Rachio API identifiers automatically.

Manual creation is also supported.
The local openHAB Thing ID may be chosen freely, but the controller Thing must be configured with the real Rachio controller UUID in `deviceId`.
The controller UUID is the Rachio API device ID, for example `811aea42-2bf5-4761-9f97-900108d6f04e`.
It is not the controller MAC address, such as `009D6BC04DAC`.

Manual zone Things must be configured with the real Rachio zone UUID in `zoneId`.
Using discovery first is the easiest way to obtain these identifiers; they are also visible in discovery properties and debug logs.

### openHAB Cloud / myopenHAB.org Configuration

For users of [openHAB Cloud](https://www.openhab.org/docs/configuration/openhab-cloud.html) or [myopenHAB.org](https://www.myopenhab.org/), configure the public callback URL and Basic Auth credentials separately:

1. **Determine your myopenHAB.org credentials:**
   - Username: Your myopenHAB.org email address
   - Password: Your myopenHAB.org password

2. **Set the callback configuration:**
   ```
   callbackUrl="https://home.myopenhab.org/rachio/webhook"
   callbackUsername="user@example.com"
   callbackPassword="raw-password-with-special-characters"
   ```

3. **Validation:**
   - Test that `https://home.myopenhab.org/rachio/webhook` is reachable with Basic Auth
   - Check openHAB logs for successful webhook registration
   - Verify that Rachio events appear in openHAB without delays

The username and password are entered as raw values. The binding percent-encodes them internally before registering the webhook URL with Rachio, so you do not need to manually encode `@`, `?`, `#`, `/`, or similar URI-reserved characters.

Legacy `callbackUrl` values that already contain validly encoded credentials, such as `https://user%40example.com:pass%3Fword@home.myopenhab.org/rachio/webhook`, remain supported for backward compatibility. The separate `callbackUsername` and `callbackPassword` fields are recommended for new configurations and take precedence if both models are configured.

### Device Thing - represents one Rachio Controller

|Channel      |Description                                                                                                            |
|:------------|:----------------------------------------------------------------------------------------------------------------------|
|name         |Device Name - name of the controller                                                                                   |
|active       |ON: Device is active, OFF: Device is deactivated                                                                       |
|online       |ON: Controller is connected to the cloud. OFF: Controller is offline, check Internet connection.                       |
|paused       |ON: Pause the currently active zone run for `pauseTime` seconds; OFF: Resume the active zone run                       |
|pauseTime    |Number of seconds to pause the active zone run when `paused` receives ON. Valid range is 0 to 3600 seconds.            |
|sleepMode    |ON: Rachio device sleep mode is active, OFF: Rachio device sleep mode is not active (read-only webhook state).         |
|stop         |ON: Stop watering for all zones (command), OFF: normal operation                                                       |
|run          |ON: Start watering selected/all zones (defined in runZones)                                                            |
|runZones     |Zones to run at a time - list, e.g: "1,3" = run zone 1 and 3; "" means: run all zones                                  |
|runTime      |Controller-level run time, in seconds, for the multi-zone `run` command                                                |
|rainDelay    |> 0: Rain delay scheduled for x sec; =0: Currently not in rain delay mode                                              |
|rainSensorTripped|ON: Rain sensor has tripped (rain detected)                                                                        |
|lastUpdate   |Timestamp of last status update                                                                                        |
|lastEvent    |Last event received from the cloud (requires configuration of event callback)                                          |
|lastEventTime|Timestamp last event has been received (only if event callback is active)                                              |
|scheduleName |Current/last executed schedule: name                                                                                   |
|scheduleInfo |Description of current/last executed schedule                                                                          |
|scheduleStart|Schedule start time                                                                                                    |
|scheduleStop |Schedule end time                                                                                                      |
|currentScheduleRunning|ON when Rachio reports a currently running schedule from `/device/{id}/current_schedule`.                     |
|currentScheduleId|Rachio schedule ID for the currently running schedule.                                                              |
|currentScheduleName|Name of the currently running schedule.                                                                            |
|currentScheduleType|Type of the currently running schedule.                                                                            |
|currentScheduleStartTime|Start time of the currently running schedule.                                                                 |
|currentScheduleEndTime|End time of the currently running schedule.                                                                     |
|currentScheduleDuration|Duration of the currently running schedule in seconds.                                                         |
|lastApiEventType|Type of the latest event retrieved from recent device event history.                                                |
|lastApiEventTime|Time of the latest event retrieved from recent device event history.                                                |
|lastApiEventSummary|Summary of the latest event retrieved from recent device event history.                                          |
|forecastSummary|Forecast summary from Rachio.                                                                                        |
|forecastTodayHigh|Today's forecast high temperature in the configured forecast units.                                                |
|forecastTodayLow|Today's forecast low temperature in the configured forecast units.                                                  |
|forecastPrecipitation|Today's forecast precipitation amount.                                                                         |
|forecastPrecipitationProbability|Today's forecast precipitation probability.                                                            |
|forecastWind|Today's forecast wind speed in the configured forecast units.                                                            |
|forecastUpdated|Timestamp of the forecast data when provided by Rachio.                                                               |
|lastSkipType|Most recent weather intelligence skip event type.                                                                        |
|lastSkipScheduleId|Schedule ID associated with the most recent weather intelligence skip event.                                       |
|lastSkipStartTime|Start time associated with the most recent weather intelligence skip event.                                        |
|lastSkipReason|Summary or reason from the most recent weather intelligence skip event.                                                |

Controller identity is based on the Rachio API controller UUID configured as `deviceId`.
Discovery fills this automatically.
For manually created controller Things, set `deviceId` to the Rachio API device UUID.
The MAC address may still appear in discovered Thing UIDs for compatibility, but it is not the API controller ID.

When starting zones from the controller Thing with `runZones` and `run`, the controller `runTime` channel controls the duration for every selected zone.
If controller `runTime` is greater than 0, that value is used for all selected zones.
If controller `runTime` is 0, the bridge `defaultRuntime` is used.
Zone-specific `runTime` values only apply when starting an individual zone from that zone Thing.

Current schedule, forecast, and event history channels use a last-known-value policy.
If one of these extra read endpoints fails during a refresh, the binding logs the failure and keeps the previously published values.
A successful current schedule response that reports no running schedule still clears the current schedule channels normally.

### Zone Thing - represents one zone of a Controller

|Channel      |Description                                                                                                            |
|:------------|:----------------------------------------------------------------------------------------------------------------------|
|number       |Zone number as assigned by the controller (zone 1..16)                                                                 |
|name         |Name of the zone as configured in the App.                                                                             |
|enabled      |ON: zone is enabled (ready to run), OFF: zone is disabled. Sending ON/OFF enables or disables the zone.                |
|run          |ON: The zone starts watering. If runTime is = 0 the defaultRuntime will be used. OFF: Zone stops watering.             |
|runTime      |Number of seconds to run the zone when run receives ON command                                                         |
|runTotal     |Total number of seconds the zone was watering (as returned by the cloud service).                                      |
|imageUrl     |URL to the zone picture as configured in the App. Rachio supplies default pictures if no image was created.            |
|moistureLevel|Command channel for `zone/setMoistureLevel`. Send the moisture level in millimeters.                                  |
|moisturePercent|Command channel for `zone/setMoisturePercent`. Send a number from 0 to 1.                                           |
|lastUpdate   |Timestamp of last status update                                                                                        |
|lastEvent    |Last event received from the cloud (requires configuration of event callback)                                          |
|lastEventTime|Timestamp last event has been received (only if event callback is active)                                              |

Zone identity is based on the Rachio API zone UUID configured as `zoneId`.
Discovery fills this automatically.
For manually created zone Things, set `zoneId` to the Rachio API zone UUID.

### Schedule Things

Fixed schedule rules are represented by `schedule` Things.
Discovery creates schedule Things when the Rachio controller payload includes schedule rule IDs.
Manual schedule creation requires `scheduleRuleId`, the real Rachio schedule rule UUID.
If the controller payload does not include schedule rule IDs, add schedule Things manually.

|Channel|Description|
|:------|:----------|
|name|Schedule rule name.|
|enabled|ON if the schedule rule is enabled.|
|type|Schedule rule type.|
|startTime|Schedule start time when provided by Rachio.|
|lastRun|Last run time when provided by Rachio.|
|nextRun|Next run time when provided by Rachio.|
|zones|Comma-separated Rachio zone IDs associated with the schedule.|
|seasonalAdjustment|Seasonal adjustment value. Sending a number updates the schedule rule adjustment.|
|start|Send ON to start the schedule rule.|
|skip|Send ON to skip the schedule rule.|
|skipForwardZoneRun|Send ON to skip the currently running zone in the schedule context.|
|lastUpdate|Timestamp of last schedule state update.|

Flex schedules are represented by read-only `flexschedule` Things.
Manual flex schedule creation requires `flexScheduleRuleId`.
The flex schedule channels mirror the read-only schedule metadata channels.
Discovery creates flex schedule Things when the Rachio controller payload includes flex schedule rule IDs.

### Webhook Events

The binding registers for the current Smart Irrigation Controller webhook event types exposed by Rachio, including schedule started/stopped/completed, zone run started/stopped/completed/paused, rain/freeze/wind/climate skip notifications, and no-skip notifications.
Webhook registration is resource-aware internally: the binding now models each desired webhook target by Rachio resource type, resource ID, and event type set.
Existing Smart Irrigation Controller behavior is unchanged, while the shared webhook infrastructure is prepared for future Smart Hose Timer and Smart Lighting resources.
Schedule events update controller schedule channels and matching `schedule` Things when present.
Zone run events update the corresponding zone Thing when the event carries enough zone identity information.
Weather skip notifications update the controller `lastSkip*` channels and the normal `lastEvent` channels.
Smart Hose Timer and Smart Lighting events are not exposed as user-facing Things or channels in this phase.
Unsupported resource-family events are safely ignored with DEBUG logging.

### Property/Home API

The binding includes internal support for Rachio's modern Property Service on `https://cloud-rest.rach.io`.
This is infrastructure for future multi-product support and does not currently create user-facing Property/Home Things.
The implemented API layer can list properties for a user, retrieve a property by ID, and look up a property by documented entity resource identifiers.
Current internal lookup helpers cover the documented location, base station, and lighting area entity IDs.
Future Smart Hose Timer and Smart Lighting support can build on this without changing existing controller, zone, schedule, or flex schedule Things.

# Full example

## Thing Definition

```
Bridge rachio:cloud:1 @ "Sprinkler" [ apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",  pollingInterval=60, defaultRuntime=120  ]
{
    // Controller
    Thing device XXXXXXXXXXXX "Rachio-XXXXXX" @ "Sprinkler" [
        deviceId="811aea42-2bf5-4761-9f97-900108d6f04e"
    ]
    
    // Zones
    Thing zone XXXXXXXXXXXX-1 "Rachio zone 1" @ "Sprinkler" [
        zoneId="a4f319e9-f88e-476f-b341-0ea571a202a0"
    ]
    Thing zone XXXXXXXXXXXX-2 "Rachio zone 2" @ "Sprinkler" [
        zoneId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]

    Thing schedule morning "Morning schedule" @ "Sprinkler" [
        scheduleRuleId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]

    Thing flexschedule flex "Flex schedule" @ "Sprinkler" [
        flexScheduleRuleId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]
}
```

### Items Definition

```
    // Sprinkler Controller
    String   RachioC04DAC_Name          "Name"               {channel="rachio:device:1:XXXXXXXXXXXX:name"}
    Switch   RachioC04DAC_Active        "Active"             {channel="rachio:device:1:XXXXXXXXXXXX:active"}
    Switch   RachioC04DAC_Online        "Online"             {channel="rachio:device:1:XXXXXXXXXXXX:online"}
    Switch   RachioC04DAC_Paused        "Paused"             {channel="rachio:device:1:XXXXXXXXXXXX:paused"}
    Number   RachioC04DAC_PauseTime     "Pause Time"         {channel="rachio:device:1:XXXXXXXXXXXX:pauseTime"}
    Switch   RachioC04DAC_SleepMode     "Sleep Mode"         {channel="rachio:device:1:XXXXXXXXXXXX:sleepMode"}
    Switch   RachioC04DAC_Stop          "Stop Watering"      {channel="rachio:device:1:XXXXXXXXXXXX:stop"}
    Switch   RachioC04DAC_Run           "Run Multiple Zones" {channel="rachio:device:1:XXXXXXXXXXXX:run"}
    String   RachioC04DAC_RunZones      "Run Zone List"      {channel="rachio:device:1:XXXXXXXXXXXX:runZones"}
    Number   RachioC04DAC_RunTime       "Run Time"           {channel="rachio:device:1:XXXXXXXXXXXX:runTime"}
    Number   RachioC04DAC_RainDelay     "Rain Delay"         {channel="rachio:device:1:XXXXXXXXXXXX:rainDelay"}
    Switch   RachioC04DAC_RainSensorTr  "Rain Sensor"        {channel="rachio:device:1:XXXXXXXXXXXX:rainSensorTripped"}
    String   RachioC04DAC_lastEvent     "Last Event"         {channel="rachio:device:1:XXXXXXXXXXXX:lastEvent"}
    DateTime RachioC04DAC_lastEventTime "Last Event Time"    {channel="rachio:device:1:XXXXXXXXXXXX:lastEventTime"}
    DateTime RachioC04DAC_lastUpdate    LastUpdate"          {channel="rachio:device:1:XXXXXXXXXXXX:lastUpdate"}

    // Zone1
    String   RachioZone1_Name           "Zone Name"       {channel="rachio:zone:1:XXXXXXXXXXXX-1:name"}
    Number   RachioZone1_Number         "Zone Number"     {channel="rachio:zone:1:XXXXXXXXXXXX-1:number"}  
    Switch   RachioZone1_Enabled        "Zone Enabled"    {channel="rachio:zone:1:XXXXXXXXXXXX-1:enabled"}
    Switch   RachioZone1_Run            "Run Zone"        {channel="rachio:zone:1:XXXXXXXXXXXX-1:run"}
    Number   RachioZone1_RunTime        "Zone Runtime"    {channel="rachio:zone:1:XXXXXXXXXXXX-1:runTime"}
    Number   RachioZone1_RunTotal       "Total Runtime"   {channel="rachio:zone:1:XXXXXXXXXXXX-1:runTotal"}
    String   RachioZone1_ImageUrl       "Zone Image URL"  {channel="rachio:zone:1:XXXXXXXXXXXX-1:imageUrl"}
    String   RachioZone1_lastEvent      "Last Event"      {channel="rachio:zone:1:XXXXXXXXXXXX-1:lastEvent"}
    DateTime RachioZone1_lastEventTime  "Last Event Time" {channel="rachio:zone:1:XXXXXXXXXXXX-1:lastEventTime"}
    DateTime RachioZone1_lastUpdate     "Last Update"     {channel="rachio:zone:1:XXXXXXXXXXXX-1:lastUpdate"}

    // Zone2
    String   RachioZone2_Name           "Zone Name"       {channel="rachio:zone:1:XXXXXXXXXXXX-2:name"}
    Number   RachioZone2_Number         "Zone Number"     {channel="rachio:zone:1:XXXXXXXXXXXX-2:number"}
    Switch   RachioZone2_Enabled        "Zone Enabled"    {channel="rachio:zone:1:XXXXXXXXXXXX-2:enabled"}
    Switch   RachioZone2_Run            "Run Zone"        {channel="rachio:zone:1:XXXXXXXXXXXX-2:run"} 
    Number   RachioZone2_RunTime        "Zone Runtime"    {channel="rachio:zone:1:XXXXXXXXXXXX-2:runTime"}
    Number   RachioZone2_RunTotal       "Total Runtime"   {channel="rachio:zone:1:XXXXXXXXXXXX-2:runTotal"}
    String   RachioZone2_ImageUrl       "Zone Image URL"  {channel="rachio:zone:1:XXXXXXXXXXXX-2:imageUrl"}
    String   RachioZone2_lastEvent      "Last Event"      {channel="rachio:zone:1:XXXXXXXXXXXX-2:lastEvent"}
    DateTime RachioZone2_lastEventTime  "Last Event Time" {channel="rachio:zone:1:XXXXXXXXXXXX-2:lastEventTime"}
    DateTime RachioZone2_lastUpdate     "Last Update"     {channel="rachio:zone:1:XXXXXXXXXXXX-2:lastUpdate"}
```

### Rule Example

```
var Timer rachio_timer5

rule "Start Rachio zone6_2"
    when
        Item ZWaveNode051ZRC90SceneMaster8ButtonRemote_SceneNumber changed
    then
        if (ZWaveNode051ZRC90SceneMaster8ButtonRemote_SceneNumber.state==8.3) {
            if (RachioZone6_Run.state==OFF) {
                // Set zone Quick run time to 1800 sec
                RachioZone6_RunTime.sendCommand(1800)
                RachioZone6_Run.sendCommand(ON)
                ZWaveNode051ZRC90SceneMaster8ButtonRemote_SceneNumber.sendCommand(0.0)
                rachio_timer5 = createTimer(now.plusSeconds(1800)) [|
                RachioZone6_RunTime.sendCommand(120)
                RachioZone6_Run.sendCommand(OFF)
                rachio_timer5 = null
                ]
            }
    else {
                if (RachioZone6_Run.state==ON) {
                    // Change back zone Quick run time to default 120 sec
                    RachioZone6_RunTime.sendCommand(120)
                    RachioZone6_Run.sendCommand(OFF)
                    ZWaveNode051ZRC90SceneMaster8ButtonRemote_SceneNumber.sendCommand(0.0)
                    }
         }  
        }
end
```

### Rule Example

Catch zone events:

```
rule "Zone started"
when
    Item RachioZone1_lastEvent changed
then
    if (RachioZone1_lastEvent == "ZONE_STARTED") {
        ...
    }
   
end
```

## API Migration - New Rachio WebhookService

### Overview

The Rachio binding uses Rachio's current WebhookService API for receiving events. The new API provides improved reliability, better event types, and enhanced security features.

Inbound WebhookService events are verified with the `x-signature` header before they are parsed or routed.
The binding recomputes the HMAC-SHA256 signature over the raw HTTP request body with the configured Rachio API token and rejects unsigned or invalid requests.

### API Versions

| Feature | Legacy API (NotificationService) | New API (WebhookService) |
|---------|----------------------------------|---------------------------|
| **Base URL** | `https://api.rach.io/1/public/` | `https://cloud-rest.rach.io/` |
| **Status** | Deprecated | Current (Recommended) |
| **Event Format** | Numeric IDs (5, 6, 7, 8...) | String types (DEVICE_ZONE_RUN_STARTED_EVENT...) |
| **Signature Validation** | Basic Auth in URL | HMAC-SHA256 in x-signature header |
| **Rate Limit** | Deprecated API limit | 3,500 requests/day, resetting at midnight UTC |

### Migration Guide

#### Step 1: Configure a Public Callback URL

Add a public HTTPS callback URL to your bridge configuration:

**Using .things file:**
```
Bridge rachio:cloud:1 [ apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxx", callbackUrl="https://host.example.org/rachio/webhook" ]
{
}
```

**Using openHAB UI:**
Navigate to the Rachio Cloud Connector thing settings and set the callback URL.

#### Step 2: Verify Webhook Registration

Check the openHAB logs to verify successful webhook registration:

```
DEBUG org.openhab.binding.rachio.internal.api.RachioApi - Register WebHook for target 'IRRIGATION_CONTROLLER:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'
DEBUG org.openhab.binding.rachio.internal.api.RachioApi - Webhook successfully registered with new WebhookService API
```

#### Step 3: Monitor Events

Events from the new API will be received and processed automatically. Both APIs support the same event channels on device and zone things.

### New Supported Event Types

The new API provides additional event types:

- `DEVICE_ZONE_RUN_STARTED_EVENT` - Zone watering started
- `DEVICE_ZONE_RUN_COMPLETED_EVENT` - Zone watering completed
- `DEVICE_ZONE_RUN_STOPPED_EVENT` - Zone watering stopped (new)
- `DEVICE_ZONE_RUN_PAUSED_EVENT` - Zone watering paused (new)
- `SCHEDULE_STARTED_EVENT` - Schedule started
- `SCHEDULE_COMPLETED_EVENT` - Schedule completed
- `SCHEDULE_STOPPED_EVENT` - Schedule stopped (new)
- `RAIN_SKIP_NOTIFICATION_EVENT` - Rain detected, watering skipped
- `CLIMATE_SKIP_NOTIFICATION_EVENT` - Climate skip applied
- `FREEZE_SKIP_NOTIFICATION_EVENT` - Freeze detected, watering skipped
- `WIND_SKIP_NOTIFICATION_EVENT` - High wind, watering skipped
- `NO_SKIP_NOTIFICATION_EVENT` - Normal watering (no skip)

### Troubleshooting

**Webhook registration fails:**
- Check that `callbackUrl` is a valid public HTTPS URL
- Verify the URL is accessible from Rachio's servers
- Check firewall and port forwarding rules

**Events not received:**
- Verify webhook registration in logs: `log:set DEBUG org.openhab.binding.rachio`
- Check that events are being generated (run a zone manually)
- Verify the callback URL hasn't changed
- Verify that the webhook request includes a valid `x-signature` header

**Rate limiting:**
- Rachio allows 3,500 API requests per day, resetting at midnight UTC
- Adjust `pollingInterval` if needed (recommended: > 90 seconds)

### Documentation

For detailed information about the new WebhookService API implementation, see:
- [API Migration Summary](API_MIGRATION_SUMMARY.md) - Comprehensive technical reference
- [WebhookService Guide](WEBHOOK_SERVICE_GUIDE.md) - Implementation details and examples
