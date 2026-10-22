package com.smartuniversity.service.rag;

/**
 * A piece of a document small enough to embed and to quote back to the model.
 *
 * @param pageNumber 1-based page the text came from
 * @param index      position of the chunk within the whole document, starting at 0
 * @param text       the chunk's text
 */
public record TextChunk(int pageNumber, int index, String text) {
}
