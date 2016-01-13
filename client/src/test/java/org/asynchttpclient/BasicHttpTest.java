/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.asynchttpclient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.asynchttpclient.Dsl.*;
import static org.asynchttpclient.test.EventCollectingHandler.*;
import static org.asynchttpclient.test.TestUtils.*;
import static org.asynchttpclient.util.DateUtils.millisTime;
import static org.testng.Assert.*;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.asynchttpclient.cookie.Cookie;
import org.asynchttpclient.handler.MaxRedirectException;
import org.asynchttpclient.request.body.multipart.Part;
import org.asynchttpclient.request.body.multipart.StringPart;
import org.asynchttpclient.test.EventCollectingHandler;
import org.asynchttpclient.test.TestUtils;
import org.asynchttpclient.testserver.HttpServer;
import org.eclipse.jetty.client.HttpSender;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BasicHttpTest {

    private static HttpServer server;

    @BeforeClass
    public static void start() throws Exception {
        server = new HttpServer();
        server.start();
    }

    @AfterClass
    public static void stop() throws Exception {
        server.close();
    }

    private static String getTargetUrl() {
        return server.getUrl() + "/foo/bar";
    }

    @Test
    public void getRootUrl() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            Request request = get(server.getUrl()).build();

            server.enqueueOk();
            String url = client.executeRequest(request, new AsyncCompletionHandler<String>() {
                @Override
                public String onCompleted(Response response) throws Exception {
                    return response.getUri().toString();
                }

                @Override
                public void onThrowable(Throwable t) {
                    fail("Unexpected exception: " + t.getMessage(), t);
                }

            }).get(TIMEOUT, TimeUnit.SECONDS);
            assertEquals(url, server.getUrl());
        }
    }

    @Test
    public void getUrlWithPathWithoutQuery() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            Request request = get(getTargetUrl()).build();

            server.enqueueOk();
            String url = client.executeRequest(request, new AsyncCompletionHandler<String>() {
                @Override
                public String onCompleted(Response response) throws Exception {
                    return response.getUri().toString();
                }

                @Override
                public void onThrowable(Throwable t) {
                    fail("Unexpected exception: " + t.getMessage(), t);
                }

            }).get(TIMEOUT, TimeUnit.SECONDS);
            assertEquals(url, getTargetUrl());
        }
    }

    @Test
    public void getUrlWithPathWithQuery() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            String targetUrl = getTargetUrl() + "?q=+%20x";

            Request request = get(targetUrl).build();
            assertEquals(request.getUrl(), targetUrl);

            server.enqueueOk();
            String url = client.executeRequest(request, new AsyncCompletionHandler<String>() {
                @Override
                public String onCompleted(Response response) throws Exception {
                    return response.getUri().toString();
                }

                @Override
                public void onThrowable(Throwable t) {
                    fail("Unexpected exception: " + t.getMessage(), t);
                }

            }).get(TIMEOUT, TimeUnit.SECONDS);
            assertEquals(url, targetUrl);
        }
    }

    @Test
    public void getUrlWithPathWithQueryParams() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            Request request = get(getTargetUrl()).addQueryParam("q", "a b").build();

            server.enqueueOk();
            String url = client.executeRequest(request, new AsyncCompletionHandler<String>() {
                @Override
                public String onCompleted(Response response) throws Exception {
                    return response.getUri().toString();
                }

                @Override
                public void onThrowable(Throwable t) {
                    fail("Unexpected exception: " + t.getMessage(), t);
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
            assertEquals(url, getTargetUrl() + "?q=a%20b");
        }
    }

    @Test
    public void getResponseBody() throws Exception {

        try (AsyncHttpClient client = asyncHttpClient()) {

            Request request = get(getTargetUrl()).build();
            final String body = "Hello World";

            server.enqueueResponse(response -> {
                response.setStatus(200);
                response.setContentType(TEXT_HTML_CONTENT_TYPE_WITH_UTF_8_CHARSET);
                print(response, body);
            });

            client.executeRequest(request, new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    String contentLengthHeader = response.getHeader(HttpHeaders.Names.CONTENT_LENGTH);
                    assertNotNull(contentLengthHeader);
                    assertEquals(Integer.parseInt(contentLengthHeader), body.length());
                    assertContentTypesEquals(response.getContentType(), TEXT_HTML_CONTENT_TYPE_WITH_UTF_8_CHARSET);
                    assertEquals(response.getResponseBody(), body);
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void getWithHeaders() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            HttpHeaders h = new DefaultHttpHeaders();
            for (int i = 1; i < 5; i++) {
                h.add("Test" + i, "Test" + i);
            }
            Request request = get(getTargetUrl()).setHeaders(h).build();

            server.enqueueEcho();

            client.executeRequest(request, new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    for (int i = 1; i < 5; i++) {
                        assertEquals(response.getHeader("X-Test" + i), "Test" + i);
                    }
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void postWithHeadersAndFormParams() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            HttpHeaders h = new DefaultHttpHeaders();
            h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);

            Map<String, List<String>> m = new HashMap<>();
            for (int i = 0; i < 5; i++) {
                m.put("param_" + i, Arrays.asList("value_" + i));
            }

            Request request = post(getTargetUrl()).setHeaders(h).setFormParams(m).build();

            server.enqueueEcho();

            client.executeRequest(request, new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    for (int i = 1; i < 5; i++) {
                        assertEquals(response.getHeader("X-param_" + i), "value_" + i);
                    }
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void headHasEmptyBody() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            Request request = head(getTargetUrl()).build();

            server.enqueueOk();
            Response response = client.executeRequest(request, new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);

            assertTrue(response.getResponseBody().isEmpty());
        }
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void nullSchemeThrowsNPE() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            client.prepareGet("gatling.io").execute();
        }
    }

    @Test
    public void jettyRespondsWithChunked() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            server.enqueueEcho();
            client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    assertEquals(response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING), HttpHeaders.Values.CHUNKED);
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void getWithCookies() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            final Cookie coo = Cookie.newValidCookie("foo", "value", false, "/", "/", Long.MIN_VALUE, false, false);
            server.enqueueEcho();
            client.prepareGet(getTargetUrl()).addCookie(coo).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    List<Cookie> cookies = response.getCookies();
                    assertEquals(cookies.size(), 1);
                    assertEquals(cookies.get(0).toString(), "foo=value");
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void formContentTypeIsAutomaticallyAdded() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            server.enqueueEcho();
            client.preparePost(getTargetUrl()).addFormParam("foo", "bar").execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    HttpHeaders h = response.getHeaders();
                    assertEquals(h.get("X-" + HttpHeaders.Names.CONTENT_TYPE), HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void defaultBodyEncodingIsIso() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            server.enqueueEcho();
            Response response = client.preparePost(getTargetUrl()).setBody("\u017D\u017D\u017D\u017D\u017D\u017D").execute().get();
            assertEquals(response.getResponseBodyAsBytes(), "\u017D\u017D\u017D\u017D\u017D\u017D".getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    @Test
    public void postFormParametersAsBodyString() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            HttpHeaders h = new DefaultHttpHeaders();
            h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append("param_").append(i).append("=value_").append(i).append("&");
            }
            sb.setLength(sb.length() - 1);

            server.enqueueEcho();
            client.preparePost(getTargetUrl()).setHeaders(h).setBody(sb.toString()).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    for (int i = 1; i < 5; i++) {
                        assertEquals(response.getHeader("X-param_" + i), "value_" + i);

                    }
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void postFormParametersAsBodyStream() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            HttpHeaders h = new DefaultHttpHeaders();
            h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append("param_").append(i).append("=value_").append(i).append("&");
            }
            sb.setLength(sb.length() - 1);

            ByteArrayInputStream is = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));

            server.enqueueEcho();
            client.preparePost(getTargetUrl()).setHeaders(h).setBody(is).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    for (int i = 1; i < 5; i++) {
                        assertEquals(response.getHeader("X-param_" + i), "value_" + i);

                    }
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void putFormParametersAsBodyStream() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            HttpHeaders h = new DefaultHttpHeaders();
            h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append("param_").append(i).append("=value_").append(i).append("&");
            }
            sb.setLength(sb.length() - 1);
            ByteArrayInputStream is = new ByteArrayInputStream(sb.toString().getBytes());

            server.enqueueEcho();
            client.preparePut(getTargetUrl()).setHeaders(h).setBody(is).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    assertEquals(response.getStatusCode(), 200);
                    for (int i = 1; i < 5; i++) {
                        assertEquals(response.getHeader("X-param_" + i), "value_" + i);
                    }
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test
    public void postSingleStringPart() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            server.enqueueEcho();
            client.preparePost(getTargetUrl()).addBodyPart(new StringPart("foo", "bar")).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    String xContentType = response.getHeader("X-Content-Type");
                    String boundary = xContentType.substring((xContentType.indexOf("boundary") + "boundary".length() + 1));
                    assertTrue(response.getResponseBody().regionMatches(false, "--".length(), boundary, 0, boundary.length()));
                    return response;
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    // @Test
    // public void asyncDoPostProxyTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient(config().setProxyServer(proxyServer("localhost", port2).build()))) {
    // HttpHeaders h = new DefaultHttpHeaders();
    // h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
    // StringBuilder sb = new StringBuilder();
    // for (int i = 0; i < 5; i++) {
    // sb.append("param_").append(i).append("=value_").append(i).append("&");
    // }
    // sb.setLength(sb.length() - 1);
    //
    // Response response = client.preparePost(getTargetUrl()).setHeaders(h).setBody(sb.toString()).execute(new AsyncCompletionHandler<Response>() {
    // @Override
    // public Response onCompleted(Response response) throws Exception {
    // return response;
    // }
    //
    // @Override
    // public void onThrowable(Throwable t) {
    // }
    // }).get();
    //
    // assertEquals(response.getStatusCode(), 200);
    // assertEquals(response.getHeader("X-" + HttpHeaders.Names.CONTENT_TYPE), HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
    // }
    // }

    @Test
    public void getVirtualHost() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {

            String virtualHost = "localhost:" + server.getPort();

            Request request = get(getTargetUrl()).setVirtualHost(virtualHost).build();

            server.enqueueEcho();
            Response response = client.executeRequest(request, new AsyncCompletionHandlerAdapter()).get();

            assertEquals(response.getStatusCode(), 200);
            assertEquals(response.getHeader("X-" + HttpHeaders.Names.HOST), virtualHost);
        }
    }

    @Test(expectedExceptions = CancellationException.class)
    public void cancelledFutureThrowsCancellationException() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            HttpHeaders headers = new DefaultHttpHeaders();
            headers.add("X-Delay", 5_000);

            server.enqueueEcho();
            Future<Response> future = client.prepareGet(getTargetUrl()).setHeaders(headers).execute(new AsyncCompletionHandlerAdapter() {
                @Override
                public void onThrowable(Throwable t) {
                }
            });
            future.cancel(true);
            future.get(TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void futureTimeOutThrowsTimeoutException() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            HttpHeaders headers = new DefaultHttpHeaders();
            headers.add("X-Delay", 5_000);

            server.enqueueEcho();
            Future<Response> future = client.prepareGet(getTargetUrl()).setHeaders(headers).execute(new AsyncCompletionHandlerAdapter() {
                @Override
                public void onThrowable(Throwable t) {
                }
            });

            future.get(2, TimeUnit.SECONDS);
        }
    }

    @Test(expectedExceptions = ConnectException.class)
    public void connectFailureThrowsConnectException() throws Throwable {
        try (AsyncHttpClient client = asyncHttpClient()) {
            int dummyPort = findFreePort();
            try {
                client.preparePost(String.format("http://localhost:%d/", dummyPort)).execute(new AsyncCompletionHandlerAdapter() {
                    @Override
                    public void onThrowable(Throwable t) {
                    }
                }).get(TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                throw ex.getCause();
            }
        }
    }

    @Test
    public void connectFailureNotifiesHandlerWithConnectException() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            final CountDownLatch l = new CountDownLatch(1);
            int port = findFreePort();

            client.prepareGet(String.format("http://localhost:%d/", port)).execute(new AsyncCompletionHandlerAdapter() {
                @Override
                public void onThrowable(Throwable t) {
                    try {
                        assertTrue(t instanceof ConnectException);
                    } finally {
                        l.countDown();
                    }
                }
            });

            if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
                fail("Timed out");
            }
        }
    }

    @Test(groups = "online", expectedExceptions = UnknownHostException.class)
    public void unknownHostThrowsUnknownHostException() throws Throwable {
        try (AsyncHttpClient client = asyncHttpClient()) {

            try {
                client.prepareGet("http://null.gatling.io").execute(new AsyncCompletionHandlerAdapter() {
                    @Override
                    public void onThrowable(Throwable t) {
                    }
                }).get(TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }
    }

    @Test
    public void getEmptyBody() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            server.enqueueOk();
            Response response = client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter()).get(TIMEOUT, TimeUnit.SECONDS);
            assertTrue(response.getResponseBody().isEmpty());
        }
    }

    @Test
    public void getEmptyBodyNotifiesHandler() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            // Use a l in case the assert fail
            final CountDownLatch l = new CountDownLatch(1);

            server.enqueueOk();
            client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter() {

                @Override
                public Response onCompleted(Response response) throws Exception {
                    try {
                        assertEquals(response.getStatusCode(), 200);
                    } finally {
                        l.countDown();
                    }
                    return response;
                }
            });

            if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
                fail("Timed out");
            }
        }
    }

    @Test
    public void exceptionInOnCompletedGetNotifiedToOnThrowable() throws Exception {
        try (AsyncHttpClient client = asyncHttpClient()) {
            final CountDownLatch l = new CountDownLatch(1);
            final AtomicReference<String> message = new AtomicReference<String>();

            server.enqueueOk();
            client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter() {
                @Override
                public Response onCompleted(Response response) throws Exception {
                    throw new IllegalStateException("FOO");
                }

                @Override
                public void onThrowable(Throwable t) {
                    message.set(t.getMessage());
                    l.countDown();
                }
            });

            if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
                fail("Timed out");
            }

            assertEquals(message.get(), "FOO");
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void exceptionInOnCompletedGetNotifiedToFuture() throws Throwable {
        try (AsyncHttpClient client = asyncHttpClient()) {

            server.enqueueOk();
            Future<Response> future = client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter() {
                @Override
                public Response onCompleted(Response response) throws Exception {
                    throw new IllegalStateException("FOO");
                }

                @Override
                public void onThrowable(Throwable t) {
                }
            });

            try {
                future.get(TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw e.getCause();
            }
        }
    }

    //
    // @Test
    // public void asyncDoGetDelayHandlerTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient(config().setRequestTimeout(5 * 1000))) {
    // HttpHeaders h = new DefaultHttpHeaders();
    // h.add("LockThread", "true");
    //
    // // Use a l in case the assert fail
    // final CountDownLatch l = new CountDownLatch(1);
    //
    // client.prepareGet(getTargetUrl()).setHeaders(h).execute(new AsyncCompletionHandlerAdapter() {
    //
    // @Override
    // public Response onCompleted(Response response) throws Exception {
    // try {
    // fail("Must not receive a response");
    // } finally {
    // l.countDown();
    // }
    // return response;
    // }
    //
    // @Override
    // public void onThrowable(Throwable t) {
    // try {
    // if (t instanceof TimeoutException) {
    // assertTrue(true);
    // } else {
    // fail("Unexpected exception", t);
    // }
    // } finally {
    // l.countDown();
    // }
    // }
    // });
    //
    // if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
    // fail("Timed out");
    // }
    // }
    // }
    //
    // @Test
    // public void asyncDoGetQueryStringTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // // Use a l in case the assert fail
    // final CountDownLatch l = new CountDownLatch(1);
    //
    // AsyncCompletionHandler<Response> handler = new AsyncCompletionHandlerAdapter() {
    //
    // @Override
    // public Response onCompleted(Response response) throws Exception {
    // try {
    // assertTrue(response.getHeader("X-pathInfo") != null);
    // assertTrue(response.getHeader("X-queryString") != null);
    // } finally {
    // l.countDown();
    // }
    // return response;
    // }
    // };
    //
    // Request req = get(getTargetUrl() + "?foo=bar").build();
    //
    // client.executeRequest(req, handler).get();
    //
    // if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
    // fail("Timed out");
    // }
    // }
    // }
    //
    // @Test
    // public void asyncDoGetKeepAliveHandlerTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // // Use a l in case the assert fail
    // final CountDownLatch l = new CountDownLatch(2);
    //
    // AsyncCompletionHandler<Response> handler = new AsyncCompletionHandlerAdapter() {
    //
    // String remoteAddr = null;
    //
    // @Override
    // public Response onCompleted(Response response) throws Exception {
    // try {
    // assertEquals(response.getStatusCode(), 200);
    // if (remoteAddr == null) {
    // remoteAddr = response.getHeader("X-KEEP-ALIVE");
    // } else {
    // assertEquals(response.getHeader("X-KEEP-ALIVE"), remoteAddr);
    // }
    // } finally {
    // l.countDown();
    // }
    // return response;
    // }
    // };
    //
    // client.prepareGet(getTargetUrl()).execute(handler).get();
    // client.prepareGet(getTargetUrl()).execute(handler);
    //
    // if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
    // fail("Timed out");
    // }
    // }
    // }
    //
    // @Test(groups = "online")
    // public void asyncDoGetMaxRedirectTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient(config().setMaxRedirects(0).setFollowRedirect(true))) {
    // // Use a l in case the assert fail
    // final CountDownLatch l = new CountDownLatch(1);
    //
    // AsyncCompletionHandler<Response> handler = new AsyncCompletionHandlerAdapter() {
    //
    // @Override
    // public Response onCompleted(Response response) throws Exception {
    // fail("Should not be here");
    // return response;
    // }
    //
    // @Override
    // public void onThrowable(Throwable t) {
    // t.printStackTrace();
    // try {
    // assertEquals(t.getClass(), MaxRedirectException.class);
    // } finally {
    // l.countDown();
    // }
    // }
    // };
    //
    // client.prepareGet("http://google.com").execute(handler);
    //
    // if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
    // fail("Timed out");
    // }
    // }
    // }
    //
    // @Test(groups = "online")
    // public void asyncDoGetNestedTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // // FIXME find a proper website that redirects the same number of
    // // times whatever the language
    // // Use a l in case the assert fail
    // final CountDownLatch l = new CountDownLatch(2);
    //
    // final AsyncCompletionHandlerAdapter handler = new AsyncCompletionHandlerAdapter() {
    //
    // private final static int MAX_NESTED = 2;
    //
    // private AtomicInteger nestedCount = new AtomicInteger(0);
    //
    // @Override
    // public Response onCompleted(Response response) throws Exception {
    // try {
    // if (nestedCount.getAndIncrement() < MAX_NESTED) {
    // System.out.println("Executing a nested request: " + nestedCount);
    // client.prepareGet("http://www.lemonde.fr").execute(this);
    // }
    // } finally {
    // l.countDown();
    // }
    // return response;
    // }
    //
    // @Override
    // public void onThrowable(Throwable t) {
    // t.printStackTrace();
    // }
    // };
    //
    // client.prepareGet("http://www.lemonde.fr").execute(handler);
    //
    // if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
    // fail("Timed out");
    // }
    // }
    // }
    //
    // @Test(groups = "online")
    // public void asyncDoGetStreamAndBodyTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // Response response = client.prepareGet("http://www.lemonde.fr").execute().get();
    // assertEquals(response.getStatusCode(), 200);
    // }
    // }
    //
    // @Test(groups = "online")
    // public void asyncUrlWithoutPathTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // Response response = client.prepareGet("http://www.lemonde.fr").execute().get();
    // assertEquals(response.getStatusCode(), 200);
    // }
    // }
    //
    // @Test
    // public void optionsTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // Response response = client.prepareOptions(getTargetUrl()).execute().get();
    //
    // assertEquals(response.getStatusCode(), 200);
    // assertEquals(response.getHeader("Allow"), "GET,HEAD,POST,OPTIONS,TRACE");
    // }
    // }
    //
    // @Test(groups = "online")
    // public void testAwsS3() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // Response response = client.prepareGet("http://test.s3.amazonaws.com/").execute().get();
    // if (response.getResponseBody() == null || response.getResponseBody().equals("")) {
    // fail("No response Body");
    // } else {
    // assertEquals(response.getStatusCode(), 403);
    // }
    // }
    // }
    //
    // @Test(groups = "online")
    // public void testAsyncHttpProviderConfig() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient(config().addChannelOption(ChannelOption.TCP_NODELAY, Boolean.TRUE))) {
    // Response response = client.prepareGet("http://test.s3.amazonaws.com/").execute().get();
    // if (response.getResponseBody() == null || response.getResponseBody().equals("")) {
    // fail("No response Body");
    // } else {
    // assertEquals(response.getStatusCode(), 403);
    // }
    // }
    // }
    //
    // @Test
    // public void idleRequestTimeoutTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient(config().setPooledConnectionIdleTimeout(5000).setRequestTimeout(10000))) {
    // HttpHeaders h = new DefaultHttpHeaders();
    // h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
    // h.add("LockThread", "true");
    //
    // long t1 = millisTime();
    // try {
    // client.prepareGet(getTargetUrl()).setHeaders(h).setUrl(getTargetUrl()).execute().get();
    // fail();
    // } catch (Throwable ex) {
    // final long elapsedTime = millisTime() - t1;
    // System.out.println("EXPIRED: " + (elapsedTime));
    // assertNotNull(ex.getCause());
    // assertTrue(elapsedTime >= 10000 && elapsedTime <= 25000);
    // }
    // }
    // }
    //
    // @Test
    // public void asyncDoPostCancelTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // HttpHeaders h = new DefaultHttpHeaders();
    // h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
    // h.add("LockThread", "true");
    // StringBuilder sb = new StringBuilder();
    // sb.append("LockThread=true");
    //
    // final AtomicReference<CancellationException> ex = new AtomicReference<>();
    // ex.set(null);
    // try {
    // Future<Response> future = client.preparePost(getTargetUrl()).setHeaders(h).setBody(sb.toString()).execute(new AsyncCompletionHandlerAdapter() {
    //
    // @Override
    // public void onThrowable(Throwable t) {
    // if (t instanceof CancellationException) {
    // ex.set((CancellationException) t);
    // }
    // t.printStackTrace();
    // }
    //
    // });
    //
    // future.cancel(true);
    // } catch (IllegalStateException ise) {
    // fail();
    // }
    // assertNotNull(ex.get());
    // }
    // }
    //
    // @Test
    // public void getShouldAllowBody() throws IOException {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // client.prepareGet(getTargetUrl()).setBody("Boo!").execute();
    // }
    // }
    //
    // @Test(groups = "standalone", expectedExceptions = NullPointerException.class)
    // public void invalidUri() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // client.prepareGet(String.format("http:localhost:%d/foo/test", port1)).build();
    // }
    // }
    //
    // @Test
    // public void bodyAsByteTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // Response response = client.prepareGet(getTargetUrl()).execute().get();
    // assertEquals(response.getStatusCode(), 200);
    // assertEquals(response.getResponseBodyAsBytes(), new byte[] {});
    // }
    // }
    //
    // @Test
    // public void mirrorByteTest() throws Exception {
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // Response response = client.preparePost(getTargetUrl()).setBody("MIRROR").execute().get();
    // assertEquals(response.getStatusCode(), 200);
    // assertEquals(new String(response.getResponseBodyAsBytes(), UTF_8), "MIRROR");
    // }
    // }
    //
    // @Test
    // public void testNewConnectionEventsFired() throws Exception {
    // Request request = get("http://localhost:" + port1 + "/Test").build();
    //
    // try (AsyncHttpClient client = asyncHttpClient()) {
    // EventCollectingHandler handler = new EventCollectingHandler();
    // client.executeRequest(request, handler).get(3, TimeUnit.SECONDS);
    // handler.waitForCompletion(3, TimeUnit.SECONDS);
    //
    // Object[] expectedEvents = new Object[] {//
    // CONNECTION_POOL_EVENT,//
    // HOSTNAME_RESOLUTION_EVENT,//
    // HOSTNAME_RESOLUTION_SUCCESS_EVENT,//
    // CONNECTION_OPEN_EVENT,//
    // CONNECTION_SUCCESS_EVENT,//
    // REQUEST_SEND_EVENT,//
    // HEADERS_WRITTEN_EVENT,//
    // STATUS_RECEIVED_EVENT,//
    // HEADERS_RECEIVED_EVENT,//
    // CONNECTION_OFFER_EVENT,//
    // COMPLETED_EVENT };
    //
    // assertEquals(handler.firedEvents.toArray(), expectedEvents, "Got " + Arrays.toString(handler.firedEvents.toArray()));
    // }
    // }
}
