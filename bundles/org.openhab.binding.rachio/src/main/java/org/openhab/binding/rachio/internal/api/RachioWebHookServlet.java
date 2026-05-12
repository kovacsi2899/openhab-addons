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
package org.openhab.binding.rachio.internal.api;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioHandlerFactory;
import org.openhab.binding.rachio.internal.api.json.RachioEventGsonDTO;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

/**
 * {@link RachioWebHookServlet} implements the callback for the Rachio Cloud event API.
 *
 * @author Markus Michels - Initial contribution
 */
@Component(service = HttpServlet.class, configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true)
@NonNullByDefault
public class RachioWebHookServlet extends HttpServlet {
    private static final long serialVersionUID = -4654253998990066051L;
    private static final String WEBHOOK_SIGNATURE_HEADER = "x-signature";
    private final Logger logger = LoggerFactory.getLogger(RachioWebHookServlet.class);
    private final Gson gson = new Gson();

    private final HttpService httpService;
    private final RachioHandlerFactory rachioHandlerFactory;

    /**
     * OSGi activation callback.
     *
     * @param config Service config.
     */
    @Activate
    public RachioWebHookServlet(@Reference HttpService httpService,
            @Reference RachioHandlerFactory rachioHandlerFactory, Map<String, Object> config) {
        this.httpService = httpService;
        this.rachioHandlerFactory = rachioHandlerFactory;
        try {
            httpService.registerServlet(SERVLET_WEBHOOK_PATH, this, null, httpService.createDefaultHttpContext());
            logger.debug("RchioWebhook: Started servlet at {}", SERVLET_WEBHOOK_PATH);
        } catch (ServletException | NamespaceException e) {
            logger.warn("RchioWebhook: Could not start Rachio Webhook servlet", e);
        }
    }

    /**
     * OSGi deactivation callback.
     */
    @Deactivate
    protected void deactivate() {
        httpService.unregister(SERVLET_WEBHOOK_PATH);
        logger.debug("RachioWebHook: Servlet stopped");
    }

