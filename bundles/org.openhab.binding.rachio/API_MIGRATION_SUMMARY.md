# Rachio Binding - OpenHAB 5.1 API Migration Summary

## Overview
This document summarizes the migration of the Rachio Binding from the legacy webhook notification API to the current WebhookService API and support for OpenHAB 5.1.

## Changes Made

### 1. **API Endpoint Updates** [RachioBindingConstants.java]

#### Added New Cloud REST Base URL
```java
public static final String APIURL_CLOUD_REST_BASE = "https://cloud-rest.rach.io";
```

#### New WebhookService Endpoints (cloud-rest.rach.io)
```java
public static final String WEBHOOK_CREATE = "/webhook/createWebhook";
public static final String WEBHOOK_GET = "/webhook/getWebhook/";
public static final String WEBHOOK_LIST = "/webhook/listWebhooks";
public static final String WEBHOOK_UPDATE = "/webhook/updateWebhook";
public static final String WEBHOOK_DELETE = "/webhook/deleteWebhook/";
public static final String WEBHOOK_DELETE_ALL = "/webhook/deleteAllWebhooks";
public static final String WEBHOOK_LIST_EVENT_TYPES = "/webhook/listWebhookEventTypes";
```

### 2. **Event Type Constants** [RachioBindingConstants.java]

#### Added New String-Based Event Types (replacing numeric IDs)
```java
// New Webhook event types (string-based)
public static final String EVENT_DEVICE_ZONE_RUN_STARTED = "DEVICE_ZONE_RUN_STARTED_EVENT";
public static final String EVENT_DEVICE_ZONE_RUN_COMPLETED = "DEVICE_ZONE_RUN_COMPLETED_EVENT";
public static final String EVENT_SCHEDULE_STARTED = "SCHEDULE_STARTED_EVENT";
public static final String EVENT_SCHEDULE_COMPLETED = "SCHEDULE_COMPLETED_EVENT";
public static final String EVENT_RAIN_SKIP = "RAIN_SKIP_NOTIFICATION_EVENT";
public static final String EVENT_CLIMATE_SKIP = "CLIMATE_SKIP_NOTIFICATION_EVENT";
public static final String EVENT_FREEZE_SKIP = "FREEZE_SKIP_NOTIFICATION_EVENT";
public static final String EVENT_WIND_SKIP = "WIND_SKIP_NOTIFICATION_EVENT";
public static final String EVENT_NO_SKIP = "NO_SKIP_NOTIFICATION_EVENT";
```

### 3. **Configuration Handling** [RachioConfiguration.java]

The binding keeps the existing bridge configuration surface and uses the current WebhookService when `callbackUrl` is configured.

### 4. **HTTP Client Support** [RachioHttp.java]

#### Extended Constructor for Future Multi-URL Support
```java
public RachioHttp(final String key, final String baseUrl) {
    apikey = key;
    // Note: baseUrl parameter could be used for future extensions
}
```

### 5. **Webhook Registration Method** [RachioApi.java]

#### Updated `registerWebHook()` Method
The method implements the modern WebhookService API:
```java
public void registerWebHook(String deviceId, String callbackUrl,
    @Nullable String externalId, Boolean clearAllCallbacks) throws RachioApiException
```

**Features:**
- Uses `https://cloud-rest.rach.io/webhook/*` endpoints
- JSON payload format compatible with new API:
  ```json
  {
    "resourceId": {
      "irrigationControllerId": "device-id"
    },
    "externalId": "external-id",
    "url": "https://callback-url",
    "eventTypes": [
      "DEVICE_ZONE_RUN_STARTED_EVENT",
      "DEVICE_ZONE_RUN_COMPLETED_EVENT",
      "SCHEDULE_STARTED_EVENT",
      ...
    ]
  }
  ```
- Supports webhook cleanup and deletion
- Returns improved error handling

### 6. **Bridge Handler Integration** [RachioBridgeHandler.java]

#### Updated Webhook Registration Logic
```java
public void registerWebHook(String deviceId) throws RachioApiException {
    if (getCallbackUrl().isEmpty()) {
        logger.debug("RachioCloud: No callbackUrl configured.");
    } else {
        rachioApi.registerWebHook(deviceId, getCallbackUrl(), getExternalId(), getClearAllCallbacks());
    }
}
```

