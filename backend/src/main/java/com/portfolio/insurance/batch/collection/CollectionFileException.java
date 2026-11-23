package com.portfolio.insurance.batch.collection;

/** A bank result file that cannot be applied as a whole; the message is shown on the collection files screen. */
public class CollectionFileException extends RuntimeException {

    public CollectionFileException(String message) {
        super(message);
    }
}
