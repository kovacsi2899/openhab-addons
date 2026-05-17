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
package org.openhab.binding.rachio.internal.handler;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.THING_TYPE_FLEXSCHEDULE;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.THING_TYPE_SCHEDULE;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * Tests schedule handler status lifecycle around initial refresh failures.
 */
@NonNullByDefault
@SuppressWarnings({ "null" })
class RachioScheduleHandlerStatusTest {
    @Test
    void scheduleHandlerDoesNotOverwriteFailedInitialRefreshWithOnline() {
        Thing thing = thing(new ThingUID(THING_TYPE_SCHEDULE, "bridge", "schedule"));
        TestScheduleHandler handler = new TestScheduleHandler(thing, false);
        ThingHandlerCallback callback = Mockito.mock(ThingHandlerCallback.class);
        handler.setCallback(callback);

        handler.publicGoOnline();

        verify(callback).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.OFFLINE
                && status.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));
        verify(callback, never()).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void scheduleHandlerGoesOnlineAfterSuccessfulInitialRefresh() {
        Thing thing = thing(new ThingUID(THING_TYPE_SCHEDULE, "bridge", "schedule"));
        TestScheduleHandler handler = new TestScheduleHandler(thing, true);
        ThingHandlerCallback callback = Mockito.mock(ThingHandlerCallback.class);
        handler.setCallback(callback);

        handler.publicGoOnline();

        verify(callback).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void flexScheduleHandlerDoesNotOverwriteFailedInitialRefreshWithOnline() {
        Thing thing = thing(new ThingUID(THING_TYPE_FLEXSCHEDULE, "bridge", "flex"));
        TestFlexScheduleHandler handler = new TestFlexScheduleHandler(thing, false);
        ThingHandlerCallback callback = Mockito.mock(ThingHandlerCallback.class);
        handler.setCallback(callback);

        handler.publicGoOnline();

        verify(callback).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.OFFLINE
                && status.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR));
        verify(callback, never()).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.ONLINE));
    }

    @Test
    void flexScheduleHandlerGoesOnlineAfterSuccessfulInitialRefresh() {
        Thing thing = thing(new ThingUID(THING_TYPE_FLEXSCHEDULE, "bridge", "flex"));
        TestFlexScheduleHandler handler = new TestFlexScheduleHandler(thing, true);
        ThingHandlerCallback callback = Mockito.mock(ThingHandlerCallback.class);
        handler.setCallback(callback);

        handler.publicGoOnline();

        verify(callback).statusUpdated(eq(thing), argThat(status -> status.getStatus() == ThingStatus.ONLINE));
    }

    private Thing thing(ThingUID uid) {
        Thing thing = Mockito.mock(Thing.class);
        when(thing.getUID()).thenReturn(uid);
        return thing;
    }

    private static class TestScheduleHandler extends RachioScheduleHandler {
        private final boolean refreshSuccess;

        TestScheduleHandler(Thing thing, boolean refreshSuccess) {
            super(thing);
            this.refreshSuccess = refreshSuccess;
        }

        void publicGoOnline() {
            goOnline();
        }

        @Override
        protected boolean refreshScheduleRule() {
            if (!refreshSuccess) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "failed");
            }
            return refreshSuccess;
        }
    }

    private static class TestFlexScheduleHandler extends RachioFlexScheduleHandler {
        private final boolean refreshSuccess;

        TestFlexScheduleHandler(Thing thing, boolean refreshSuccess) {
            super(thing);
            this.refreshSuccess = refreshSuccess;
        }

        void publicGoOnline() {
            goOnline();
        }

        @Override
        protected boolean refreshFlexScheduleRule() {
            if (!refreshSuccess) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "failed");
            }
            return refreshSuccess;
        }
    }
}