    @Override
    protected void service(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        if (request == null || resp == null) {
            return;
        }

        setHeaders(resp);

        String ipAddress = getClientIpAddress(request);
        @Nullable
        String path = request.getRequestURI();
        logger.trace("RachioWebhook: Request from {}:{}{} ({}:{}, {})", ipAddress, request.getRemotePort(), path,
                request.getRemoteHost(), request.getServerPort(), request.getProtocol());

        if (!SERVLET_WEBHOOK_PATH.equalsIgnoreCase(path)) {
            logger.debug("RachioWebHook: Invalid request received - path = {}", path);
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.debug("RachioWebHook: Invalid request received - method = {}", request.getMethod());
            resp.setHeader("Allow", "POST, OPTIONS");
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        byte[] rawBody = request.getInputStream().readAllBytes();
        @Nullable
        String signature = request.getHeader(WEBHOOK_SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            logger.warn("RachioWebHook: Rejecting webhook request from {} because the x-signature header is missing",
                    ipAddress);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (!rachioHandlerFactory.isValidWebHookSignature(signature, rawBody)) {
            logger.warn("RachioWebHook: Rejecting webhook request from {} because signature validation failed",
                    ipAddress);
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String data = new String(rawBody, StandardCharsets.UTF_8);
        try {
            logger.trace("RachioWebHook: Received {} byte webhook payload", rawBody.length);
            RachioEventGsonDTO event = parseEvent(data);
            event.normalize();
            logger.trace("RachioEvent {}.{} for device '{}': {}", event.category, event.type, event.deviceId,
                    event.summary);

            event.apiResult.setRateLimit(request.getHeader(RACHIO_JSON_RATE_LIMIT),
                    request.getHeader(RACHIO_JSON_RATE_REMAINING), request.getHeader(RACHIO_JSON_RATE_RESET));

            if (!rachioHandlerFactory.webHookEvent(ipAddress, event)) {
                logger.debug("RachioWebHook: Unable to route validated webhook event ({})", describeEvent(event));
            }
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("");
        } catch (JsonSyntaxException e) {
            logger.warn("RachioWebHook: Rejecting webhook request from {} because JSON parsing failed: {}", ipAddress,
                    e.getMessage());
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (RuntimeException e) {
            logger.debug("RachioWebHook: Exception processing validated webhook callback: {}", e.getMessage(), e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        @Nullable
        String ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ipAddress == null) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress != null ? ipAddress : "unknown";
    }

    private RachioEventGsonDTO parseEvent(String data) {
        try {
            return parseEventDirectly(data);
        } catch (JsonSyntaxException e) {
            @Nullable
            String legacyData = createLegacyJson(data);
            if (legacyData == null) {
                throw e;
            }
            logger.debug("RachioWebHook: Attempting legacy malformed webhook parser after direct JSON parsing failed");
            return parseEventDirectly(legacyData);
        }
    }

    private RachioEventGsonDTO parseEventDirectly(String data) {
        @Nullable
        RachioEventGsonDTO event = gson.fromJson(data, RachioEventGsonDTO.class);
        if (event == null) {
            throw new JsonSyntaxException("Webhook payload did not contain an event object");
        }
        return event;
    }

    private @Nullable String createLegacyJson(String data) {
        @Nullable
        String unwrappedJson = unwrapLegacyJsonString(data);
        if (unwrappedJson != null) {
            return unwrappedJson;
        }
        return convertLegacyStringifiedObjects(data);
    }

    private @Nullable String unwrapLegacyJsonString(String data) {
        try {
            JsonElement root = JsonParser.parseString(data);
            if (!root.isJsonPrimitive()) {
                return null;
            }
            JsonPrimitive primitive = root.getAsJsonPrimitive();
            if (!primitive.isString()) {
                return null;
            }
            String value = primitive.getAsString().trim();
            return looksLikeJsonObject(value) ? value : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private @Nullable String convertLegacyStringifiedObjects(String data) {
        try {
            JsonElement root = JsonParser.parseString(data);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject normalized = root.getAsJsonObject().deepCopy();
            boolean changed = replaceStringifiedObject(normalized, "payload");
            changed |= replaceStringifiedObject(normalized, "network");
            changed |= replaceStringifiedObject(normalized, "zoneRunStatus");
            changed |= replaceStringifiedObject(normalized, "eventParms");
            changed |= replaceStringifiedObject(normalized, "deltaProperties");
            return changed ? gson.toJson(normalized) : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private boolean replaceStringifiedObject(JsonObject object, String memberName) {
        JsonElement member = object.get(memberName);
        if (member == null || !member.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = member.getAsJsonPrimitive();
        if (!primitive.isString()) {
            return false;
        }
        String value = primitive.getAsString().trim();
        if (!looksLikeJsonObject(value)) {
            return false;
        }

        try {
            JsonElement parsed = JsonParser.parseString(value);
            if (!parsed.isJsonObject()) {
                return false;
            }
            object.add(memberName, parsed);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private boolean looksLikeJsonObject(String value) {
        return value.startsWith("{") && value.endsWith("}");
    }

    private String describeEvent(RachioEventGsonDTO event) {
        return "eventId=" + printable(event.eventId) + ", eventType=" + printable(event.eventType) + ", resourceType="
                + printable(event.resourceType) + ", resourceId=" + printable(event.resourceId) + ", deviceId="
                + printable(event.deviceId);
    }

    private String printable(String value) {
        return value.isEmpty() ? "n/a" : value;
    }

    private void setHeaders(HttpServletResponse response) {
        response.setCharacterEncoding(SERVLET_WEBHOOK_CHARSET);
        response.setContentType(SERVLET_WEBHOOK_APPLICATION_JSON);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers",
                "Origin, X-Requested-With, Content-Type, Accept, " + WEBHOOK_SIGNATURE_HEADER);
    }
}
