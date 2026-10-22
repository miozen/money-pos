package com.money.feature.ums.application.cloudsync;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** JDK-only HTTPS publisher. It is called only by the explicitly enabled local dispatcher. */
@Component
@RequiredArgsConstructor
public class HttpsMemberCloudSyncPublisher {
    private final MemberCloudSyncProperties properties;
    private final MemberCloudSyncRequestFactory requestFactory;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public void publish(List<MemberCloudSyncRequestFactory.CloudSyncItem> items) {
        if (items.isEmpty()) return;
        MemberCloudSyncRequestFactory.CloudBatchRequest request = requestFactory.build(properties, items, System.currentTimeMillis());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                .timeout(Duration.ofSeconds(20)).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(request.getBody()));
        request.getHeaders().forEach(builder::header);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Omnia Cloud接收端返回HTTP " + response.statusCode());
            requestFactory.requireAcceptedCount(response.body(), items.size());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Omnia Cloud发布被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Omnia Cloud会员快照发布失败: " + ex.getMessage(), ex);
        }
    }

    /** Verifies signed Worker authentication and D1 availability without writing a member snapshot. */
    public void testConnection() {
        MemberCloudSyncRequestFactory.CloudBatchRequest request = requestFactory.buildConnectionTest(properties, System.currentTimeMillis());
        URI endpoint = URI.create(properties.getEndpoint().replace("/internal/v1/member-snapshots:batch", "/internal/v1/member-snapshots:test"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20)).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(request.getBody()));
        request.getHeaders().forEach(builder::header);
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !response.body().contains("\"ok\":true")) {
                throw new IllegalStateException("Omnia Cloud连接测试失败，HTTP " + response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Omnia Cloud连接测试被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Omnia Cloud连接测试失败: " + ex.getMessage(), ex);
        }
    }
}
