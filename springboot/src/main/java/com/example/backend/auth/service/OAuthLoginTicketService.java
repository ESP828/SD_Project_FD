package com.example.backend.auth.service;

import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthLoginTicketService {

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final long validityMillis;

    public OAuthLoginTicketService(
            @Value("${oauth.ticket-validity:120000}") long validityMillis
    ) {
        if (validityMillis <= 0) {
            throw new IllegalStateException("OAuth login ticket 만료시간은 0보다 커야 합니다.");
        }
        this.validityMillis = validityMillis;
    }

    public String issue(AuthTokenResponse tokenResponse) {
        removeExpiredTickets();
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        tickets.put(value, new Ticket(tokenResponse, Instant.now().toEpochMilli() + validityMillis));
        return value;
    }

    public AuthTokenResponse consume(String value) {
        Ticket ticket = tickets.remove(value);
        if (ticket == null || ticket.expiresAt() < Instant.now().toEpochMilli()) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_TICKET);
        }
        return ticket.tokenResponse();
    }

    private void removeExpiredTickets() {
        long now = Instant.now().toEpochMilli();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private record Ticket(AuthTokenResponse tokenResponse, long expiresAt) {
    }
}
