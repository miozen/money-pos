package com.money.feature.ums.application.cloudsync;

import com.money.feature.ums.infrastructure.persistence.entity.CloudMemberSyncConfig;
import com.money.feature.ums.infrastructure.persistence.mapper.CloudMemberSyncConfigMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemberCloudSyncConfigurationServiceTest {
    @Test
    void normalConfigurationViewRedactsSecretsWhileExplicitRevealReturnsThem() {
        CloudMemberSyncConfigMapper mapper = mock(CloudMemberSyncConfigMapper.class);
        CloudMemberSyncConfig config = new CloudMemberSyncConfig();
        config.setId(1L); config.setStoreId("store-01"); config.setEnabled(true);
        config.setSigningSecretBase64("signing-secret"); config.setSearchHmacSecretBase64("search-secret");
        when(mapper.selectById(1L)).thenReturn(config);
        MemberCloudSyncConfigurationService service = new MemberCloudSyncConfigurationService(mapper,
                mock(MemberCloudSyncProperties.class), mock(MemberCloudInitialBackfillService.class),
                mock(MemberCloudSyncDispatcher.class), mock(HttpsMemberCloudSyncPublisher.class));

        MemberCloudSyncConfigurationDTO view = service.view();
        MemberCloudSyncSecretRevealDTO revealed = service.revealSecrets();

        assertThat(view.getSigningSecretBase64()).isNull();
        assertThat(view.getSearchHmacSecretBase64()).isNull();
        assertThat(view.isSigningSecretConfigured()).isTrue();
        assertThat(view.isSearchHmacSecretConfigured()).isTrue();
        assertThat(revealed.getSigningSecretBase64()).isEqualTo("signing-secret");
        assertThat(revealed.getSearchHmacSecretBase64()).isEqualTo("search-secret");
    }
}
