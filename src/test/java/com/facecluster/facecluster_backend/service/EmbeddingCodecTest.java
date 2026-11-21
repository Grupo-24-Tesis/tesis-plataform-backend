package com.facecluster.facecluster_backend.service;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingCodecTest {

    @Test
    void roundTripOfAUnitVector() {
        float[] vector = {1, 0, 0};
        String base64 = Base64.getEncoder().encodeToString(EmbeddingCodec.toBytes(vector));

        float[] decoded = EmbeddingCodec.decode(base64, 3);

        assertThat(decoded).containsExactly(1f, 0f, 0f);
        assertThat(EmbeddingCodec.fromBytes(EmbeddingCodec.toBytes(decoded)))
                .containsExactly(1f, 0f, 0f);
    }

    @Test
    void bytesAreLittleEndian() {
        byte[] bytes = EmbeddingCodec.toBytes(new float[] {1f});

        assertThat(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat())
                .isEqualTo(1f);
        assertThat(bytes).hasSize(4);
    }

    @Test
    void rejectsANormOfTwo() {
        String base64 = Base64.getEncoder()
                .encodeToString(EmbeddingCodec.toBytes(new float[] {2, 0, 0}));

        assertThatThrownBy(() -> EmbeddingCodec.decode(base64, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNaN() {
        String base64 = Base64.getEncoder()
                .encodeToString(EmbeddingCodec.toBytes(new float[] {Float.NaN, 0, 0}));

        assertThatThrownBy(() -> EmbeddingCodec.decode(base64, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAWrongLength() {
        String base64 = Base64.getEncoder()
                .encodeToString(EmbeddingCodec.toBytes(new float[] {1, 0, 0}));

        assertThatThrownBy(() -> EmbeddingCodec.decode(base64, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dotOfUnitVectors() {
        assertThat(EmbeddingCodec.dot(new float[] {1, 0}, new float[] {0.5f, 2}))
                .isEqualTo(0.5);
    }
}
