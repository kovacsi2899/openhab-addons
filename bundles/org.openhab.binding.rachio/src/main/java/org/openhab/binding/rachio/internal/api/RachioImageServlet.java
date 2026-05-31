/*
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

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RachioImageServlet} Rachio sometimes returns an incorrect media type for images. This servlet is a work around
 * for that. It rewrites the image url to point to the binding and adds the correct media type before returning the url
 * to the channel/item.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(service = {}, configurationPolicy = ConfigurationPolicy.OPTIONAL, immediate = true)
public class RachioImageServlet extends HttpServlet {
    private static final long serialVersionUID = 8706067059503685993L;
    private static final String HTTP_METHOD_GET = "GET";
    private final Logger logger = LoggerFactory.getLogger(RachioImageServlet.class);

    private final Object registrationLock = new Object();
    private @Nullable HttpService httpService;
    private boolean servletRegistered;

    /**
     * OSGi HttpService bind callback.
     *
     * @param httpService the HTTP service used for manual servlet registration
     */
    @Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY)
    protected void bindHttpService(HttpService httpService) {
        synchronized (registrationLock) {
            if (Objects.equals(this.httpService, httpService) && servletRegistered) {
                logger.debug("RachioImage: Image servlet already registered at {}, skipping duplicate bind",
                        SERVLET_IMAGE_PATH);
                return;
            }
            if (servletRegistered) {
                unregisterServletLocked();
            }

            this.httpService = httpService;
            registerServletLocked(httpService);
        }
    }

    protected void unbindHttpService(HttpService httpService) {
        synchronized (registrationLock) {
            if (!Objects.equals(this.httpService, httpService)) {
                logger.debug("RachioImage: Ignoring HttpService unbind for non-current service");
                return;
            }

            unregisterServletLocked();
            this.httpService = null;
        }
    }

    /**
     * OSGi deactivation callback.
     */
    @Deactivate
    protected void deactivate() {
        synchronized (registrationLock) {
            unregisterServletLocked();
            httpService = null;
        }
    }

    private void registerServletLocked(HttpService httpService) {
        try {
            logger.debug("RachioImage: Registering image servlet alias {}", SERVLET_IMAGE_PATH);
            httpService.registerServlet(SERVLET_IMAGE_PATH, this, null, httpService.createDefaultHttpContext());
            servletRegistered = true;
        } catch (ServletException | NamespaceException e) {
            servletRegistered = false;
            logger.warn("RachioImage: Could not register image servlet alias {}: {}", SERVLET_IMAGE_PATH,
                    e.getMessage());
        }
    }

    private void unregisterServletLocked() {
        HttpService currentHttpService = httpService;
        if (!servletRegistered || currentHttpService == null) {
            return;
        }

        try {
            logger.debug("RachioImage: Unregistering image servlet alias {}", SERVLET_IMAGE_PATH);
            currentHttpService.unregister(SERVLET_IMAGE_PATH);
        } catch (IllegalArgumentException e) {
            logger.debug("RachioImage: Image servlet alias {} was already unregistered", SERVLET_IMAGE_PATH);
        } finally {
            servletRegistered = false;
        }
    }

    @Override
    protected void service(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        if ((request == null) || (resp == null)) {
            return;
        }

        InputStream reader = null;
        OutputStream writer = null;
        try {
            String ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (ipAddress == null) {
                ipAddress = request.getRemoteAddr();
            }
            @Nullable
            String requestUri = request.getRequestURI();
            logger.trace("RachioImage: Request from {}:{} ({}:{}, {})", ipAddress, request.getRemotePort(),
                    request.getRemoteHost(), request.getServerPort(), request.getProtocol());
            String method = request.getMethod();
            if (!HTTP_METHOD_GET.equalsIgnoreCase(method)) {
                logger.warn("RachioImage: Unexpected method='{}'", method);
            }
            if (!isImageServletPath(requestUri)) {
                logger.debug("RachioImage: Ignoring request outside image servlet path");
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            @Nullable
            String imagePath = normalizeImagePath(request.getPathInfo());
            if (imagePath == null) {
                logger.debug("Ignoring Rachio image request without image path");
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            logger.debug("RachioImage: Serving image request: method={}, pathLength={}", method, imagePath.length());
            setHeaders(resp);
            URLConnection conn = openImageConnection(imagePath);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            reader = conn.getInputStream();
            writer = resp.getOutputStream();

            // read data in 4k chunks
            byte[] data = new byte[4096];
            int n;
            while (((n = reader.read(data)) != -1)) {
                writer.write(data, 0, n);
            }
        } catch (RuntimeException e) {
            logger.debug("RachioImage: Unable to process request: {}", e.getMessage());
        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    private boolean isImageServletPath(@Nullable String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if (requestUri.length() == SERVLET_IMAGE_PATH.length()) {
            return SERVLET_IMAGE_PATH.equalsIgnoreCase(requestUri);
        }
        return requestUri.length() > SERVLET_IMAGE_PATH.length()
                && requestUri.regionMatches(true, 0, SERVLET_IMAGE_PATH, 0, SERVLET_IMAGE_PATH.length())
                && requestUri.charAt(SERVLET_IMAGE_PATH.length()) == '/';
    }

    static @Nullable String normalizeImagePath(@Nullable String pathInfo) {
        if (pathInfo == null) {
            return null;
        }
        String normalized = pathInfo.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        int lastSeparator = normalized.lastIndexOf('/');
        if (lastSeparator >= 0) {
            normalized = normalized.substring(lastSeparator + 1).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    protected URLConnection openImageConnection(String imagePath) throws IOException {
        return new URL(SERVLET_IMAGE_URL_BASE + imagePath).openConnection();
    }

    private void setHeaders(HttpServletResponse response) {
        response.setContentType(SERVLET_IMAGE_MIME_TYPE);
        response.setHeader("Access-Control-Allow-Origin", "*");
    }
}
