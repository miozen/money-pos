package com.money.platform.runtime.database;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedMariaDbGuardianCharacterizationTest {

    @Test
    void recognizesSameDataDirectoryAcrossSeparatorAndCaseVariants() {
        assertThat(EmbeddedMariaDbGuardian.sameDataDirectory("D:\\WanXiang\\POS-Data\\db_data\\", "d:\\wanxiang\\pos-data\\db_data"))
                .isTrue();
        assertThat(EmbeddedMariaDbGuardian.sameDataDirectory("/var/lib/mariadb/", "/var/lib/mariadb"))
                .isTrue();
        assertThat(EmbeddedMariaDbGuardian.sameDataDirectory("/var/lib/other", "/var/lib/mariadb"))
                .isFalse();
    }

    @Test
    void detectsAnOccupiedLoopbackPortWithoutStartingMariaDb() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            assertThat(EmbeddedMariaDbGuardian.isPortInUse(server.getLocalPort())).isTrue();
        }
    }
}
