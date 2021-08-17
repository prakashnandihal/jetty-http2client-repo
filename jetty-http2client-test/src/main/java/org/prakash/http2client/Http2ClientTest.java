package org.prakash.http2client;

import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class Http2ClientTest {

    private static final String APNS_TOPIC_REQUEST_HEADER = "apns-topic";
    private static final String APNS_AUTHORIZATION_HEADER = "authorization";

    private static final String PRIORITY_HEADER_VALUE = "10";
    private static final String PUSH_TYPE_HEADER_VALUE = "alert";

    private static final String APNS_PRIORITY_HEADER = "apns-priority";
    private static final String APNS_PUSH_TYPE_HEADER = "apns-push-type";

    public static void main(String args[]) throws Exception {

        final HTTP2Client http2Client = new HTTP2Client();
        http2Client.start();
        final SslContextFactory ssl = new SslContextFactory.Client(true);
        ssl.setKeyStorePassword("");
        HttpClient client = new HttpClient(new HttpClientTransportOverHTTP2(http2Client), ssl);

        client.setMaxRequestsQueuedPerDestination(500000);
        client.setMaxConnectionsPerDestination(100);
        client.setConnectTimeout(3000L);
        client.start();

        int threadCount = 100;
        int noOfRequestsPerThread = 100;
        CountDownLatch countDownLatch = new CountDownLatch((threadCount * noOfRequestsPerThread));

        for (int i = 0; i < threadCount; i++) {

            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    postRequests(client, countDownLatch, noOfRequestsPerThread);
                }
            });

            t.setName("Thread-Id-" + i);
            t.start();
        }

        countDownLatch.await();
        System.out.println("CountDownLatch count:" + countDownLatch.getCount());

        try {
            client.stop();
            System.out.println("Http Client Stopped successfully:");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void postRequests(HttpClient client, CountDownLatch countDownLatch,
            int noOfRequests) {

        String url = "https://api.push.apple.com";
        String json = "{\"ei\":\"ETY-12ACCFDG\",\"aps\":{\"alert\":\"Welcome\"},\"rsys_src\":\"cvb\"}";
        /**
         * Jwt token is valid for only 1 hour ,We need Apple ios app credentials to generate Auth token.
         */
        String jwtToken = "eyJraWQiOiJGTUJEVVpNSjU2IiwiYWxnIjoiRVMyNTYifQ==.eyJpc3MiOiJWWUFLUzdXN1ZDIiwiaWF0IjoxNjI5MTc2MjYxfQ==.MEUCIQCMXoBujRSDvqSbI2puKXErXrO+6HivejXQvCFYiJS1LgIgTzl6xQgP3KMUVy/Gur+o/FJBJ3E2b5qps4erRwwCWoc=";

        for (int i = 0; i < noOfRequests; i++) {
            final Request req = client.POST(url).path(
                    "/3/device/3513a176f516b442b84f4c8c3471f9869356d9f1cd957690e117a63b627" + i)
                    .content(new StringContentProvider(json));

            req.header(APNS_TOPIC_REQUEST_HEADER, "org.test.local.xyz");
            req.header(APNS_AUTHORIZATION_HEADER, "bearer " + jwtToken);
            req.header(APNS_PRIORITY_HEADER, PRIORITY_HEADER_VALUE);
            req.header(APNS_PUSH_TYPE_HEADER, PUSH_TYPE_HEADER_VALUE);

            sendAsync(req, countDownLatch);

        }
    }

    private static void sendAsync(Request req, CountDownLatch countDownLatch) {
        req.send(new BufferingResponseListener() {
            @Override
            public void onComplete(Result result) {

                if (result.isSucceeded()) {
                    System.out.println("status" + getContentAsString() + "code:"
                            + result.getResponse().getStatus());
                }
                else {
                    System.out.println("Error:" + result.getFailure());
                }
                countDownLatch.countDown();
            }
        });
    }

    private static void sendSync(Request req) {
        ContentResponse response = null;
        try {
            response = req.send();
        }
        catch (InterruptedException | TimeoutException | ExecutionException e) {
            e.printStackTrace();
        }

        if (response != null) {

            int statusCode = response.getStatus();
            String contentAsString = response.getContentAsString();

            System.out.println(Thread.currentThread().getName() + "Status:" + statusCode + ",Rsp:"
                    + contentAsString);

            HttpFields headers = response.getHeaders();
            Enumeration<String> enums = headers.getFieldNames();

            while (enums.hasMoreElements()) {
                String param = enums.nextElement();
                System.out.println(param + ":" + headers.get(param));
            }
        }
    }

}
