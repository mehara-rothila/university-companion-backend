package com.smartuniversity.service.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorMathTest {

    @Test
    void normalizeScalesToUnitLength() {
        float[] v = VectorMath.normalize(new float[]{3, 4});

        assertArrayEquals(new float[]{0.6f, 0.8f}, v, 1e-6f);
        assertEquals(1.0, VectorMath.dot(v, v), 1e-6);
    }

    @Test
    void normalizeLeavesZeroVectorAlone() {
        assertArrayEquals(new float[]{0, 0}, VectorMath.normalize(new float[]{0, 0}));
    }

    @Test
    void dotOfUnitVectorsIsCosineSimilarity() {
        float[] x = VectorMath.normalize(new float[]{1, 0});
        float[] diagonal = VectorMath.normalize(new float[]{1, 1});

        assertEquals(Math.sqrt(0.5), VectorMath.dot(x, diagonal), 1e-6);
        assertEquals(0.0, VectorMath.dot(x, new float[]{0, 1}), 1e-9);
    }

    @Test
    void dotRejectsDifferentSizes() {
        assertThrows(IllegalArgumentException.class, () -> VectorMath.dot(new float[2], new float[3]));
    }

    @Test
    void bytesRoundTrip() {
        float[] original = {0.25f, -1.5f, 3.75f, Float.MIN_VALUE};

        byte[] bytes = VectorMath.toBytes(original);

        assertEquals(original.length * 4, bytes.length);
        assertArrayEquals(original, VectorMath.fromBytes(bytes));
    }

    @Test
    void fromBytesRejectsPartialFloats() {
        assertThrows(IllegalArgumentException.class, () -> VectorMath.fromBytes(new byte[5]));
    }
}
