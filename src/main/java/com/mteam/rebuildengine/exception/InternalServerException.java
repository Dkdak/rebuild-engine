package com.mteam.sleerenthome.exception;

public class InternalServerException extends RuntimeException {
    public InternalServerException(String errorUpdateRoom) {
        super(errorUpdateRoom);
    }
}
