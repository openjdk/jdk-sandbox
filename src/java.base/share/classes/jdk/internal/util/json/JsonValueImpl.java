package jdk.internal.util.json;

/**
 * Base interface for our JsonValue implementations, such that we can provide a row and col
 * position for JsonAssertionError.
 */
public interface JsonValueImpl {

    /**
     * Return the row where the JsonValue occurs in the document, if it was parsed.
     * Otherwise, return -1.
     */
   int row();

    /**
     * Return the row where the JsonValue occurs in the document, if it was parsed.
     * Otherwise, return -1.
     */
    int col();
}
