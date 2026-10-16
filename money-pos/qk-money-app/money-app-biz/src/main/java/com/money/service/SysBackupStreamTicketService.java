package com.money.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges authenticated backup-console setup to the headerless browser EventSource transport.
 * Tickets are short lived and consumed atomically before an SSE emitter is created.
 */
@Service
public class SysBackupStreamTicketService {

    private static final long TICKET_TTL_MILLIS = 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Long> pendingTickets = new ConcurrentHashMap<>();

    public String issueTicket() {
        purgeExpiredTickets();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pendingTickets.put(ticket, System.currentTimeMillis() + TICKET_TTL_MILLIS);
        return ticket;
    }

    public boolean consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return false;
        }
        Long expiresAt = pendingTickets.remove(ticket);
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }

    private void purgeExpiredTickets() {
        long now = System.currentTimeMillis();
        pendingTickets.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
