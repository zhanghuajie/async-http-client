package org.asynchttpclient.example.completable;

import org.asynchttpclient.*;

import java.util.concurrent.Future;

/**
 * Created by zhanghuajie on 16/10/14.
 */
public class AsyncHttpClientTest {

    public final static String TESTURL="https://kyfw.12306.cn/otn/lcxxcx/query?purpose_codes=ADULT&queryDate=2016-11-30&from_station=BXP&to_station=EDP";
    public static void main(String[] args) throws Exception {
        AsyncHttpClientConfig cf = new DefaultAsyncHttpClientConfig.Builder()
                .setConnectionTtl(2*1000)
                .setFollowRedirect(false)
                .setAcceptAnyCertificate(true)
                .build();
        AsyncHttpClient asyncHttpClient = new DefaultAsyncHttpClient(cf);
        Future<Response> f = asyncHttpClient.prepareGet(TESTURL).execute();
        Response r = f.get();
        System.out.print(r.getResponseBody());

    }
}
