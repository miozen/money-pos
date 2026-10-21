package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncConfig;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncConfigMapper;
import com.money.web.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.URI;

/** Owns the one local store configuration and refreshes the in-memory publisher settings after saving. */
@Service
@RequiredArgsConstructor
public class MemberCloudSyncConfigurationService {
    private static final long CONFIG_ID = 1L;
    private final CloudMemberSyncConfigMapper mapper;
    private final MemberCloudSyncProperties properties;

    @PostConstruct
    public void loadAtStartup() { properties.apply(config()); }

    public MemberCloudSyncConfigurationDTO view() {
        CloudMemberSyncConfig config = config();
        MemberCloudSyncConfigurationDTO result = new MemberCloudSyncConfigurationDTO();
        result.setEndpoint(config.getEndpoint()); result.setStoreId(config.getStoreId()); result.setTenantId(config.getTenantId()); result.setKeyId(config.getKeyId());
        result.setEnabled(Boolean.TRUE.equals(config.getEnabled())); result.setInitialBackfillEnabled(Boolean.TRUE.equals(config.getInitialBackfillEnabled()));
        result.setInitialBackfillBatchSize(config.getInitialBackfillBatchSize());
        result.setSigningSecretConfigured(present(config.getSigningSecretBase64())); result.setSearchHmacSecretConfigured(present(config.getSearchHmacSecretBase64()));
        return result;
    }

    @Transactional
    public MemberCloudSyncConfigurationDTO save(MemberCloudSyncConfigurationDTO request) {
        CloudMemberSyncConfig config = config();
        config.setEndpoint(trim(request.getEndpoint())); config.setStoreId(trim(request.getStoreId())); config.setTenantId(request.getTenantId()); config.setKeyId(trim(request.getKeyId()));
        if (present(request.getSigningSecretBase64())) config.setSigningSecretBase64(request.getSigningSecretBase64().trim());
        if (present(request.getSearchHmacSecretBase64())) config.setSearchHmacSecretBase64(request.getSearchHmacSecretBase64().trim());
        config.setEnabled(request.isEnabled()); config.setInitialBackfillEnabled(request.isInitialBackfillEnabled());
        config.setInitialBackfillBatchSize(Math.max(1, Math.min(request.getInitialBackfillBatchSize() == null ? 500 : request.getInitialBackfillBatchSize(), 1000)));
        if (config.getEnabled() && !complete(config)) throw new BaseException("启用Omnia Cloud前，请填写有效地址、门店、Key ID和两个密钥");
        mapper.updateById(config);
        properties.apply(config);
        return view();
    }

    private CloudMemberSyncConfig config() {
        CloudMemberSyncConfig config = mapper.selectById(CONFIG_ID);
        if (config != null) return config;
        config = new CloudMemberSyncConfig(); config.setId(CONFIG_ID); config.setEnabled(false); config.setTenantId(0L); config.setInitialBackfillEnabled(false); config.setInitialBackfillBatchSize(500);
        mapper.insert(config); return config;
    }
    private boolean present(String value) { return value != null && !value.trim().isEmpty(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private boolean complete(CloudMemberSyncConfig config) {
        try {
            URI uri = URI.create(config.getEndpoint());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && "/internal/v1/member-snapshots:batch".equals(uri.getPath())
                    && present(config.getStoreId()) && present(config.getKeyId())
                    && present(config.getSigningSecretBase64()) && present(config.getSearchHmacSecretBase64());
        } catch (Exception ex) { return false; }
    }
}
