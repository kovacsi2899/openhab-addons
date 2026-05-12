# New Rachio WebhookService Implementation Guide

## Overview
This guide documents the implementation of the new Rachio WebhookService API for OpenHAB 5.1 compatibility.

## Key API Differences

### Old NotificationService (Deprecated)
- **Base URL:** `https://api.rach.io/1/public/`
- **Webhook Endpoints:**
  - POST `/notification/webhook` - Register
  - GET `/notification/{deviceId}/webhook` - Query
  - DELETE `/notification/webhook/{id}` - Delete
- **Event Types:** Numeric (5, 6, 7, 8, 9, 10, 11, 12, 14)
- **Authentication:** Basic HTTP auth in URL (username:password@host)

### New WebhookService (Current)
- **Base URL:** `https://cloud-rest.rach.io/`
- **Webhook Endpoints:**
  - POST `/webhook/createWebhook` - Register
  - GET `/webhook/listWebhooks` - List all
  - GET `/webhook/getWebhook/{id}` - Get single
  - PUT `/webhook/updateWebhook` - Update
  - DELETE `/webhook/deleteWebhook/{id}` - Delete
  - DELETE `/webhook/deleteAllWebhooks` - Delete all
  - GET `/webhook/listWebhookEventTypes` - List available event types
- **Event Types:** String-based (e.g., `DEVICE_ZONE_RUN_STARTED_EVENT`)
- **Authentication:** Bearer token in Authorization header
- **Signature Validation:** HMAC-SHA256 in `x-signature` header

## Configuration

Set the bridge `callbackUrl` to a public HTTPS URL that forwards to `/rachio/webhook`.

## Webhook Payload Structures

### Request Payload (CreateWebhook)
```json
{
  "resourceId": {
    "irrigationControllerId": "85c309c6-ba69-4f90-8f3c-60e5ea3640fb"
  },
  "externalId": "openHAB-rachio-binding",
  "url": "https://webhook.site/0021d1c5-168c-4d3e-a7e4-d2a97deaf82e",
  "eventTypes": [
    "DEVICE_ZONE_RUN_STARTED_EVENT",
    "DEVICE_ZONE_RUN_COMPLETED_EVENT",
    "SCHEDULE_STARTED_EVENT",
    "SCHEDULE_COMPLETED_EVENT",
    "RAIN_SKIP_NOTIFICATION_EVENT",
    "CLIMATE_SKIP_NOTIFICATION_EVENT",
    "FREEZE_SKIP_NOTIFICATION_EVENT",
    "WIND_SKIP_NOTIFICATION_EVENT",
    "NO_SKIP_NOTIFICATION_EVENT"
  ]
}
```

### Event Payload (Received)
```json
{
  "eventId": "6776d89e-b4e7-3f5a-864f-ba39e6bafa05",
  "eventType": "DEVICE_ZONE_RUN_STARTED_EVENT",
  "externalId": "openHAB-rachio-binding",
  "payload": {
    "durationSeconds": "120",
    "startTime": "2024-12-19T19:43:34.685Z",
    "zoneNumber": "10",
    "zoneName": "Front Lawn"
  },
  "resourceId": "85c309c6-ba69-4f90-8f3c-60e5ea3640fb",
  "resourceType": "IRRIGATION_CONTROLLER",
  "timestamp": "2024-12-19T19:43:31Z"
}
```

## Event Types

### Zone Events
- `DEVICE_ZONE_RUN_STARTED_EVENT` - Zone watering started
- `DEVICE_ZONE_RUN_COMPLETED_EVENT` - Zone watering completed
- `DEVICE_ZONE_RUN_STOPPED_EVENT` - Zone watering stopped (user action)
- `DEVICE_ZONE_RUN_PAUSED_EVENT` - Zone watering paused

### Schedule Events
- `SCHEDULE_STARTED_EVENT` - Schedule started
- `SCHEDULE_COMPLETED_EVENT` - Schedule completed
- `SCHEDULE_STOPPED_EVENT` - Schedule stopped

### Weather Events
- `RAIN_SKIP_NOTIFICATION_EVENT` - Rain detected, watering skipped
- `CLIMATE_SKIP_NOTIFICATION_EVENT` - Climate skip applied
- `FREEZE_SKIP_NOTIFICATION_EVENT` - Freeze detected, watering skipped
- `WIND_SKIP_NOTIFICATION_EVENT` - High wind, watering skipped
- `NO_SKIP_NOTIFICATION_EVENT` - Normal watering (no skip)

### Rain Sensor Events
- `RAIN_SENSOR_DETECTION_ON_EVENT` - Rain sensor tripped (if supported by the service)
- `RAIN_SENSOR_DETECTION_OFF_EVENT` - Rain sensor cleared (if supported by the service)

