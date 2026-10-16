package com.money.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysBackupStreamTicketServiceTest {

    private final SysBackupStreamTicketService service = new SysBackupStreamTicketService();

    @Test
    void ticketCanBeConsumedOnlyOnce() {
        String ticket = service.issueTicket();

        assertThat(ticket).isNotBlank();
        assertThat(service.consumeTicket(ticket)).isTrue();
        assertThat(service.consumeTicket(ticket)).isFalse();
    }

    @Test
    void missingOrUnknownTicketIsRejected() {
        assertThat(service.consumeTicket(null)).isFalse();
        assertThat(service.consumeTicket("unknown")).isFalse();
    }
}
