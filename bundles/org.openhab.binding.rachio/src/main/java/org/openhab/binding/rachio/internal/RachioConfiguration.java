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

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioConfiguration} contains the binding configuration and default values. The field names represent the
 * configuration names, do not rename them if you don't intend to break the configuration interface.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioConfiguration {
    private static final String REDACTED = "[redacted]";
    private static final String REDACTED_INVALID_URL = "[redacted invalid URL]";

    private final Logger logger = LoggerFactory.getLogger(RachioConfiguration.class);

    public String apikey = "";
    public int pollingInterval = DEFAULT_POLLING_INTERVAL_SEC;
    public int defaultRuntime = DEFAULT_ZONE_RUNTIME_SEC;
    public String callbackUrl = "";
    public String callbackUsername = "";
    public String callbackPassword = "";
    public Boolean clearAllCallbacks = false;
    public int eventHistoryLookbackHours = DEFAULT_EVENT_HISTORY_LOOKBACK_HOURS;
    public String forecastUnits = DEFAULT_FORECAST_UNITS;

    public void updateConfig(@Nullable Map<String, @Nullable Object> config) {
        if (config == null) {
            return;
        }
        for (HashMap.@Nullable Entry<String, @Nullable Object> ce : config.entrySet()) {
            String key = ce.getKey();
            if (key.equalsIgnoreCase("component.name") || key.equalsIgnoreCase("component.id")) {
                continue;
            }
            if (ce.getValue() == null) {
                // no value set
                continue;
            }
            Object configValue = ce.getValue();
            String value = configValue != null ? configValue.toString() : "";

            if (key.equalsIgnoreCase("service.pid")) {
                logger.debug("Rachio: Binding configuration:");
            }
            logger.debug("  {}={}", key, sanitizeValueForLogging(key, value));

            if (key.equalsIgnoreCase(PARAM_APIKEY)) {
                apikey = value;
            } else if (key.equalsIgnoreCase(PARAM_POLLING_INTERVAL)) {
                this.pollingInterval = Integer.parseInt(value);
            } else if (key.equalsIgnoreCase(PARAM_DEF_RUNTIME)) {
                this.defaultRuntime = Integer.parseInt(value);
            } else if (key.equalsIgnoreCase(PARAM_CALLBACK_URL)) {
                this.callbackUrl = value;
            } else if (key.equalsIgnoreCase(PARAM_CALLBACK_USERNAME)) {
                this.callbackUsername = value;
            } else if (key.equalsIgnoreCase(PARAM_CALLBACK_PASSWORD)) {
                this.callbackPassword = value;
            } else if (key.equalsIgnoreCase(PARAM_CLEAR_CALLBACK)) {
                String str = value;
                this.clearAllCallbacks = str.toLowerCase().equals("true");
            } else if (key.equalsIgnoreCase(PARAM_EVENT_HISTORY_LOOKBACK_HOURS)) {
                this.eventHistoryLookbackHours = Math.max(0, Integer.parseInt(value));
            } else if (key.equalsIgnoreCase(PARAM_FORECAST_UNITS)) {
                this.forecastUnits = value.equalsIgnoreCase("US") ? "US" : "METRIC";
            }
        }
    }

    private String sanitizeValueForLogging(String key, String value) {
        if (key.equalsIgnoreCase(PARAM_APIKEY)) {
            return REDACTED;
        }
        if (key.equalsIgnoreCase(PARAM_CALLBACK_USERNAME)) {
            return REDACTED;
        }
        if (key.equalsIgnoreCase(PARAM_CALLBACK_PASSWORD)) {
            return REDACTED;
        }
        if (key.equalsIgnoreCase(PARAM_CALLBACK_URL)) {
            return sanitizeCallbackUrlForLogging(value);
        }
        return value;
    }

    private String sanitizeCallbackUrlForLogging(String value) {
        if (value.isBlank()) {
            return value;
        }

        try {
            URI uri = new URI(value);
            String rawUserInfo = uri.getRawUserInfo();
            String rawAuthority = uri.getRawAuthority();
            if (rawUserInfo == null) {
                if (rawAuthority != null && rawAuthority.contains("@")) {
                    return REDACTED_INVALID_URL;
                }
                return value;
            }

            if (rawAuthority == null) {
                return REDACTED_INVALID_URL;
            }

            String userInfoPrefix = rawUserInfo + "@";
            if (!rawAuthority.startsWith(userInfoPrefix)
                    || rawAuthority.indexOf('@') != rawAuthority.lastIndexOf('@')) {
                return REDACTED_INVALID_URL;
            }

            return buildCallbackUrlForLogging(uri, "***:***@" + rawAuthority.substring(userInfoPrefix.length()));
        } catch (URISyntaxException e) {
            return REDACTED_INVALID_URL;
        }
    }

    private String buildCallbackUrlForLogging(URI uri, String sanitizedAuthority) {
        StringBuilder url = new StringBuilder();
        String scheme = uri.getScheme();
        if (scheme != null) {
            url.append(scheme).append(":");
        }
        url.append("//").append(sanitizedAuthority);

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
}