> Note: The binding dynamically queries `/webhook/listWebhookEventTypes` and only subscribes to rain sensor events if the new WebhookService advertises them. If the service does not expose rain sensor change events, `rainSensorTripped` is refreshed by normal polling.

### Additional Events (For Future Implementation)
- `VALVE_RUN_START_EVENT` - Smart hose timer started (Valve service)
- `VALVE_RUN_END_EVENT` - Smart hose timer stopped (Valve service)
- `LIGHTING_ZONE_STATE_CHANGE_EVENT` - Lighting zone state changed (Lighting service)
- `PROGRAM_RAIN_SKIP_CREATED_EVENT` - Program rain skip created
- `PROGRAM_RAIN_SKIP_CANCELED_EVENT` - Program rain skip canceled

## Response Formats

### Successful Webhook Creation (201 Created)
```json
{
  "id": "webhook-id-uuid",
  "resourceId": {
    "irrigationControllerId": "85c309c6-ba69-4f90-8f3c-60e5ea3640fb"
  },
  "externalId": "openHAB-rachio-binding",
  "url": "https://webhook.site/0021d1c5-168c-4d3e-a7e4-d2a97deaf82e",
  "status": "ACTIVE",
  "eventTypes": [
    "DEVICE_ZONE_RUN_STARTED_EVENT",
    ...
  ],
  "created": "2024-12-19T19:40:00Z",
  "lastModified": "2024-12-19T19:40:00Z"
}
```

### Error Response
```json
{
  "code": "INVALID_REQUEST",
  "message": "The requested webhook already exists",
  "details": {
    "field": "url",
    "issue": "duplicate"
  }
}
```

## Webhook Cleanup Logic

### Current Implementation
1. **Query Existing Webhooks**
   - GET `https://cloud-rest.rach.io/webhook/listWebhooks`
   - Returns all webhooks for the user

2. **Filter Matching Webhooks**
   - Match by device ID (`resourceId.irrigationControllerId`)
   - Match by external ID (if specified)
   - Match by URL (if clearAllCallbacks=false)

3. **Delete Matching Webhooks**
   - DELETE `https://cloud-rest.rach.io/webhook/deleteWebhook/{id}` for each match

4. **Register New Webhook**
   - POST `https://cloud-rest.rach.io/webhook/createWebhook` with new configuration

## Error Handling

### Rate Limiting
- Headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- Daily limit: 3,500 requests
- Reset: Midnight UTC
- Thresholds:
  - Warning: 200 remaining
  - Critical: 100 remaining
  - Block: 20 remaining

### HTTP Status Codes
- **200 OK** - Request successful
- **201 Created** - Webhook created
- **400 Bad Request** - Invalid payload
- **401 Unauthorized** - Invalid API key
- **404 Not Found** - Webhook not found
- **409 Conflict** - Duplicate webhook
- **429 Too Many Requests** - Rate limit exceeded
- **500 Internal Server Error** - Server error

## Webhook Signature Validation

The servlet verifies the `x-signature` header before parsing or routing an inbound event.
The expected signature is recomputed with HMAC-SHA256 over the raw request body bytes using the configured Rachio API token.
Requests with a missing or invalid signature are rejected with `401 Unauthorized`.

Payload parsing uses the original JSON body directly for WebhookService events.
Legacy malformed JSON support is isolated to a fallback path for stringified JSON objects, so broad global replacements are not applied to standard WebhookService payloads.

### Why Important
- Prevents webhook spoofing
- Ensures events come from Rachio servers
- Protects against man-in-the-middle attacks

## Testing Checklist

- [ ] Public HTTPS `callbackUrl` is configured
- [ ] Webhook registration uses new endpoint when enabled
- [ ] Webhook cleanup works correctly
- [ ] Incoming events include a valid `x-signature` header
- [ ] Events from new API are received and parsed
- [ ] Existing thing and channel IDs remain unchanged
- [ ] Event routing to device/zone handlers works
- [ ] Rate limiting headers are processed
- [ ] Error responses are handled gracefully

## Logging

Enable DEBUG logging to troubleshoot webhook issues:
```
log:set DEBUG org.openhab.binding.rachio
```

Key log messages:
- "Register webhook, url=..." - Webhook registration started
- "Registered webhooks for device..." - Existing webhooks queried
- "Delete existing webhook..." - Webhook deletion in progress
- "Register WebHook, callback url..." - New webhook registration in progress
- "RachioEvent..." - Event received and processed

## References

- Rachio API: https://rachio.readme.io/reference
- OpenHAB Extensions: https://www.openhab.org/docs/developer/
- OpenAPI Specification: https://swagger.io/specification/

