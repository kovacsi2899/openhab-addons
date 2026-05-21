# Rachio Binding API Integration Summary

## Overview

This document summarizes the current Rachio binding API integration after the OpenHAB 5.1 migration and the later Smart Irrigation / Smart Hose Timer work.

The binding now uses both Rachio API families:

- `https://api.rach.io/1/public/` for the original public Smart Irrigation controller and zone operations.
- `https://cloud-rest.rach.io` for WebhookService, PropertyService, Smart Hose Timer valve/program APIs, and other modern cloud REST services.

## Current Integration Status

### WebhookService

The binding uses the current Rachio WebhookService endpoints:

- `/webhook/createWebhook`
- `/webhook/getWebhook/{id}`
- `/webhook/listWebhooks`
- `/webhook/updateWebhook`
- `/webhook/deleteWebhook/{id}`
- `/webhook/deleteAllWebhooks`
- `/webhook/listWebhookEventTypes`

Webhook handling is resource-aware. The binding queries the event type catalog and validates webhook targets by resource type before registration.

Currently supported webhook resource families:

- `IRRIGATION_CONTROLLER`
- `VALVE`
- `PROGRAM`

Lighting resource types are represented in the internal resource model for future support, but Smart Lighting is not yet a user-facing binding feature.

Webhook payloads are validated with the HMAC-SHA256 `x-signature` header before event processing. Callback URLs and HTTP logs are sanitized so embedded credentials and API tokens are not written in clear text.

### Smart Irrigation Controllers

The binding supports the Smart Irrigation controller model:

- Cloud connector
- Controller Things
- Zone Things
- ScheduleRule Things
- FlexScheduleRule Things

Implemented controller and zone capabilities include:

- Controller and zone discovery, including automatic post-initialization discovery.
- Manual controller creation by configured Rachio `deviceId`.
- Zone run/stop and controller multi-zone start/stop.
- Current schedule, forecast, and recent event history reads.
- Schedule and flex schedule reads and selected schedule commands.
- Zone moisture commands.
- Expanded zone telemetry and native zone image channels.
- Active running zone channels updated from irrigation webhook events.

### Smart Hose Timer

Smart Hose Timer support has moved beyond groundwork and is integrated in the binding.

Implemented user-facing Thing families include:

- `rachio:basestation`
- `rachio:valve`
- `rachio:valveprogram`

Implemented API areas include:

- BaseStation and Valve discovery/read support.
- Valve start/stop watering.
- Valve default runtime updates.
- Valve state synchronization reporting.
- Program V2 read/list support and related Program API calls.
- Summary day-view reads for upcoming/recent valve runs.
- Skip override and planned-run skip override support where the required identifiers are available.
- Valve webhooks for `VALVE_RUN_START_EVENT` and `VALVE_RUN_END_EVENT`.
- Program webhooks for `PROGRAM_RAIN_SKIP_CREATED_EVENT` and `PROGRAM_RAIN_SKIP_CANCELED_EVENT`.

Full rich user-facing editing UX for complex Program creation/update remains intentionally conservative. The API/client support exists, while future work may improve how complex Program editing is exposed in OpenHAB.

### PropertyService

PropertyService API/client groundwork is implemented:

- `/property/getProperty/{id}`
- `/property/listProperties/{userId}`
- `/property/findPropertyByEntity`

The binding includes DTOs and helper methods for internal property/entity lookups. There is not yet a dedicated user-facing `rachio:property` Thing model.

### Rate Limiting

The binding preserves Rachio rate-limit handling and includes local client-side API throttling. Locally throttled low-priority reads are treated as retryable or skippable where appropriate so temporary burst protection does not permanently break Thing initialization.

## Compatibility Notes

- Existing Thing and channel IDs are preserved.
- Existing `callbackUrl` and `clearAllCallbacks` configuration remain supported.
- Separate `callbackUsername` and `callbackPassword` fields are the preferred way to configure webhook Basic Auth credentials.
- Legacy valid callback URLs with embedded credentials remain supported for backward compatibility.
- Discovery-generated controller, zone, schedule, flex schedule, base station, valve, and valve program Things use stable Rachio identifiers.
- Branch 25 changes several channel item types from plain `Number` to typed Quantity channels without renaming channel IDs.
  Branch 26 should add Thing type update instructions for:
  `device` channels `pauseTime`, `runTime`, `rainDelay`, `currentScheduleDuration`, `forecastTodayHigh`, `forecastTodayLow`, `forecastPrecipitation`, `forecastPrecipitationProbability`, and `forecastWind`;
  `zone` channels `runTime`, `runTotal`, `availableWater`, `depthOfWater`, `saturatedDepthOfWater`, `managementAllowedDepletion`, `rootZoneDepth`, `efficiency`, `yardAreaSquareFeet`, `fixedRuntime`, `maxRuntime`, `runtimeNoMultiplier`, `moistureLevel`, and `moisturePercent`;
  `schedule`/`flexschedule` channel `seasonalAdjustment`;
  `valve` channels `runTime`, `defaultRuntime`, `batteryLevel`, `nextPlannedRunDuration`, and `lastCompletedRunDuration`;
  and `valveprogram` channels `duration` and `seasonalAdjustment`.

## Future Work

Known remaining areas include:

- Smart Lighting user-facing Thing and command support.
- Optional dedicated Property/Home Thing modeling.
- Richer user-facing UX for complex Smart Hose Timer Program creation and editing.
- Additional Rachio product families as the public API evolves.

## Documentation References

- Rachio API Documentation: https://rachio.readme.io/reference
- OpenHAB Developer Guide: https://www.openhab.org/docs/developer/
- Rachio Rate Limiting: https://rachio.readme.io/reference/rate-limiting
