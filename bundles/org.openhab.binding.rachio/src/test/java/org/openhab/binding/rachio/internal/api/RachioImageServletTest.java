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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.SERVLET_IMAGE_MIME_TYPE;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.SERVLET_IMAGE_PATH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests Rachio image servlet request handling.
 *
 * @author openHAB Contributors - Initial contribution
 */
@NonNullByDefault
@SuppressWarnings({ "null" })
class RachioImageServletTest {
    @Test
    void slashImagePathReturnsNotFoundWithoutDownload() throws Exception {
        TestImageServlet servlet = new TestImageServlet();
        HttpServletResponse response = response();

        servlet.service(request(SERVLET_IMAGE_PATH + "/", "/"), response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
        verify(response, never()).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response, never()).getOutputStream();
        assertThat(servlet.downloadCount, is(0));
    }

    @Test
    void nullImagePathReturnsNotFoundWithoutDownload() throws Exception {
        TestImageServlet servlet = new TestImageServlet();
        HttpServletResponse response = response();

        servlet.service(request(SERVLET_IMAGE_PATH, null), response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
        verify(response, never()).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response, never()).getOutputStream();
        assertThat(servlet.downloadCount, is(0));
    }

    @Test
    void emptyImagePathReturnsNotFoundWithoutDownload() throws Exception {
        TestImageServlet servlet = new TestImageServlet();
        HttpServletResponse response = response();

        servlet.service(request(SERVLET_IMAGE_PATH, ""), response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
        verify(response, never()).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response, never()).getOutputStream();
        assertThat(servlet.downloadCount, is(0));
    }

    @Test
    void blankImagePathReturnsNotFoundWithoutDownload() throws Exception {
        TestImageServlet servlet = new TestImageServlet();
        HttpServletResponse response = response();

        servlet.service(request(SERVLET_IMAGE_PATH + "/%20%20", "/   "), response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
        verify(response, never()).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response, never()).getOutputStream();
        assertThat(servlet.downloadCount, is(0));
    }

    @Test
    void validImagePathDownloadsImage() throws Exception {
        TestImageServlet servlet = new TestImageServlet();
        CapturingServletOutputStream outputStream = new CapturingServletOutputStream();
        HttpServletResponse response = response(outputStream);

        servlet.service(request(SERVLET_IMAGE_PATH + "/photo-id", "/photo-id"), response);

        verify(response, never()).sendError(HttpServletResponse.SC_NOT_FOUND);
        verify(response, never()).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response).setContentType(SERVLET_IMAGE_MIME_TYPE);
        verify(response).setHeader("Access-Control-Allow-Origin", "*");
        assertThat(servlet.downloadCount, is(1));
        assertThat(servlet.imagePath, is("photo-id"));
        assertThat(outputStream.contents(), is("image-bytes"));
    }

    @Test
    void nestedImagePathUsesLastSegmentLikeExistingProxyPath() {
        assertThat(RachioImageServlet.normalizeImagePath("/parent/photo-id"), is("photo-id"));
    }

    private HttpServletRequest request(String requestUri, @Nullable String pathInfo) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(requestUri);
        when(request.getPathInfo()).thenReturn(pathInfo);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRemotePort()).thenReturn(8080);
        when(request.getRemoteHost()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        return request;
    }

    private HttpServletResponse response() throws IOException {
        return response(new CapturingServletOutputStream());
    }

    private HttpServletResponse response(ServletOutputStream outputStream) throws IOException {
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        return response;
    }

    private static class TestImageServlet extends RachioImageServlet {
        private static final long serialVersionUID = 1937861124798153395L;

        private int downloadCount;
        private String imagePath = "";

        @Override
        protected URLConnection openImageConnection(String imagePath) throws IOException {
            downloadCount++;
            this.imagePath = imagePath;
            return new TestURLConnection(URI.create("https://example.invalid/" + imagePath).toURL());
        }
    }

    private static class TestURLConnection extends URLConnection {
        TestURLConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream("image-bytes".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class CapturingServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            outputStream.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(@Nullable WriteListener writeListener) {
        }

        String contents() {
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
