package com.portfoliomanager.importing;

/** The uploaded file cannot be read as CSV, or is outside the limits. */
public class CsvFormatException extends RuntimeException {

    private final int lineNumber;

    public CsvFormatException(String message) {
        this(message, 0);
    }

    public CsvFormatException(String message, int lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    /** The file line the problem was found on, or 0 when it is about the file as a whole. */
    public int getLineNumber() {
        return lineNumber;
    }
}
