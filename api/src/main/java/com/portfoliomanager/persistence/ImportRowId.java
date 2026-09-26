package com.portfoliomanager.persistence;

import java.io.Serializable;
import java.util.UUID;

public record ImportRowId(UUID batchId, int lineNo) implements Serializable {}
