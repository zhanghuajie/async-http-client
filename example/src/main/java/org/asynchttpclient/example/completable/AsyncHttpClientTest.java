package org.asynchttpclient.example.completable;

import org.asynchttpclient.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by zhanghuajie on 16/10/14.
 */
public class AsyncHttpClientTest {

    public final static String TESTURL="https://kyfw.12306.cn/otn/lcxxcx/query?purpose_codes=ADULT&queryDate=2016-11-30&from_station=BXP&to_station=EDP";
    public static void main(String[] args) throws Exception {
        AsyncHttpClientConfig cf = new DefaultAsyncHttpClientConfig.Builder()
                .setConnectionTtl(2 * 1000)
                .setFollowRedirect(false)
                .setRequestTimeout(2 * 1000)
                .setAcceptAnyCertificate(true)
                .setMaxConnections(10000)
                .setMaxConnectionsPerHost(10)
//                .setProxyServer(new ProxyServer("127.0.0.1", 8080))
                .build();
        AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(cf);
        List<Future<Response>> futurelist = new LinkedList<>();
        for (int i = 0; i <100000 ; i++) {
            Future<Response> f = asyncHttpClient.prepareGet(TESTURL).execute(new AsyncCompletionHandler<Response>() {
                @Override
                public Response onCompleted(Response response) throws Exception {
                    return null;
                }
            });
            futurelist.add(f);
        }
//        Response r = f.get();
//        System.out.print(r.getResponseBody());

    }
}
