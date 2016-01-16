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
package org.asynchttpclient.testserver;

import static org.asynchttpclient.test.TestUtils.*;
import io.netty.handler.codec.http.HttpHeaders;

import java.io.Closeable;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class HttpServer implements Closeable {

    private int port;
    private Server server;
    private final ConcurrentLinkedQueue<Handler> handlers = new ConcurrentLinkedQueue<>();

    public HttpServer() {
    }

    public HttpServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        if (port == 0) {
            port = findFreePort();
        }
        server = newJettyHttpServer(port);
        server.setHandler(new QueueHandler());
        server.start();
    }

    public void enqueue(Handler handler) {
        handlers.offer(handler);
    }

    public void enqueueOk() {
        enqueueResponse(response -> response.setStatus(200));
    }

    public void enqueueResponse(Consumer<HttpServletResponse> c) {
        handlers.offer(new ConsumerHandler(c));
    }

    public void enqueueEcho() {
        handlers.offer(new EchoHandler());
    }

    public void enqueueRedirect(int status, String location) {
        enqueueResponse(response -> {
            response.setStatus(status);
            response.setHeader(HttpHeaders.Names.LOCATION, location);
        });
    }

    public int getPort() {
        return port;
    }

    public String getUrl() {
        return "http://localhost:" + port;
    }

    public void reset() {
        handlers.clear();
    }

    @Override
    public void close() throws IOException {
        if (server == null) {
            try {
                server.stop();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private class QueueHandler extends AbstractHandler {

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            Handler handler = HttpServer.this.handlers.poll();
            if (handler == null) {
                response.sendError(500, "No handler enqueued");
                response.getOutputStream().flush();
                response.getOutputStream().close();

            } else {
                handler.handle(target, baseRequest, request, response);
            }
        }
    }

    public static abstract class AutoFlushHandler extends AbstractHandler {

        private final boolean closeAfterResponse;

        public AutoFlushHandler() {
            this(false);
        }

        public AutoFlushHandler(boolean closeAfterResponse) {
            this.closeAfterResponse = closeAfterResponse;
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            handle0(target, baseRequest, request, response);
            response.getOutputStream().flush();
            if (closeAfterResponse) {
                response.getOutputStream().close();
            }
        }

        protected abstract void handle0(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException;
    }

    private static class ConsumerHandler extends AutoFlushHandler {

        private final Consumer<HttpServletResponse> c;

        public ConsumerHandler(Consumer<HttpServletResponse> c) {
            this(c, false);
        }

        public ConsumerHandler(Consumer<HttpServletResponse> c, boolean closeAfterResponse) {
            super(closeAfterResponse);
            this.c = c;
        }

        @Override
        protected void handle0(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            c.accept(response);
        }
    }

    public static class EchoHandler extends AutoFlushHandler {

        @Override
        protected void handle0(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            String delay = request.getHeader("X-Delay");
            if (delay != null) {
                try {
                    Thread.sleep(Long.parseLong(delay));
                } catch (NumberFormatException | InterruptedException e1) {
                    throw new ServletException(e1);
                }
            }

            response.setStatus(200);

            if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
                response.addHeader("Allow", "GET,HEAD,POST,OPTIONS,TRACE");
            }

            response.addHeader("X-ClientPort", String.valueOf(request.getRemotePort()));

            String pathInfo = request.getPathInfo();
            if (pathInfo != null)
                response.addHeader("X-PathInfo", pathInfo);

            String queryString = request.getQueryString();
            if (queryString != null)
                response.addHeader("X-QueryString", queryString);

            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                response.addHeader("X-" + headerName, request.getHeader(headerName));
            }

            for (Entry<String, String[]> e : baseRequest.getParameterMap().entrySet()) {
                response.addHeader("X-" + e.getKey(), e.getValue()[0]);
            }

            Cookie[] cs = request.getCookies();
            if (cs != null) {
                for (Cookie c : cs) {
                    response.addCookie(c);
                }
            }

            int size = 16384;
            if (request.getContentLength() > 0) {
                size = request.getContentLength();
            }
            if (size > 0) {
                byte[] bytes = new byte[size];
                int read = 0;
                while (read > -1) {
                    read = request.getInputStream().read(bytes);
                    if (read > 0) {
                        response.getOutputStream().write(bytes, 0, read);
                    }
                }
            }
        }
    }
}
