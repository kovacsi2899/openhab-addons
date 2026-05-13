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
Bridge rachio:cloud:1 [ apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxx", pollingInterval=180, defaultRuntime=120, callbackUrl="https://host.example.org/rachio/webhook", clearAllCallbacks=true  ]
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
|callbackUrl      |Public HTTPS URL that forwards to `/rachio/webhook`. For openHAB Cloud / myopenHAB.org, use `https://username:password@home.myopenhab.org/rachio/webhook` where username is your myopenHAB.org email address (URL-encode @ as %40 if present, e.g., `user%40example.com`). For direct reverse proxies, use `https://yourhost.example.org/rachio/webhook`. Credentials are automatically encoded for Basic Auth. The webhook service automatically signs events with the `x-signature` header.|
|clearAllCallbacks|The binding dynamically registers the callback. It also supports multiple applications registered to receive events, e.g. a 2nd OH device with the binding providing the same functionality. If for any reason your device setup changes (e.g. new ip address) you need to clear the registered URL once to avoid the "old URL" still receiving events. This also allows to move for a test setup to the regular setup.|

The bridge thing doesn't have any channels.

### openHAB Cloud / myopenHAB.org Configuration

For users of [openHAB Cloud](https://www.openhab.org/docs/configuration/openhab-cloud.html) or [myopenHAB.org](https://www.myopenhab.org/), configure the callbackUrl as follows:

1. **Determine your myopenHAB.org credentials:**
   - Username: Your myopenHAB.org email address
   - Password: Your myopenHAB.org password

2. **URL-encode special characters in username:**
   - If your email contains `@`, encode it as `%40`
   - Example: `user@example.com` becomes `user%40example.com`
   - Other special characters like `+` should also be encoded if present

3. **Set the callbackUrl:**
   ```
   callbackUrl="https://user%40example.com:password@home.myopenhab.org/rachio/webhook"
   ```

4. **Validation:**
   - Test that `https://home.myopenhab.org/rachio/webhook` is reachable with Basic Auth
   - Check openHAB logs for successful webhook registration
   - Verify that Rachio events appear in openHAB without delays

**Important:** The binding automatically handles URL encoding of credentials. Manual encoding is only required for the username portion when configuring the callbackUrl parameter.

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
|runTime      |Run time of all zones                                                                                                  |
|rainDelay    |> 0: Rain delay scheduled for x sec; =0: Currently not in rain delay mode                                              |
|rainSensorTripped|ON: Rain sensor has tripped (rain detected)                                                                        |
|lastUpdate   |Timestamp of last status update                                                                                        |
|lastEvent    |Last event received from the cloud (requires configuration of event callback)                                          |
|lastEventTime|Timestamp last event has been received (only if event callback is active)                                              |
|scheduleName |Current/last executed schedule: name                                                                                   |
|scheduleInfo |Description of current/last executed schedule                                                                          |
|scheduleStart|Schedule start time                                                                                                    |
|scheduleStop |Schedule end time                                                                                                      |

The are no additional configuration options on the device level.

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
|lastUpdate   |Timestamp of last status update                                                                                        |
|lastEvent    |Last event received from the cloud (requires configuration of event callback)                                          |
|lastEventTime|Timestamp last event has been received (only if event callback is active)                                              |


# Full example

## Thing Definition

```
Bridge rachio:cloud:1 @ "Sprinkler" [ apikey="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",  pollingInterval=60, defaultRuntime=120  ]
{
    // Controller
    Thing rachio:device:1:XXXXXXXXXXXX "Rachio-XXXXXX" @ "Sprinkler"
    
    // Zones
    Thing rachio:zone:1:XXXXXXXXXXXX-1 "Rachio zone 1" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-2 "Rachio zone 2" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-3 "Rachio zone 3" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-4 "Rachio zone 4" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-5 "Rachio zone 5" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-6 "Rachio zone 6" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-7 "Rachio zone 7" @ "Sprinkler"
    Thing rachio:zone:1:XXXXXXXXXXXX-8 "Rachio zone 8" @ "Sprinkler"
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
DEBUG org.openhab.binding.rachio.internal.api.RachioApi - Register WebHook with new API, url=https://your-domain.com/rachio/webhook
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
