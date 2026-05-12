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

import static java.net.HttpURLConnection.*;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;
import static org.openhab.binding.rachio.internal.RachioUtils.getString;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RachioHttp} implements the http-based REST API to access the Rachio Cloud
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioHttp {
    private final Logger logger = LoggerFactory.getLogger(RachioHttp.class);
    private static final String HTTP_METHOD_DELETE = "DELETE";
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";
    private static final String HTTP_METHOD_PUT = "PUT";

    private int apiCalls = 0;
    private String apikey = "";

    public RachioHttp() {
    }

    /**
     * Constructor for the Rachio API class to create a connection to the Rachio cloud service.
     *
     * @param key Rachio API Access token (see Web UI)
     * @throws Exception
     */
    public RachioHttp(final String key) {
        apikey = key;
    }

    /**
     * Given a URL and a set parameters, send a HTTP GET request to the URL location created by the URL and parameters.
     *
     * @param url The URL to send a GET request to.
     * @param urlParameters List of parameters to use in the URL for the GET request. Null if no parameters.
     * @return RachioApiResult including GET response, http code etc.
     * @throws Exception
     */
    public RachioApiResult httpGet(String url, @Nullable String urlParameters) throws RachioApiException {
        return httpRequest(HTTP_METHOD_GET, url, urlParameters, null);
    }

    /**
     * Given a URL and a set parameters, send a HTTP POST request to the URL location created by the URL and parameters.
     *
     * @param url The URL to send a POST request to.
     * @param urlParameters List of parameters to use in the URL for the POST request. Null if no parameters.
     * @return RachioApiResult including GET response, http code etc.
     * @throws Exception
     */
    public RachioApiResult httpPut(String url, String putData) throws RachioApiException {
        return httpRequest(HTTP_METHOD_PUT, url, null, putData);
    }

    /**
     * Given a URL and a set parameters, send a HTTP POST request to the URL location created by the URL and parameters.
     *
     * @param url The URL to send a POST request to.
     * @param postData List of parameters to use in the URL for the POST request. Null if no parameters.
     * @return RachioApiResult including GET response, http code etc.
     * @throws Exception
     */
    public RachioApiResult httpPost(String url, String postData) throws RachioApiException {
        return httpRequest(HTTP_METHOD_POST, url, null, postData);
    }

    /**
     * Given a URL and a set parameters, send a HTTP GET request to the URL location created by the URL and parameters.
     *
     * @param url The URL to send a GET request to.
     * @param urlParameters List of parameters to use in the URL for the GET request. Null if no parameters.
     * @return RachioApiResult including GET response, http code etc.
     * @throws Exception if something went wrong (e.g. unable to connect)
     */
    public RachioApiResult httpDelete(String url, @Nullable String urlParameters) throws RachioApiException {
        return httpRequest(HTTP_METHOD_DELETE, url, urlParameters, null);
    }

    /**
     * Given a URL and a set parameters, send a HTTP GET request to the URL location created by the URL and parameters.
     *
     * @param url The URL to send a GET request to.
     * @param urlParameters List of parameters to use in the URL for the GET request. Null if no parameters.
     * @return RachioApiResult including GET response, http code etc.
     * @throws Exception
     */
    protected RachioApiResult httpRequest(String method, String url, @Nullable String urlParameters,
            @Nullable String reqDatas) throws RachioApiException {

        RachioApiResult result = new RachioApiResult();
        try {
            apiCalls++;

            URL location = null;
            if (urlParameters != null) {
                location = new URL(url + "?" + urlParameters);
            } else {
                location = new URL(url);
            }
            result.requestMethod = method;
            result.url = location.toString();
            result.apiCalls = apiCalls;

            HttpURLConnection request = (HttpURLConnection) location.openConnection();
            if (!apikey.isEmpty()) {
                request.setRequestProperty("Authorization", "Bearer " + apikey);
                result.apikey = apikey;
            }
            request.setRequestMethod(method);
            request.setConnectTimeout(15000); // set timeout to 15 seconds
            request.setRequestProperty("User-Agent", SERVLET_WEBHOOK_USER_AGENT);
            request.setRequestProperty("Content-Type", SERVLET_WEBHOOK_APPLICATION_JSON);
            logger.trace("RachioHttp[Call #{}]: Call Rachio cloud service: {} '{}')", apiCalls,
                    request.getRequestMethod(), result.url);
            if (method.equals(HTTP_METHOD_PUT) || method.equals(HTTP_METHOD_POST)) {
                request.setDoOutput(true);
                DataOutputStream wr = new DataOutputStream(request.getOutputStream());
                wr.write(reqDatas != null ? reqDatas.getBytes(StandardCharsets.UTF_8) : new byte[0]);
                wr.flush();
                wr.close();
            }
            StringBuilder response = new StringBuilder();

            result.responseCode = request.getResponseCode();
            if (request.getHeaderField(RACHIO_JSON_RATE_LIMIT) != null) {
                result.setRateLimit(request.getHeaderField(RACHIO_JSON_RATE_LIMIT),
                        request.getHeaderField(RACHIO_JSON_RATE_REMAINING),
                        request.getHeaderField(RACHIO_JSON_RATE_RESET));
                if (result.isRateLimitBlocked()) {
                    String message = MessageFormat.format("RachioHttp: Critcal API rate limit: {0} / {1}, reset at {2}",
                            result.rateRemaining, result.rateLimit, result.rateReset);
                    throw new RachioApiException(message, result);
                }
            }

            if ((result.responseCode < HTTP_OK) || (result.responseCode >= HTTP_MULT_CHOICE)) {
                result.resultString = readResponse(request.getErrorStream());
                String message = MessageFormat.format(
                        "RachioHttp: Error sending HTTP {0} request to {1} - http response code={2}, response={3}",
                        request.getRequestMethod(), url, result.responseCode, result.resultString);
                throw new RachioApiException(message, result);
            }

            InputStream responseStream = request.getInputStream();
            if (responseStream != null) {
                response.append(readResponse(responseStream));
            }

            result.resultString = response.toString();
            logger.trace("RachioHttp: {} {} - Response='{}'", request.getRequestMethod(), url, result.resultString);

            return result;
        } catch (RuntimeException | IOException e) {
            result.resultString = getString(e.toString());
            if (result.resultString.contains("Server returned HTTP response code: 429")) {
                result.responseCode = HttpStatus.TOO_MANY_REQUESTS_429;
            }
            throw new RachioApiException(result.resultString, result, e);
        }
    }

    private String readResponse(@Nullable InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
        }
        return response.toString();
    }
}
