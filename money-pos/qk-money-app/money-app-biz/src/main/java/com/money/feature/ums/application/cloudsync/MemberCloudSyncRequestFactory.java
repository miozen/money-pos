package com.money.feature.ums.application.cloudsync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.money.dto.cloud.MemberCloudSnapshot;
import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.util.*;

/** Builds the Worker contract without ever serializing a raw local phone number. */
@Component
@RequiredArgsConstructor
public class MemberCloudSyncRequestFactory {
    private final ObjectMapper objectMapper;

    public CloudBatchRequest build(MemberCloudSyncProperties properties, List<CloudSyncItem> items, long nowEpochMillis) {
        requireConfigured(properties);
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (CloudSyncItem item : items) snapshots.add(toWireSnapshot(item, properties.getSearchHmacSecretBase64()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", properties.getStoreId());
        body.put("snapshots", snapshots);
        try {
            String json = objectMapper.writeValueAsString(body);
            return signedRequest(properties, "/internal/v1/member-snapshots:batch", json, nowEpochMillis);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化Omnia Cloud会员快照", ex);
        }
    }

    /** Signed, no-data connection probe for the Worker and its D1 binding. */
    public CloudBatchRequest buildConnectionTest(MemberCloudSyncProperties properties, long nowEpochMillis) {
        requireConfigured(properties);
        try {
            return signedRequest(properties, "/internal/v1/member-snapshots:test",
                    objectMapper.writeValueAsString(Collections.singletonMap("storeId", properties.getStoreId())), nowEpochMillis);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化Omnia Cloud连通性测试", ex);
        }
    }

    public void requireAcceptedCount(String responseBody, int expectedCount) {
        try {
            int accepted = objectMapper.readTree(responseBody).path("accepted").asInt(-1);
            if (accepted != expectedCount) throw new IllegalStateException("Omnia Cloud确认数量不匹配");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Omnia Cloud返回无效确认结果", ex);
        }
    }

    private Map<String, Object> toWireSnapshot(CloudSyncItem item, String searchSecret) {
        CloudMemberSyncOutbox outbox = item.getOutbox();
        Map<String, Object> result = new LinkedHashMap<>();
        // Snowflake member IDs exceed JavaScript's safe integer range; the Worker receives exact decimal text.
        result.put("memberId", Long.toString(outbox.getMemberId()));
        result.put("version", Long.toString(outbox.getSnapshotVersion()));
        result.put("operation", outbox.getOperation());
        result.put("sourceUpdatedAt", toEpochMillis(outbox));
        if (!"UPSERT".equals(outbox.getOperation())) return result;
        MemberCloudSnapshot snapshot = Objects.requireNonNull(item.getSnapshot(), "UPSERT requires a member snapshot");
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("name", snapshot.getName());
        projection.put("nameNormalized", normalizeName(snapshot.getName()));
        projection.put("phoneHmac", hmacHex(searchSecret, normalizePhone(snapshot.getPhone())));
        projection.put("phoneLast4Hmac", hmacHex(searchSecret, last4(snapshot.getPhone())));
        projection.put("phoneMasked", maskPhone(snapshot.getPhone()));
        projection.put("memberType", snapshot.getType());
        projection.put("balance", zero(snapshot.getBalance()));
        projection.put("couponBalance", zero(snapshot.getCouponBalance()));
        projection.put("brandLevels", snapshot.getBrandLevels());
        projection.put("brandLevelDescriptions", snapshot.getBrandLevelDescriptions());
        projection.put("unusedCoupons", snapshot.getUnusedCoupons());
        projection.put("quantityRights", snapshot.getQuantityRights());
        projection.put("amountRights", snapshot.getAmountRights());
        projection.put("targetPlans", snapshot.getTargetPlans());
        result.put("snapshot", projection);
        return result;
    }

    private CloudBatchRequest signedRequest(MemberCloudSyncProperties properties, String path, String json, long nowEpochMillis) {
        String hash = sha256Hex(json);
        String nonce = UUID.randomUUID().toString();
        String canonical = "POST\n" + path + "\n" + nowEpochMillis + "\n" + nonce + "\n" + hash;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Omnia-Timestamp", Long.toString(nowEpochMillis));
        headers.put("X-Omnia-Nonce", nonce);
        headers.put("X-Omnia-Body-SHA256", hash);
        headers.put("X-Omnia-Signature", hmacHex(properties.getSigningSecretBase64(), canonical));
        return new CloudBatchRequest(json, headers);
    }

    private long toEpochMillis(CloudMemberSyncOutbox item) {
        return (item.getUpdateTime() == null ? item.getCreateTime() : item.getUpdateTime()).toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    private void requireConfigured(MemberCloudSyncProperties properties) {
        if (blank(properties.getEndpoint()) || !isReceiverEndpoint(properties.getEndpoint()) || blank(properties.getStoreId()) ||
                blank(properties.getSigningSecretBase64()) || blank(properties.getSearchHmacSecretBase64())) {
            throw new IllegalStateException("Omnia Cloud发布器需要显式配置HTTPS端点、门店、签名密钥和检索HMAC密钥");
        }
    }
    private boolean isReceiverEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && "/internal/v1/member-snapshots:batch".equals(uri.getPath());
        } catch (IllegalArgumentException ex) { return false; }
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String normalizeName(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String normalizePhone(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private String last4(String value) { String normalized = normalizePhone(value); return normalized.length() <= 4 ? normalized : normalized.substring(normalized.length() - 4); }
    private String maskPhone(String value) { String normalized = normalizePhone(value); return normalized.length() < 7 ? null : normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String sha256Hex(String value) { try { return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String hmacHex(String secretBase64, String value) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256")); return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException("Omnia Cloud密钥格式无效", ex); } }
    private String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format("%02x", value)); return result.toString(); }

    @lombok.Value public static class CloudSyncItem { CloudMemberSyncOutbox outbox; MemberCloudSnapshot snapshot; }
    @lombok.Value public static class CloudBatchRequest { String body; Map<String, String> headers; }
}
