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
    private final MemberCloudInitialBackfillService initialBackfillService;
    private final MemberCloudSyncDispatcher dispatcher;
    private final HttpsMemberCloudSyncPublisher publisher;

    @PostConstruct
    public void loadAtStartup() { properties.apply(config()); }

    public MemberCloudSyncConfigurationDTO view() {
        CloudMemberSyncConfig config = config();
        MemberCloudSyncConfigurationDTO result = new MemberCloudSyncConfigurationDTO();
        result.setEndpoint(config.getEndpoint()); result.setStoreId(config.getStoreId());
        result.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        result.setSigningSecretConfigured(present(config.getSigningSecretBase64())); result.setSearchHmacSecretConfigured(present(config.getSearchHmacSecretBase64()));
        return result;
    }

    @Transactional
    public MemberCloudSyncConfigurationDTO save(MemberCloudSyncConfigurationDTO request) {
        CloudMemberSyncConfig config = config();
        config.setEndpoint(trim(request.getEndpoint())); config.setStoreId(defaultStoreId(trim(request.getStoreId())));
        if (present(request.getSigningSecretBase64())) config.setSigningSecretBase64(request.getSigningSecretBase64().trim());
        if (present(request.getSearchHmacSecretBase64())) config.setSearchHmacSecretBase64(request.getSearchHmacSecretBase64().trim());
        config.setEnabled(request.isEnabled());
        if (config.getEnabled() && !complete(config)) throw new BaseException("启用Omnia Cloud前，请填写有效地址、门店和两个密钥");
        mapper.updateById(config);
        properties.apply(config);
        return view();
    }

    @Transactional
    public void beginRepair() { initialBackfillService.beginRepair(); }

    /** This dispatches only the existing local Outbox; it never creates or changes member records. */
    public void dispatchNow() { dispatcher.dispatchNow(); }

    public void testConnection() { publisher.testConnection(); }

    private CloudMemberSyncConfig config() {
        CloudMemberSyncConfig config = mapper.selectById(CONFIG_ID);
        if (config != null) return config;
        config = new CloudMemberSyncConfig(); config.setId(CONFIG_ID); config.setEnabled(false); config.setTenantId(0L); config.setStoreId("store-01"); config.setInitialBackfillBatchSize(500);
        mapper.insert(config); return config;
    }
    private boolean present(String value) { return value != null && !value.trim().isEmpty(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String defaultStoreId(String value) { return present(value) ? value : "store-01"; }
    private boolean complete(CloudMemberSyncConfig config) {
        try {
            URI uri = URI.create(config.getEndpoint());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && "/internal/v1/member-snapshots:batch".equals(uri.getPath())
                    && present(config.getStoreId())
                    && present(config.getSigningSecretBase64()) && present(config.getSearchHmacSecretBase64());
        } catch (Exception ex) { return false; }
    }
}
