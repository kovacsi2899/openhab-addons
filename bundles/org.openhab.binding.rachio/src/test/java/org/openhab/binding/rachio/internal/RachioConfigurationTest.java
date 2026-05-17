/**
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.DEFAULT_EVENT_HISTORY_LOOKBACK_HOURS;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.DEFAULT_FORECAST_UNITS;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.MAX_EVENT_HISTORY_LOOKBACK_HOURS;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.PARAM_EVENT_HISTORY_LOOKBACK_HOURS;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.PARAM_FORECAST_UNITS;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests robust Rachio binding configuration parsing.
 */
class RachioConfigurationTest {
    @Test
    void invalidEventHistoryLookbackUsesDefault() {
        RachioConfiguration configuration = new RachioConfiguration();

        configuration.updateConfig(Map.of(PARAM_EVENT_HISTORY_LOOKBACK_HOURS, "not-a-number"));

        assertThat(configuration.eventHistoryLookbackHours, is(DEFAULT_EVENT_HISTORY_LOOKBACK_HOURS));
    }

    @Test
    void negativeEventHistoryLookbackDisablesPolling() {
        RachioConfiguration configuration = new RachioConfiguration();

        configuration.updateConfig(Map.of(PARAM_EVENT_HISTORY_LOOKBACK_HOURS, "-1"));

        assertThat(configuration.eventHistoryLookbackHours, is(0));
    }

    @Test
    void excessivelyLargeEventHistoryLookbackIsClamped() {
        RachioConfiguration configuration = new RachioConfiguration();

        configuration.updateConfig(Map.of(PARAM_EVENT_HISTORY_LOOKBACK_HOURS, "9999"));

        assertThat(configuration.eventHistoryLookbackHours, is(MAX_EVENT_HISTORY_LOOKBACK_HOURS));
    }

    @Test
    void invalidForecastUnitsUsesDefault() {
        RachioConfiguration configuration = new RachioConfiguration();

        configuration.updateConfig(Map.of(PARAM_FORECAST_UNITS, "SI"));

        assertThat(configuration.forecastUnits, is(DEFAULT_FORECAST_UNITS));
    }

    @Test
    void forecastUnitsAreNormalized() {
        RachioConfiguration configuration = new RachioConfiguration();

        configuration.updateConfig(Map.of(PARAM_FORECAST_UNITS, "us"));

        assertThat(configuration.forecastUnits, is("US"));
    }
}
