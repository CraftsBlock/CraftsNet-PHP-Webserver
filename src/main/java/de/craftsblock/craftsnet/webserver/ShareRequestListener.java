package de.craftsblock.craftsnet.webserver;

import com.sun.net.httpserver.HttpExchange;
import de.craftsblock.craftscore.event.EventHandler;
import de.craftsblock.craftscore.event.ListenerAdapter;
import de.craftsblock.craftsnet.api.http.Request;
import de.craftsblock.craftsnet.api.http.Response;
import de.craftsblock.craftsnet.events.shares.ShareRequestEvent;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class ShareRequestListener implements ListenerAdapter {

    private static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cache(null)
            .build();

    @EventHandler
    public void handleShareRequest(ShareRequestEvent event) throws URISyntaxException, IOException {
        Request request = event.getExchange().request();
        Response response = event.getExchange().response();

        String url = "http://127.0.0.1:9000";
        URL phpUrl = new URI(url + request.getRawUrl()).toURL();

        HttpExchange exchange = request.unsafe();

        okhttp3.Request.Builder connetionBuilder = new okhttp3.Request.Builder()
                .url(phpUrl);
        connetionBuilder.setMethod$okhttp(exchange.getRequestMethod());

        Headers.Builder requestHeaders = connetionBuilder.getHeaders$okhttp();
        request.getHeaders().forEach((key, values) -> requestHeaders.set(key, String.join(", ", values)));
        requestHeaders.set("X-Forwarded-For", request.getIp());
        requestHeaders.set("X-Forwarded-Host", request.getDomain());

        connetionBuilder.headers(requestHeaders.build());

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            InputStream requestBody = exchange.getRequestBody();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = requestBody.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            RequestBody body = RequestBody.create(os.toByteArray(), null);
            connetionBuilder.method(exchange.getRequestMethod(), body);
        } catch (IllegalArgumentException e) {
            if (!e.getMessage().contains("must not have a request body")) throw e;
        }

        try (okhttp3.Response connection = okHttpClient.newCall(connetionBuilder.build()).execute()) {
            response.setCode(connection.code());
            connection.headers().forEach(pair -> response.setHeader(pair.getFirst(), pair.getSecond()));

            if (connection.body() != null)
                try (ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
                     InputStream responseStream = connection.body().byteStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = responseStream.read(buffer)) != -1) {
                        responseBody.write(buffer, 0, bytesRead);
                    }
                    response.print(responseBody.toByteArray());
                } catch (FileNotFoundException ignored) {
                }
        }

        event.setCancelled(true);
    }

}
