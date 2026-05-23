# Rachio Sprinkler Binding

This binding integrates your Rachio sprinkler system into openHAB, retrieves status information, and controls common functions such as running zones and stopping watering.

The binding uses the Rachio Cloud API, so you need a Rachio account.
You need to get an API key before the binding can discover your devices.
Go to the [Rachio Web App](https://app.rach.io/), click Account Settings in the left navigation.
At the bottom you'll find a link "Get API key".

To receive events from the Rachio cloud service, such as start/stop zone and skip-watering events, configure a public HTTPS callback URL that can reach your openHAB instance.

The device setup is read from the Rachio online service when a Rachio Cloud Connector Thing is configured.
The binding implements monitoring and day-to-day control, but it is not intended to replace the Rachio mobile or web app for full device configuration.

Once the binding is able to connect to the Cloud API it automatically starts discovery for supported resources under this account.
As a result the following things can appear in the Inbox:

- 1 cloud per account (binding supports multiple accounts)
- 1 device for every controller (binding supports multiple controllers)
- n zones for each zone on any controller
- n schedules for fixed schedule rules returned by the Rachio controller API
- n flex schedules for flex schedule rules returned by the Rachio controller API
- 1 basestation for each Smart Hose Timer Wi-Fi hub
- n valves for each Smart Hose Timer BaseStation
- n valve programs for Smart Hose Timer schedules returned by the Rachio Program API

Example: 2 controllers with 8 zones each under the same account creates 19 Things (1 cloud bridge, 2 devices, 16 zones).

## Supported Things

The Cloud API Connector is represented by a Bridge Thing.
All devices are connected to this Thing, and all zones are connected to the corresponding device.

|Thing |Description                                                                                                            |
|:-----|:----------------------------------------------------------------------------------------------------------------------|
|cloud |Each Rachio account is represented by a `cloud` thing. The binding supports multiple accounts at the same time.          |
|device|Each sprinkler controller is represented by a `device` thing, which links to the cloud thing.                             |
|zone  |Each zone for each controller creates a `zone` thing, which links to the device thing and directly to the bridge thing.|
|schedule|Each fixed schedule rule can be represented by a `schedule` thing. Discovery creates these when schedule IDs are present in the controller response.|
|flexschedule|Each flex schedule rule can be represented by a read-only `flexschedule` thing when flex schedule IDs are present in the controller response.|
|basestation|Each Smart Hose Timer Wi-Fi hub can be represented by a read-only `basestation` thing.|
|valve|Each Smart Hose Timer valve can be represented by a `valve` thing for manual start/stop and default runtime control.|
|valveprogram|Each Smart Hose Timer Program can be represented by a `valveprogram` thing for schedule metadata, skip controls, and Program webhook state.|

## Rachio API Coverage

This binding maps the official Rachio API to openHAB Things, channels, and commands for monitoring and day-to-day control.
It is not intended to replace the Rachio mobile or web app for full device configuration.
Rachio API identifiers are real UUIDs from the API; a controller `deviceId` is not the controller MAC address.

### Supported

- Rachio account / Cloud Connector
- Irrigation controller discovery
- Controller status and basic control
- Zone discovery and zone start/stop
- Multiple-zone start
- Fixed schedule discovery and basic control
- Flex schedule discovery and read-only status
- Current schedule
- Forecast
- Recent controller events
- Rain delay
- Webhook registration and routing
- Webhook signature validation with the `x-signature` header
- Webhook duplicate event protection using Rachio `eventId`
- Smart Hose Timer base stations
- Smart Hose Timer valves
- Smart Hose Timer valve programs
- Smart Hose Timer planned run and program skip controls where represented by current channels
- QuantityType channel support for physical values

Schedule-rule, Smart Hose Timer Program, and webhook support is intentionally scoped to the openHAB Things and channels listed above.
The binding does not expose a full schedule/program create-update-delete editor; use the Rachio app for full device and schedule configuration.
For webhook event types, the binding queries Rachio's `listWebhookEventTypes` catalog and subscribes to supported irrigation, valve, and program events that match the implemented Thing types.

### Will be supported

- Smart Lighting Controller resources and `LIGHTING_CONTROLLER` webhook events.
- Any Rachio API feature that is not currently represented by an openHAB Thing, channel, or action and is intentionally outside this PR scope.
- Optional richer diagnostics for Smart Hose Timer state synchronization.

### Not planned / not applicable

- Full replacement of the Rachio mobile or web app for device setup, watering schedule editing, or account administration.
- Changing Rachio cloud account settings outside the official Rachio app and web app flows.

### Configuration

Account-level settings belong on the Rachio Cloud Connector Thing (`rachio:cloud`).
This includes the API token, polling/default runtime values, event and forecast preferences, Smart Hose Timer summary windows, and webhook callback settings.

Older binding-level configuration is still accepted as a deprecated fallback for compatibility.
The Cloud Connector Thing configuration is authoritative: when a value exists on the Thing, it wins over binding-level configuration.
The effective precedence is:

```
Cloud Connector Thing configuration > deprecated binding-level fallback > built-in default
```

### Quantity Channels and Compatibility

Physical numeric channels use typed openHAB Quantity item types where the Rachio unit is known, for example `Number:Time`, `Number:Length`, `Number:Area`, `Number:Temperature`, `Number:Speed`, and `Number:Dimensionless`.
Channel IDs were not renamed.
Existing rules that send plain numbers remain supported: runtime and delay commands still interpret plain numbers as seconds, zone moisture level interprets plain numbers as millimeters, and moisture percent interprets plain numbers as a 0..1 fraction.
Quantity commands are also accepted for the updated command channels.

Forecast channels follow the Cloud Connector `forecastUnits` setting.
With `METRIC`, forecast temperatures, precipitation, and wind are published as Celsius, millimeters, and meters per second.
With `US`, they are published as Fahrenheit, inches, and miles per hour.
Because those forecast units are selected at runtime, the temperature, precipitation, and wind channels publish explicit `QuantityType` units instead of declaring one fixed XML unit hint.
Precipitation probability follows the Rachio forecast API's 0..1 fraction semantics and uses the `one` unit hint.
Zone soil-water telemetry returned by the Rachio controller API is published as inches, while `moistureLevel` commands use millimeters as required by the Rachio moisture endpoint.

The Thing definitions include semantic equipment and channel tags for common status, control, measurement, timestamp, duration, water, rain, wind, temperature, and battery channels.

### Upgrading Existing Rachio Things and Items

Channel IDs were not renamed, so existing channel links should remain valid.
Managed Things created in the openHAB UI are migrated automatically by the Rachio thing-type update instructions when the updated binding is loaded.
Text-file `.things` definitions do not need channel migration because their channel structure comes from the current binding XML.

Thing-type update instructions update Things, not Items.
Existing Items that were created before this change are not converted automatically, so manually created `.items` entries and managed Items may need their item type changed from plain `Number` to the matching `Number:<dimension>` type.
Runtime and delay command compatibility is preserved: plain numeric commands are still accepted and interpreted as seconds for runtime and delay channels.
Zone `moistureLevel` plain numeric commands are still interpreted as millimeters.
`moisturePercent` and `forecastPrecipitationProbability` continue to use Rachio's 0..1 fraction semantics; percent display patterns can be used where you want the UI to show a percent.

|Thing type|Channels|New Item type|
|:---------|:-------|:------------|
|device|`pauseTime`, `runTime`, `rainDelay`, `currentScheduleDuration`|`Number:Time`|
|device|`forecastTodayHigh`, `forecastTodayLow`|`Number:Temperature`|
|device|`forecastPrecipitation`|`Number:Length`|
|device|`forecastPrecipitationProbability`|`Number:Dimensionless`|
|device|`forecastWind`|`Number:Speed`|
|zone|`runTime`, `runTotal`, `fixedRuntime`, `maxRuntime`, `runtimeNoMultiplier`|`Number:Time`|
|zone|`availableWater`, `depthOfWater`, `saturatedDepthOfWater`, `rootZoneDepth`, `moistureLevel`|`Number:Length`|
|zone|`yardAreaSquareFeet`|`Number:Area`|
|zone|`managementAllowedDepletion`, `efficiency`, `moisturePercent`|`Number:Dimensionless`|
|schedule, flexschedule|`seasonalAdjustment`|`Number:Dimensionless`|
|valve|`runTime`, `defaultRuntime`, `nextPlannedRunDuration`, `lastCompletedRunDuration`|`Number:Time`|
|valve|`batteryLevel`|`Number:Dimensionless`|
|valveprogram|`duration`, `intervalDays`|`Number:Time`|
|valveprogram|`seasonalAdjustment`|`Number:Dimensionless`|

Valve Program `intervalDays` is represented as `Number:Time` with day semantics.

Example updated Items:

```text
Number:Time Rachio_Zone1_RunTime "Zone 1 runtime [%d s]" { channel="rachio:zone:cloud:zone1:runTime" }
Number:Length Rachio_Zone1_MoistureLevel "Zone 1 moisture [%.1f mm]" { channel="rachio:zone:cloud:zone1:moistureLevel" }
Number:Dimensionless Rachio_ForecastPrecipProbability "Rain probability [%.0f %%]" { channel="rachio:device:cloud:controller1:forecastPrecipitationProbability" }
```

**Option A: Using openHAB UI**

- Go to Inbox and press the + button.
- Click Add Manually at the end of the list, this will open a list of addable things.
- Select the Rachio Binding
- Select Rachio Cloud Connector thing
- Enter at least the API key; other settings are optional.

To receive events from the Rachio Cloud service, set `callbackUrl` to a public HTTPS URL that forwards to `/rachio/webhook`, for example `https://host.example.org/rachio/webhook`.

Save the Thing configuration.

After the bridge connects successfully, supported Things are discovered automatically and appear in the Inbox.
Use Scan later if you want to refresh discovery results manually.

**Option B: Using .things file**

Create conf/things/rachio.things and fill in the parameters:

```
Bridge rachio:cloud:1 [
    apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxx",
    pollingInterval=180,
    defaultRuntime=120,
    eventHistoryLookbackHours=24,
    forecastUnits="METRIC",
    hoseSummaryLookbackDays=2,
    hoseSummaryLookaheadDays=7,
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
|apikey           |API token required to access the Rachio Cloud account. Create it in the Rachio Web App account settings.|
|pollingInterval  |Delay between two status polls. A value around 10 minutes is usually enough for regular status updates when webhooks are configured. If you cannot use events, a smaller delay can provide quicker updates for running zones.|
|                 |Important: Please make sure to use an interval > 90sec. Rachio allows 3,500 API requests per day, and the limit resets at midnight UTC. This means if you are accessing the API too frequently your account can get blocked until the next reset.|
|defaultRuntime   |You could run zones in 2 different ways:|
|                 |1. Just by pushing the button in your UI. The zone will start watering for &lt;defaultRuntime&gt; seconds.| 
|                 |2. Setting the zone's channel runTime to &lt;n&gt; seconds and then starting the zone. This starts the zone for &lt;n&gt; seconds. This variant usually requires an openHAB rule that sets runTime and then sends ON to the run channel.|
|eventHistoryLookbackHours|Hours of recent controller event history to retrieve. Set to 0 to disable event history polling.|
|forecastUnits    |Units for the Rachio forecast endpoint: `METRIC` or `US`.|
|hoseSummaryLookbackDays|Days of recent Smart Hose Timer Summary day-view data to retrieve for valve and program run state. Default is 2; set to 0 to skip historical runs.|
|hoseSummaryLookaheadDays|Days of upcoming Smart Hose Timer Summary day-view data to retrieve for planned runs and skip controls. Default is 7.|
|callbackUrl      |Public HTTPS URL that forwards to `/rachio/webhook`. In the recommended Basic Auth setup, do not include credentials in this URL. For openHAB Cloud / myopenHAB.org, use `https://home.myopenhab.org/rachio/webhook`. Prefer openHAB Cloud / myopenHAB.org or a properly authenticated reverse proxy; do not expose an unauthenticated openHAB endpoint directly to the Internet.|
|callbackUsername |Optional HTTP Basic Auth username for the webhook endpoint, for example your myopenHAB.org email address. Enter the raw value; the binding percent-encodes it before registering the webhook with Rachio.|
|callbackPassword |Optional HTTP Basic Auth password for the webhook endpoint. Enter the raw value, including special characters such as `@`, `?`, `#`, or `/`; the binding percent-encodes it before registering the webhook with Rachio.|
|clearAllCallbacks|The binding dynamically registers callbacks and supports multiple applications receiving events. If your callback setup changes, enable this once to clear stale URLs so the old callback no longer receives events. Disable it again after a successful registration.|

The bridge thing doesn't have any channels.

### Adding Controllers and Zones

Recommended: use Inbox discovery.

1. Add and configure the Rachio Cloud bridge.
2. Wait for the bridge to initialize successfully.
3. Accept the discovered controller and zone Things.

Discovery runs automatically after successful bridge initialization, creates stable openHAB Thing UIDs, and provides the real Rachio API identifiers automatically.
Use Scan as an optional re-run if you add Rachio resources later or want to refresh the Inbox.

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

Webhook forwarding through openHAB Cloud / myopenHAB.org does not require exposing any Items in the Cloud Connector configuration.

The username and password are entered as raw values. The binding percent-encodes them internally before registering the webhook URL with Rachio, so you do not need to manually encode `@`, `?`, `#`, `/`, or similar URI-reserved characters.

Legacy `callbackUrl` values that already contain validly encoded credentials, such as `https://user%40example.com:pass%3Fword@home.myopenhab.org/rachio/webhook`, remain supported for backward compatibility. The separate `callbackUsername` and `callbackPassword` fields are recommended for new configurations and take precedence if both models are configured.

### Device Thing - represents one Rachio Controller

|Channel      |Description                                                                                                            |
|:------------|:----------------------------------------------------------------------------------------------------------------------|
|name         |Device Name - name of the controller                                                                                   |
|active       |ON: Device is active, OFF: Device is deactivated                                                                       |
|online       |ON: Controller is connected to the cloud. OFF: Controller is offline, check Internet connection.                       |
|paused       |ON: Pause the currently active zone run for `pauseTime` seconds; OFF: Resume the active zone run                       |
|pauseTime    |`Number:Time` duration to pause the active zone run when `paused` receives ON. Plain numeric commands are seconds. Valid range is 0 to 3600 seconds.|
|sleepMode    |ON: Rachio device sleep mode is active, OFF: Rachio device sleep mode is not active (read-only webhook state).         |
|stop         |ON: Stop watering for all zones (command), OFF: normal operation                                                       |
|run          |ON: Start watering selected/all zones (defined in runZones)                                                            |
|runZones     |Zones to run at a time - list, e.g: "1,3" = run zone 1 and 3; "" means: run all zones                                  |
|runTime      |Controller-level `Number:Time` run duration for the multi-zone `run` command. Plain numeric commands are seconds.      |
|rainDelay    |`Number:Time` rain delay duration. Plain numeric commands are seconds; 0 means currently not in rain delay mode.       |
|rainSensorTripped|ON: Rain sensor has tripped (rain detected)                                                                        |
|activeZoneNumber|Zone number currently watering, populated from zone run webhook events.                                             |
|activeZoneName|Zone name currently watering, populated from zone run webhook events.                                                 |
|activeZoneId|Rachio zone UUID currently watering, populated from zone run webhook events.                                            |
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
|currentScheduleDuration|`Number:Time` duration of the currently running schedule, published in seconds.                               |
|lastApiEventType|Type of the latest event retrieved from recent device event history.                                                |
|lastApiEventTime|Time of the latest event retrieved from recent device event history.                                                |
|lastApiEventSummary|Summary of the latest event retrieved from recent device event history.                                          |
|forecastSummary|Forecast summary from Rachio.                                                                                        |
|forecastTodayHigh|Today's `Number:Temperature` high temperature in the configured forecast units.                                    |
|forecastTodayLow|Today's `Number:Temperature` low temperature in the configured forecast units.                                      |
|forecastPrecipitation|Today's `Number:Length` forecast precipitation amount in the configured forecast units.                       |
|forecastPrecipitationProbability|Today's `Number:Dimensionless` precipitation probability. Rachio values are 0..1 fractions and the channel uses the `one` unit hint.|
|forecastWind|Today's `Number:Speed` wind speed in the configured forecast units.                                                   |
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

The `activeZoneNumber`, `activeZoneName`, and `activeZoneId` channels are event-driven.
They are set when Rachio sends a zone-run-started webhook event, preserved during pause events, and cleared when the zone run is stopped or completed.
After an openHAB restart they may remain empty until the next relevant webhook event.

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
|runTime      |`Number:Time` duration to run the zone when run receives ON. Plain numeric commands are seconds.                       |
|runTotal     |Total `Number:Time` duration the zone was watering, as returned by the cloud service.                                  |
|availableWater|`Number:Length` available water value returned by Rachio for the zone, published in inches.                         |
|imageUrl     |URL to the zone picture as configured in the App. Rachio supplies default pictures if no image was created.            |
|image        |Native openHAB Image channel for the zone picture.                                                                    |
|depthOfWater |`Number:Length` depth of water value returned by Rachio, published in inches.                                         |
|saturatedDepthOfWater|`Number:Length` saturated depth of water value returned by Rachio, published in inches.                    |
|managementAllowedDepletion|`Number:Dimensionless` depletion fraction returned by Rachio.                                               |
|rootZoneDepth|`Number:Length` root zone depth value returned by Rachio, published in inches.                                        |
|efficiency   |`Number:Dimensionless` efficiency fraction returned by Rachio.                                                         |
|yardAreaSquareFeet|`Number:Area` yard area in square feet as returned by Rachio.                                                    |
|lastWateredDate|Timestamp when Rachio reports the zone was last watered.                                                            |
|fixedRuntime |`Number:Time` fixed runtime value returned by Rachio, published in seconds.                                           |
|maxRuntime   |`Number:Time` maximum runtime value returned by Rachio, published in seconds.                                         |
|runtimeNoMultiplier|`Number:Time` runtime without multiplier value returned by Rachio, published in seconds.                     |
|scheduleDataModified|ON when Rachio reports modified schedule data for the zone.                                                    |
|moistureLevel|`Number:Length` command channel for `zone/setMoistureLevel`. Plain numeric commands are millimeters.                  |
|moisturePercent|`Number:Dimensionless` command channel for `zone/setMoisturePercent`. Plain numeric commands are a 0..1 fraction; quantity percentages such as `50 %` are converted to `0.5`.|
|lastUpdate   |Timestamp of last status update                                                                                        |
|lastEvent    |Last event received from the cloud (requires configuration of event callback)                                          |
|lastEventTime|Timestamp last event has been received (only if event callback is active)                                              |

Zone identity is based on the Rachio API zone UUID configured as `zoneId`.
Discovery fills this automatically.
For manually created zone Things, set `zoneId` to the Rachio API zone UUID.
The existing `imageUrl` channel remains available for URL-based integrations.
The `image` channel downloads the same zone picture as native openHAB image data and can be linked to an `Image` Item.
If an image cannot be downloaded, the zone remains online and the URL channel is still updated.

### Smart Hose Timer Things

Smart Hose Timer support covers BaseStations, Valves, and Valve Programs.
Discovery is recommended: after the Cloud bridge is online, automatic Inbox discovery creates `basestation` Things, matching `valve` Things, and `valveprogram` Things where the Rachio Program API returns program IDs.
Use Scan as an optional manual refresh.
Manual creation is also supported when the real Rachio IDs are configured:

```
Thing basestation hosehub "Hose Timer Hub" [
    baseStationId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
]

Thing valve garden "Garden Hose Valve" [
    valveId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    baseStationId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
]

Thing valveprogram morninghose "Morning Hose Program" [
    programId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    valveId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    baseStationId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
]
```

BaseStations are lightweight read-only Things.

|Channel|Description|
|:------|:----------|
|name|BaseStation name when reported by Rachio.|
|online|ON when Rachio reports the BaseStation is online or connected.|
|lastUpdate|Timestamp of last BaseStation state update.|

Valves can be started and stopped from openHAB.

|Channel|Description|
|:------|:----------|
|name|Valve name when reported by Rachio.|
|online|ON when Rachio reports the valve is online or connected.|
|run|Send ON to start watering, OFF to stop watering.|
|runTime|`Number:Time` runtime for the next manual valve start. Plain numeric commands are seconds. If 0, the valve default runtime is used, then the bridge `defaultRuntime` fallback.|
|defaultRuntime|`Number:Time` valve default manual runtime. Plain numeric commands are seconds. Sending a value updates Rachio using `setDefaultRuntime`.|
|stateMatches|ON when `ValveState.matches` indicates the physical valve has synchronized with the desired cloud-side state.|
|flowDetected|ON when a valve webhook event or valve state reports flow.|
|batteryLevel|`Number:Dimensionless` battery level when reported by Rachio, published as percent.|
|serialNumber|Valve serial number when reported by Rachio.|
|lastRunType|Run type from the most recent valve webhook event.|
|lastEndReason|End reason from the most recent valve stop webhook event.|
|nextPlannedRunTime|Start time of the next planned valve run from Summary day views.|
|nextPlannedRunDuration|`Number:Time` duration of the next planned valve run, published in seconds.|
|nextPlannedRunProgramId|Program ID associated with the next planned valve run.|
|nextPlannedRunSkipped|ON when the next planned valve run is currently skipped.|
|lastCompletedRunTime|Start time of the most recent completed valve run from Summary day views.|
|lastCompletedRunDuration|`Number:Time` duration of the most recent completed valve run, published in seconds.|
|lastRunStatus|Status of the most recent completed valve run from Summary day views.|
|skipNextPlannedRun|Send ON to skip the next upcoming valve planned run when Summary identifiers are available.|
|cancelNextPlannedRunSkip|Send ON to cancel the next skipped upcoming valve run when Summary identifiers are available.|
|lastUpdate|Timestamp of last valve state update.|
|lastEvent|Most recent valve webhook event.|
|lastEventTime|Timestamp of the most recent valve webhook event.|

The Smart Hose Timer API is asynchronous.
After changing `defaultRuntime`, Rachio may report `stateMatches=OFF` until the physical valve downloads and applies the cloud-side update.

Summary day-view polling is intentionally conservative and uses the bridge `hoseSummaryLookbackDays` and `hoseSummaryLookaheadDays` configuration.
If Summary data cannot be refreshed, the binding keeps the last known Summary-derived valve/program channel values and logs the failure.

Valve Programs expose schedule metadata and upcoming skip controls.

|Channel|Description|
|:------|:----------|
|name|Program name.|
|enabled|ON when Rachio reports that the program is enabled.|
|programType|Program type returned by Rachio.|
|valveId|Associated Smart Hose Timer Valve UUID.|
|startTime|Program start time value returned by Rachio.|
|nextRunTime|Next planned run time when available from Rachio or Summary day views.|
|lastRunTime|Last run time when available from Rachio or Summary day views.|
|duration|`Number:Time` Program duration, published in seconds.|
|daysOfWeek|Days-of-week structure returned by Rachio.|
|intervalDays|`Number:Time` Program interval, published in days when provided by Rachio.|
|seasonalAdjustment|`Number:Dimensionless` seasonal adjustment fraction when provided by Rachio.|
|updatedAt|Last update time when provided by Rachio.|
|nextProgramRunSkipped|ON when the next upcoming run for this program is currently skipped.|
|skipNextPlannedRun|Send ON to skip the next upcoming planned run for this program when Summary identifiers are available.|
|cancelNextPlannedRunSkip|Send ON to cancel the next skipped upcoming run for this program when Summary identifiers are available.|
|lastRainSkipPlannedRunStartTime|Planned run start time from the most recent Program rain-skip-created webhook event.|
|lastRainSkipCanceledPlannedRunStartTime|Planned run start time from the most recent Program rain-skip-canceled webhook event.|
|lastUpdate|Timestamp of last Program state update.|
|lastEvent|Most recent Program webhook event.|
|lastEventTime|Timestamp of the most recent Program webhook event.|

Skip commands use Summary day-view identifiers.
When a planned run ID and date are available, the binding uses the planned-run skip override endpoints.
When only a Program ID and timestamp are available, it falls back to the Program skip override endpoints.
If neither identifier set is available, the command is rejected and no invalid API request is sent.

Program V2 read/discovery is preferred.
The binding can fall back to legacy Program read endpoints where practical, but the older list endpoint is limited by Rachio to single-valve programs with fixed start times.
Program create/update/delete API support is present internally, but branch 20 keeps user-facing Program editing conservative instead of exposing a fragile full schedule editor.

Valve webhook registration uses the resource-aware WebhookService support and subscribes to `VALVE_RUN_START_EVENT` and `VALVE_RUN_END_EVENT` for each initialized Valve Thing when `callbackUrl` is configured.
Program webhook registration subscribes to `PROGRAM_RAIN_SKIP_CREATED_EVENT` and `PROGRAM_RAIN_SKIP_CANCELED_EVENT` for each initialized Valve Program Thing.
Smart Hose Timer summary/day-view, Program discovery, and skip override support are part of this binding; Program creation/editing UX can be expanded later.

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
|seasonalAdjustment|`Number:Dimensionless` seasonal adjustment value. Sending a plain number preserves the existing fraction semantics and updates the schedule rule adjustment.|
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
The binding parses Rachio's `listWebhookEventTypes` response as resource-specific groups and uses that catalog to avoid registering event types against the wrong resource family.
Schedule events update controller schedule channels and matching `schedule` Things when present.
Zone run events update the corresponding zone Thing when the event carries enough zone identity information.
Weather skip notifications update the controller `lastSkip*` channels and the normal `lastEvent` channels.
Smart Hose Timer valve run start/end events update matching `valve` Things.
Smart Hose Timer Program rain-skip-created/canceled events update matching `valveprogram` Things.
Smart Lighting events are prepared internally but are not user-facing yet.
Resource-family events that are not represented by current Things are safely ignored with DEBUG logging.

### Property/Home API

The binding includes internal support for Rachio's modern Property Service on `https://cloud-rest.rach.io`.
This is infrastructure for future multi-product support and does not currently create user-facing Property/Home Things.
The implemented API layer can list properties for a user, retrieve a property by ID, and look up a property by documented entity resource identifiers.
Current internal lookup helpers cover the documented location, base station, and lighting area entity IDs.
No controller, valve, or lighting-controller Property lookup helpers are exposed until Rachio documents those direct lookup parameters.
Future Smart Hose Timer and Smart Lighting support can build on this without changing existing controller, zone, schedule, or flex schedule Things.

# Full example

## Thing Definition

```
Bridge rachio:cloud:1 @ "Sprinkler" [ apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",  pollingInterval=180, defaultRuntime=120  ]
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

    Thing basestation hosehub "Hose Timer Hub" @ "Garden" [
        baseStationId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]

    Thing valve gardenhose "Garden Hose Valve" @ "Garden" [
        valveId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
        baseStationId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    ]

    Thing valveprogram morninghose "Morning Hose Program" @ "Garden" [
        programId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
        valveId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
        baseStationId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
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
    Number   RachioC04DAC_ActiveZoneNo  "Active Zone Number" {channel="rachio:device:1:XXXXXXXXXXXX:activeZoneNumber"}
    String   RachioC04DAC_ActiveZone    "Active Zone"        {channel="rachio:device:1:XXXXXXXXXXXX:activeZoneName"}
    String   RachioC04DAC_ActiveZoneId  "Active Zone ID"     {channel="rachio:device:1:XXXXXXXXXXXX:activeZoneId"}
    String   RachioC04DAC_lastEvent     "Last Event"         {channel="rachio:device:1:XXXXXXXXXXXX:lastEvent"}
    DateTime RachioC04DAC_lastEventTime "Last Event Time"    {channel="rachio:device:1:XXXXXXXXXXXX:lastEventTime"}
    DateTime RachioC04DAC_lastUpdate    "Last Update"        {channel="rachio:device:1:XXXXXXXXXXXX:lastUpdate"}

    // Zone1
    String   RachioZone1_Name           "Zone Name"       {channel="rachio:zone:1:XXXXXXXXXXXX-1:name"}
    Number   RachioZone1_Number         "Zone Number"     {channel="rachio:zone:1:XXXXXXXXXXXX-1:number"}  
    Switch   RachioZone1_Enabled        "Zone Enabled"    {channel="rachio:zone:1:XXXXXXXXXXXX-1:enabled"}
    Switch   RachioZone1_Run            "Run Zone"        {channel="rachio:zone:1:XXXXXXXXXXXX-1:run"}
    Number   RachioZone1_RunTime        "Zone Runtime"    {channel="rachio:zone:1:XXXXXXXXXXXX-1:runTime"}
    Number   RachioZone1_RunTotal       "Total Runtime"   {channel="rachio:zone:1:XXXXXXXXXXXX-1:runTotal"}
    String   RachioZone1_ImageUrl       "Zone Image URL"  {channel="rachio:zone:1:XXXXXXXXXXXX-1:imageUrl"}
    Image    RachioZone1_Image          "Zone Image"      {channel="rachio:zone:1:XXXXXXXXXXXX-1:image"}
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
    Image    RachioZone2_Image          "Zone Image"      {channel="rachio:zone:1:XXXXXXXXXXXX-2:image"}
    String   RachioZone2_lastEvent      "Last Event"      {channel="rachio:zone:1:XXXXXXXXXXXX-2:lastEvent"}
    DateTime RachioZone2_lastEventTime  "Last Event Time" {channel="rachio:zone:1:XXXXXXXXXXXX-2:lastEventTime"}
    DateTime RachioZone2_lastUpdate     "Last Update"     {channel="rachio:zone:1:XXXXXXXXXXXX-2:lastUpdate"}

    // Smart Hose Timer Valve
    String   HoseValve_Name             "Valve Name"             {channel="rachio:valve:1:gardenhose:name"}
    Switch   HoseValve_Run              "Run Hose Valve"         {channel="rachio:valve:1:gardenhose:run"}
    Number   HoseValve_RunTime          "Hose Runtime"           {channel="rachio:valve:1:gardenhose:runTime"}
    Number   HoseValve_DefaultRuntime   "Default Hose Runtime"   {channel="rachio:valve:1:gardenhose:defaultRuntime"}
    DateTime HoseValve_NextRun          "Next Hose Run"          {channel="rachio:valve:1:gardenhose:nextPlannedRunTime"}
    Switch   HoseValve_NextRunSkipped   "Next Hose Run Skipped"  {channel="rachio:valve:1:gardenhose:nextPlannedRunSkipped"}
    Switch   HoseValve_SkipNext         "Skip Next Hose Run"     {channel="rachio:valve:1:gardenhose:skipNextPlannedRun"}
    Switch   HoseValve_CancelSkip       "Cancel Hose Skip"       {channel="rachio:valve:1:gardenhose:cancelNextPlannedRunSkip"}

    // Smart Hose Timer Program
    String   HoseProgram_Name           "Program Name"           {channel="rachio:valveprogram:1:morninghose:name"}
    Switch   HoseProgram_Enabled        "Program Enabled"        {channel="rachio:valveprogram:1:morninghose:enabled"}
    DateTime HoseProgram_NextRun        "Program Next Run"       {channel="rachio:valveprogram:1:morninghose:nextRunTime"}
    Switch   HoseProgram_NextSkipped    "Program Run Skipped"    {channel="rachio:valveprogram:1:morninghose:nextProgramRunSkipped"}
    Switch   HoseProgram_SkipNext       "Skip Program Run"       {channel="rachio:valveprogram:1:morninghose:skipNextPlannedRun"}
    Switch   HoseProgram_CancelSkip     "Cancel Program Skip"    {channel="rachio:valveprogram:1:morninghose:cancelNextPlannedRunSkip"}
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
- `VALVE_RUN_START_EVENT` - Smart Hose Timer valve watering started
- `VALVE_RUN_END_EVENT` - Smart Hose Timer valve watering ended
- `PROGRAM_RAIN_SKIP_CREATED_EVENT` - Smart Hose Timer Program rain skip created
- `PROGRAM_RAIN_SKIP_CANCELED_EVENT` - Smart Hose Timer Program rain skip canceled

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
