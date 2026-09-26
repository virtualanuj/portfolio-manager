package com.portfoliomanager.importing;

/** Upper bounds for an uploaded CSV. The size cap follows the Vercel request body limit. */
public final class CsvLimits {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_ROWS = 5_000;

    private CsvLimits() {}
}
