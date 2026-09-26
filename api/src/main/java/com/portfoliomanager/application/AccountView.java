package com.portfoliomanager.application;

import com.portfoliomanager.domain.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountView(UUID id, String name, AccountType type, Instant createdAt) {}
