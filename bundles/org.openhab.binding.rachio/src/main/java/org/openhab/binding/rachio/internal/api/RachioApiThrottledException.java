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
package org.openhab.binding.rachio.internal.api;

import java.time.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.PRIORITY;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.RateLimitThrottleException;

/**
 * Signals that the binding intentionally deferred a Rachio API call due to local client-side rate protection.
 */
@NonNullByDefault
public class RachioApiThrottledException extends RachioApiException {
    private static final long serialVersionUID = 1L;

    private final PRIORITY priority;
    private final double budgetRate;
    private final double currentRate;

    public RachioApiThrottledException(RateLimitThrottleException throttle, RachioApiResult result) {
        super("RachioApi: " + throttle.toString(), result);
        this.priority = throttle.priority;
        this.budgetRate = throttle.budgetRate;
        this.currentRate = throttle.currentRate;
    }

    public PRIORITY getPriority() {
        return priority;
    }

    public double getBudgetRate() {
        return budgetRate;
    }

    public double getCurrentRate() {
        return currentRate;
    }

    public Duration getSuggestedRetryDelay() {
        return switch (priority) {
            case VERY_LOW -> Duration.ofSeconds(60);
            case LOW -> Duration.ofSeconds(30);
            case MED -> Duration.ofSeconds(15);
            case HI -> Duration.ZERO;
        };
    }

    @Override
    public String toString() {
        String message = getMessage();
        return message != null ? message : super.toString();
    }
}
