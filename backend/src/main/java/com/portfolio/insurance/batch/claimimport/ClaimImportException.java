package com.portfolio.insurance.batch.claimimport;

/** A CSV line that cannot be imported. The step skips the line and the message is stored in CLAIM_IMPORT_ERROR. */
class ClaimImportException extends RuntimeException {

    ClaimImportException(String message) {
        super(message);
    }
}
