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
package org.openhab.binding.rachio.internal.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.PRIORITY;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.RateLimitThrottleException;

/**
 * Tests local client-side rate limit decisions.
 */
@NonNullByDefault
class ClientRateLimitManagerTest {
    @Test
    void lowPriorityRequestEmitsDistinctThrottleExceptionWhenLocalBudgetIsExceeded() {
        ClientRateLimitManager manager = new ClientRateLimitManager(1, Duration.ofSeconds(60));
        manager.updateRateLimit(100, 1, Long.toString(Instant.now().plusSeconds(3600).getEpochSecond()));

        RateLimitThrottleException exception = assertThrows(RateLimitThrottleException.class,
                () -> manager.tryThrottle(PRIORITY.LOW));

        assertThat(exception.priority, is(PRIORITY.LOW));
    }
}
