package com.facecluster.facecluster_backend.service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

public final class EmbeddingCodec {

    private static final double MIN_NORM = 0.99;
    private static final double MAX_NORM = 1.01;

    private EmbeddingCodec() {
    }

    public static float[] decode(String base64, int dimensions) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("El embedding está vacío");
        }

        if (dimensions <= 0) {
            throw new IllegalArgumentException("Las dimensiones del embedding no son válidas");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("El embedding no es base64 válido");
        }

        if (bytes.length != dimensions * 4) {
            throw new IllegalArgumentException(
                    "El embedding tiene " + bytes.length + " bytes y se esperaban "
                            + dimensions * 4
            );
        }

        float[] vector = fromBytes(bytes);
        double squares = 0;

        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("El embedding tiene valores no finitos");
            }
            squares += (double) value * value;
        }

        double norm = Math.sqrt(squares);
        if (norm < MIN_NORM || norm > MAX_NORM) {
            throw new IllegalArgumentException(
                    "La norma del embedding está fuera de rango: " + norm
            );
        }

        return normalize(vector);
    }

    public static float[] normalize(float[] vector) {
        double squares = 0;

        for (float value : vector) {
            squares += (double) value * value;
        }

        double norm = Math.sqrt(squares);
        float[] result = new float[vector.length];

        for (int index = 0; index < vector.length; index++) {
            result[index] = (float) (vector[index] / norm);
        }

        return result;
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4)
                .order(ByteOrder.LITTLE_ENDIAN);

        for (float value : vector) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];

        for (int index = 0; index < vector.length; index++) {
            vector[index] = buffer.getFloat();
        }

        return vector;
    }

    public static double dot(float[] left, float[] right) {
        double sum = 0;

        for (int index = 0; index < left.length; index++) {
            sum += (double) left[index] * right[index];
        }

        return sum;
    }
}