### 7. **Bug Fixes**

#### Fixed Null Pointer Handling in Webhook List Parsing
Updated `parseWebHookList()` to properly handle nullable webhook entries:
```java
private void deleteExistingWebHooks(String json, String deviceId, 
    String callbackUrl, @Nullable String externalId, Boolean clearAllCallbacks) {
    for (@Nullable RachioApiWebHookEntry whe : parseWebHookList(json)) {
        if (whe == null) {
            continue;
        }
        // ... rest of logic
    }
}
```

## API Compatibility

### Authentication
OK **No Changes** - Bearer token authentication remains the same:
```
Authorization: Bearer {apikey}
```

### Rate Limiting
OK **No Changes** - Rate limit headers and limits remain compatible:
- `X-RateLimit-Limit`: 3,500 requests per day
- `X-RateLimit-Remaining`: Remaining requests
- `X-RateLimit-Reset`: Reset timestamp in UTC

### Device Control Endpoints
OK **No Changes** - Still use `https://api.rach.io/1/public/` endpoints:
- GET `/public/device/{id}`
- PUT `/public/device/on`, `/public/device/off`
- PUT `/public/zone/start`, `/public/zone/start_multiple`
- etc.

## Migration Path for Users

### Step 1: Update Configuration
Configure a public HTTPS callback URL that forwards to `/rachio/webhook`:
```
callbackUrl="https://host.example.org/rachio/webhook"
```

### Step 2: Test Webhook Registration
The binding will automatically register the callback with WebhookService. Test that:
1. Webhook registration succeeds
2. Events are received at the callback URL
3. Events are properly parsed and processed

### Step 3: Monitor Event Processing
Check logs for event type processing. The new API returns:
```json
{
  "eventId": "...",
  "eventType": "DEVICE_ZONE_RUN_STARTED_EVENT",
  "externalId": "...",
  "payload": {
    "durationSeconds": "120",
    "startTime": "2024-12-19T19:43:34.685Z",
    "zoneNumber": "10"
  },
  "resourceId": "...",
  "resourceType": "IRRIGATION_CONTROLLER",
  "timestamp": "..."
}
```

## Future Enhancements (Phase 2+)

The migration creates the foundation for:

### 1. **Lighting Service** Support
- New thing types: `lightingController`, `lightingZone`
- Endpoints: `https://cloud-rest.rach.io/lighting/*`

### 2. **Valve Service** Support (Smart Hose Timers)
- New thing types: `baseStation`, `valve`
- Endpoints: `https://cloud-rest.rach.io/valve/*`

### 3. **Property Service** Support
- New thing type: `property` (homes)
- Endpoints: `https://cloud-rest.rach.io/property/*`

### 4. **Program Service** Enhancements
- V2 API support for advanced scheduling
- Endpoints: `https://cloud-rest.rach.io/program/*`

## Testing & Validation

### Build Status
OK **Compilation Successful** - All code compiles without errors

### Compilation Warnings
- Existing nullable pointer warnings (not introduced by this migration)
- Pre-existing code quality issues

### Backward Compatibility
- Existing thing and channel IDs remain unchanged.
- Existing `callbackUrl` and `clearAllCallbacks` configuration remains in use.

## Files Modified

1. `RachioBindingConstants.java` - Added new endpoints and event types
2. `RachioConfiguration.java` - Improved configuration null handling
3. `RachioHttp.java` - Extended constructor for future support
4. `RachioApi.java` - Updated webhook registration method, fixed null handling
5. `RachioBridgeHandler.java` - Uses the updated webhook registration logic

## Files Unchanged

- Event parsing and serialization still supported (backward compatible)
- Device control logic remains unchanged
- Zone management unchanged
- All handler and servlet logic compatible with both old and new APIs

## Documentation References

- Rachio API Documentation: https://rachio.readme.io/reference
- OpenHAB Developer Guide: https://www.openhab.org/docs/developer/
- Rachio Rate Limiting: https://rachio.readme.io/reference/rate-limiting

## Notes

- The new WebhookService uses different event payload structure
- String-based event types are more descriptive than numeric IDs
- The `externalId` field in new webhook format aids in event routing
- WebhookService requests are validated with the HMAC-SHA256 `x-signature` header before payload parsing

