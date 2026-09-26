package com.portfoliomanager.web.dto;

import com.portfoliomanager.application.AccountView;
import com.portfoliomanager.domain.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String name, AccountType type, Instant createdAt) {

    public static AccountResponse from(AccountView view) {
        return new AccountResponse(view.id(), view.name(), view.type(), view.createdAt());
    }
}
