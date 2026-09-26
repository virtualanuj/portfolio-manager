package com.portfoliomanager.domain;

import java.util.Optional;

/**
 * Outcome of replaying a position's history. When a violation is present, {@code position} is the
 * state just before the offending transaction.
 */
public record ReplayResult(Position position, Optional<Violation> violation) {}
