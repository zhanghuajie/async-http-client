/*
 * Copyright (c) 2016 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 *     http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.asynchttpclient;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.asynchttpclient.Dsl.*;
import static org.asynchttpclient.test.TestUtils.*;
import static org.asynchttpclient.util.DateUtils.millisTime;
import static org.testng.Assert.*;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.asynchttpclient.cookie.Cookie;
import org.asynchttpclient.handler.MaxRedirectException;
import org.asynchttpclient.request.body.multipart.StringPart;
import org.asynchttpclient.test.EventCollectingHandler;
import org.asynchttpclient.test.TestUtils.AsyncCompletionHandlerAdapter;
import org.asynchttpclient.testserver.HttpServer;
import org.asynchttpclient.testserver.HttpTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class BasicHttpTest extends HttpTest {

    private static HttpServer server;

    @BeforeClass
    public static void start() throws Throwable {
        server = new HttpServer();
        server.start();
    }

    @AfterClass
    public static void stop() throws Throwable {
        server.close();
    }

    private static String getTargetUrl() {
        return server.getUrl() + "/foo/bar";
    }

    @Test
    public void getRootUrl() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                Request request = get(server.getUrl()).build();
                server.enqueueOk();

                Response response = client.executeRequest(request, new AsyncCompletionHandlerAdapter()).get(TIMEOUT, TimeUnit.SECONDS);
                assertEquals(response.getUri().toUrl(), server.getUrl());
            });
        });
    }

    @Test
    public void getUrlWithPathWithoutQuery() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void getUrlWithPathWithQuery() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void getUrlWithPathWithQueryParams() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void getResponseBody() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void getWithHeaders() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void postWithHeadersAndFormParams() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void headHasEmptyBody() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void nullSchemeThrowsNPE() throws Throwable {
        withClient().run(client -> client.prepareGet("gatling.io").execute());
    }

    @Test
    public void jettyRespondsWithChunked() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueEcho();
                client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter() {

                    @Override
                    public Response onCompleted(Response response) throws Exception {
                        assertEquals(response.getStatusCode(), 200);
                        assertEquals(response.getHeader(HttpHeaders.Names.TRANSFER_ENCODING), HttpHeaders.Values.CHUNKED);
                        return response;
                    }
                }).get(TIMEOUT, TimeUnit.SECONDS);
            });
        });
    }

    @Test
    public void getWithCookies() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void defaultBodyEncodingIsIso() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueEcho();
                Response response = client.preparePost(getTargetUrl()).setBody("\u017D\u017D\u017D\u017D\u017D\u017D").execute().get();
                assertEquals(response.getResponseBodyAsBytes(), "\u017D\u017D\u017D\u017D\u017D\u017D".getBytes(StandardCharsets.ISO_8859_1));
            });
        });
    }

    @Test
    public void postFormParametersAsBodyString() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void postFormParametersAsBodyStream() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void putFormParametersAsBodyStream() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void postSingleStringPart() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    // @Test
    // public void asyncDoPostProxyTest() throws Throwable {
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
    // public Response onCompleted(Response response) throws Throwable {
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
    public void getVirtualHost() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                String virtualHost = "localhost:" + server.getPort();

                Request request = get(getTargetUrl()).setVirtualHost(virtualHost).build();

                server.enqueueEcho();
                Response response = client.executeRequest(request, new AsyncCompletionHandlerAdapter()).get(TIMEOUT, TimeUnit.SECONDS);

                assertEquals(response.getStatusCode(), 200);
                if (response.getHeader("X-" + HttpHeaders.Names.HOST) == null) {
                    System.err.println(response);
                }
                assertEquals(response.getHeader("X-" + HttpHeaders.Names.HOST), virtualHost);
            });
        });
    }

    @Test(expectedExceptions = CancellationException.class)
    public void cancelledFutureThrowsCancellationException() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void futureTimeOutThrowsTimeoutException() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                HttpHeaders headers = new DefaultHttpHeaders();
                headers.add("X-Delay", 5_000);

                server.enqueueEcho();
                Future<Response> future = client.prepareGet(getTargetUrl()).setHeaders(headers).execute(new AsyncCompletionHandlerAdapter() {
                    @Override
                    public void onThrowable(Throwable t) {
                    }
                });

                future.get(2, TimeUnit.SECONDS);
            });
        });
    }

    @Test(expectedExceptions = ConnectException.class)
    public void connectFailureThrowsConnectException() throws Throwable {
        withClient().run(client -> {
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
        });
    }

    @Test
    public void connectFailureNotifiesHandlerWithConnectException() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test(expectedExceptions = UnknownHostException.class)
    public void unknownHostThrowsUnknownHostException() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                try {
                    client.prepareGet("http://null.gatling.io").execute(new AsyncCompletionHandlerAdapter() {
                        @Override
                        public void onThrowable(Throwable t) {
                        }
                    }).get(TIMEOUT, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            });
        });
    }

    @Test
    public void getEmptyBody() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueOk();
                Response response = client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter())//
                        .get(TIMEOUT, TimeUnit.SECONDS);
                assertTrue(response.getResponseBody().isEmpty());
            });
        });
    }

    @Test
    public void getEmptyBodyNotifiesHandler() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                // Use a latch in case the assert fail
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
                });
        });
    }

    @Test
    public void exceptionInOnCompletedGetNotifiedToOnThrowable() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void exceptionInOnCompletedGetNotifiedToFuture() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
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
            });
        });
    }

    @Test
    public void asyncDoGetDelayHandlerTest() throws Throwable {
        withClient(config().setRequestTimeout(1_000)).run(client -> {
            withServer(server).run(server -> {
                HttpHeaders headers = new DefaultHttpHeaders();
                headers.add("X-Delay", 5_000); // delay greater than timeout
                    // Use a latch in case the assert fail
                    final CountDownLatch l = new CountDownLatch(1);

                    server.enqueueEcho();
                    client.prepareGet(getTargetUrl()).setHeaders(headers).execute(new AsyncCompletionHandlerAdapter() {

                        @Override
                        public Response onCompleted(Response response) throws Exception {
                            try {
                                fail("Must not receive a response");
                            } finally {
                                l.countDown();
                            }
                            return response;
                        }

                        @Override
                        public void onThrowable(Throwable t) {
                            try {
                                if (t instanceof TimeoutException) {
                                    assertTrue(true);
                                } else {
                                    fail("Unexpected exception", t);
                                }
                            } finally {
                                l.countDown();
                            }
                        }
                    });

                    if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
                        fail("Timed out");
                    }
                });
        });
    }

    @Test
    public void asyncDoGetQueryStringTest() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueEcho();
                client.prepareGet(getTargetUrl() + "?foo=bar").execute(new AsyncCompletionHandlerAdapter() {
                    @Override
                    public Response onCompleted(Response response) throws Exception {
                        assertTrue(response.getHeader("X-PathInfo") != null);
                        assertTrue(response.getHeader("X-QueryString") != null);
                        return response;
                    }
                }).get(TIMEOUT, TimeUnit.SECONDS);
            });
        });
    }

    @Test
    public void asyncDoGetKeepAliveHandlerTest() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                final CountDownLatch l = new CountDownLatch(2);

                AsyncCompletionHandler<Response> handler = new AsyncCompletionHandlerAdapter() {

                    volatile String clientPort;

                    @Override
                    public Response onCompleted(Response response) throws Exception {
                        try {
                            assertEquals(response.getStatusCode(), 200);
                            if (clientPort == null) {
                                clientPort = response.getHeader("X-ClientPort");
                            } else {
                                // verify that the server saw the same client remote address/port
                                // so the same connection was used
                                assertEquals(response.getHeader("X-ClientPort"), clientPort);
                            }
                        } finally {
                            l.countDown();
                        }
                        return response;
                    }
                };

                server.enqueueEcho();
                client.prepareGet(getTargetUrl()).execute(handler).get(TIMEOUT, TimeUnit.SECONDS);
                server.enqueueEcho();
                client.prepareGet(getTargetUrl()).execute(handler);

                if (!l.await(TIMEOUT, TimeUnit.SECONDS)) {
                    fail("Timed out");
                }
            });
        });
    }

    @Test(expectedExceptions = MaxRedirectException.class)
    public void asyncDoGetMaxRedirectTest() throws Throwable {
        withClient(config().setMaxRedirects(1).setFollowRedirect(true)).run(client -> {
            withServer(server).run(server -> {
                try {
                    // max redirect is 1, so second redirect will fail
                    server.enqueueRedirect(301, getTargetUrl());
                    server.enqueueRedirect(301, getTargetUrl());
                    client.prepareGet(getTargetUrl()).execute(new AsyncCompletionHandlerAdapter() {
                        @Override
                        public Response onCompleted(Response response) throws Exception {
                            fail("Should not be here");
                            return response;
                        }

                        @Override
                        public void onThrowable(Throwable t) {
                        }
                    }).get(TIMEOUT, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            });
        });
    }

    @Test
    public void nonBlockingNestedRequetsFromIoThreadAreFine() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {

                final int maxNested = 5;

                final CountDownLatch latch = new CountDownLatch(2);

                final AsyncCompletionHandlerAdapter handler = new AsyncCompletionHandlerAdapter() {

                    private AtomicInteger nestedCount = new AtomicInteger(0);

                    @Override
                    public Response onCompleted(Response response) throws Exception {
                        try {
                            if (nestedCount.getAndIncrement() < maxNested) {
                                client.prepareGet(getTargetUrl()).execute(this);
                            }
                        } finally {
                            latch.countDown();
                        }
                        return response;
                    }
                };

                for (int i = 0; i < maxNested + 1; i++) {
                    server.enqueueOk();
                }

                client.prepareGet(getTargetUrl()).execute(handler);

                if (!latch.await(TIMEOUT, TimeUnit.SECONDS)) {
                    fail("Timed out");
                }
            });
        });
    }

    @Test
    public void optionsTest() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueEcho();
                Response response = client.prepareOptions(getTargetUrl()).execute().get();
                assertEquals(response.getStatusCode(), 200);
                assertEquals(response.getHeader("Allow"), "GET,HEAD,POST,OPTIONS,TRACE");
            });
        });
    }

    @Test(expectedExceptions = TimeoutException.class)
    public void idleRequestTimeoutTest() throws Throwable {
        withClient(config().setRequestTimeout(1_000)).run(client -> {
            withServer(server).run(server -> {
                HttpHeaders h = new DefaultHttpHeaders();
                h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
                h.add("X-Delay", 2_000);

                server.enqueueEcho();
                long start = millisTime();
                try {
                    client.prepareGet(getTargetUrl()).setHeaders(h).setUrl(getTargetUrl()).execute().get();
                } catch (Throwable ex) {
                    final long elapsedTime = millisTime() - start;
                    assertTrue(elapsedTime >= 1_000 && elapsedTime <= 1_500);
                    throw ex.getCause();
                }
            });
        });
    }

    @Test
    public void asyncDoPostCancelTest() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                HttpHeaders h = new DefaultHttpHeaders();
                h.add(HttpHeaders.Names.CONTENT_TYPE, HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
                h.add("X-Delay", 2_000);

                CountDownLatch latch = new CountDownLatch(1);

                Future<Response> future = client.preparePost(getTargetUrl()).setHeaders(h).setBody("Body").execute(new AsyncCompletionHandlerAdapter() {

                    @Override
                    public void onThrowable(Throwable t) {
                        if (t instanceof CancellationException) {
                            latch.countDown();
                        }
                    }
                });

                future.cancel(true);
                if (!latch.await(TIMEOUT, TimeUnit.SECONDS)) {
                    fail("Timed out");
                }
            });
        });
    }

    @Test
    public void getShouldAllowBody() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                client.prepareGet(getTargetUrl()).setBody("Boo!").execute();
            });
        });
    }

    @Test(groups = "standalone", expectedExceptions = NullPointerException.class)
    public void invalidUri() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                client.prepareGet(String.format("http:localhost:%d/foo/test", server.getPort())).build();
            });
        });
    }

    @Test
    public void bodyAsByteTest() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueEcho();
                Response response = client.prepareGet(getTargetUrl()).execute().get();
                assertEquals(response.getStatusCode(), 200);
                assertEquals(response.getResponseBodyAsBytes(), new byte[] {});
            });
        });
    }

    @Test
    public void mirrorByteTest() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {
                server.enqueueEcho();
                Response response = client.preparePost(getTargetUrl()).setBody("MIRROR").execute().get();
                assertEquals(response.getStatusCode(), 200);
                assertEquals(new String(response.getResponseBodyAsBytes(), UTF_8), "MIRROR");
            });
        });
    }

    @Test
    public void testNewConnectionEventsFired() throws Throwable {
        withClient().run(client -> {
            withServer(server).run(server -> {

                Request request = get(getTargetUrl()).build();

                EventCollectingHandler handler = new EventCollectingHandler();
                client.executeRequest(request, handler).get(3, TimeUnit.SECONDS);
                handler.waitForCompletion(3, TimeUnit.SECONDS);

                Object[] expectedEvents = new Object[] {//
                CONNECTION_POOL_EVENT,//
                        HOSTNAME_RESOLUTION_EVENT,//
                        HOSTNAME_RESOLUTION_SUCCESS_EVENT,//
                        CONNECTION_OPEN_EVENT,//
                        CONNECTION_SUCCESS_EVENT,//
                        REQUEST_SEND_EVENT,//
                        HEADERS_WRITTEN_EVENT,//
                        STATUS_RECEIVED_EVENT,//
                        HEADERS_RECEIVED_EVENT,//
                        CONNECTION_OFFER_EVENT,//
                        COMPLETED_EVENT };

                assertEquals(handler.firedEvents.toArray(), expectedEvents, "Got " + Arrays.toString(handler.firedEvents.toArray()));
            });
        });
    }
}
