package com.facecluster.facecluster_backend.messaging;

public class InvalidWorkerEventException extends RuntimeException {

    public InvalidWorkerEventException(String message) {
        super(message);
    }
}
