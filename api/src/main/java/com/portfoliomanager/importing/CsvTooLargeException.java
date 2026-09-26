package com.portfoliomanager.importing;

/** The file exceeds the size limit; reported as HTTP 413 rather than as a format problem. */
public class CsvTooLargeException extends CsvFormatException {

    public CsvTooLargeException() {
        super("The file is larger than 2 MB");
    }
}
