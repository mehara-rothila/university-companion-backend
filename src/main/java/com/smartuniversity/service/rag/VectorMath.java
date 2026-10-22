package com.smartuniversity.service.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Helpers for embedding vectors: normalising, comparing, and packing them into bytes for storage. */
public final class VectorMath {

    private VectorMath() {
    }

    /** Scales {@code vector} to length 1 in place and returns it. A zero vector is left unchanged. */
    public static float[] normalize(float[] vector) {
        double sumOfSquares = 0;
        for (float v : vector) {
            sumOfSquares += (double) v * v;
        }
        if (sumOfSquares == 0) {
            return vector;
        }
        float scale = (float) (1 / Math.sqrt(sumOfSquares));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    /** Dot product of two vectors. For normalised vectors this is their cosine similarity. */
    public static double dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector sizes differ: " + a.length + " vs " + b.length);
        }
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    /** Packs a vector as little-endian 32-bit floats, 4 bytes per value. */
    public static byte[] toBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asFloatBuffer().put(vector);
        return buffer.array();
    }

    /** Reverses {@link #toBytes(float[])}. */
    public static float[] fromBytes(byte[] bytes) {
        if (bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Byte length " + bytes.length + " is not a multiple of 4");
        }
        float[] vector = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector);
        return vector;
    }
}
