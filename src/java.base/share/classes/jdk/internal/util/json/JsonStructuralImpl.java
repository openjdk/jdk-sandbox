package jdk.internal.util.json;

/**
 * Used for JsonAssertionException error message building.
 */
public sealed interface JsonStructuralImpl permits JsonArrayImpl, JsonObjectImpl {

    /**
     * Return access to the underlying document, if it was parsed.
     * Otherwise, return null.
     */
    char[] doc();

    /**
     * Return the associated offset of the JsonValue in the document, if it was parsed.
     * Otherwise, return -1.
     */
    int offset();

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
