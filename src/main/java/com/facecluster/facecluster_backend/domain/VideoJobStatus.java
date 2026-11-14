package com.facecluster.facecluster_backend.domain;

public final class VideoJobStatus {

    public static final String UPLOADED = "UPLOADED";
    public static final String QUEUED = "QUEUED";
    public static final String QUEUE_ERROR = "QUEUE_ERROR";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private VideoJobStatus() {
    }
}
