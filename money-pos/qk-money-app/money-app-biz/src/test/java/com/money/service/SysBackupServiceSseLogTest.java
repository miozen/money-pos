package com.money.service;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SysBackupServiceSseLogTest {

    @Test
    void serializesQuotesAndLineBreaksAsValidJsonForSseClients() throws Exception {
        SysBackupService service = new SysBackupService(null);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        emitters(service).add(emitter);

        Method sendLog = SysBackupService.class.getDeclaredMethod("sendLog", String.class, String.class);
        sendLog.setAccessible(true);
        sendLog.invoke(service, "ERROR", "mysqldump said \"disk full\"\nretry later");

        assertThat(JSONUtil.parseObj(emitter.payload).getStr("level")).isEqualTo("ERROR");
        assertThat(JSONUtil.parseObj(emitter.payload).getStr("msg"))
                .isEqualTo("mysqldump said \"disk full\"\nretry later");
        assertThat(JSONUtil.parseObj(emitter.payload).getStr("time")).isNotBlank();
    }

    @SuppressWarnings("unchecked")
    private List<SseEmitter> emitters(SysBackupService service) throws Exception {
        Field field = SysBackupService.class.getDeclaredField("emitters");
        field.setAccessible(true);
        return (List<SseEmitter>) field.get(service);
    }

    private static final class CapturingSseEmitter extends SseEmitter {
        private String payload;

        @Override
        public void send(Object object) {
            payload = String.valueOf(object);
        }
    }
}
