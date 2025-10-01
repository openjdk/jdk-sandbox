package jdk.incubator.json.impl;

/**
 * Used for JsonAssertionException error message building.
 */
public sealed interface JsonValueImpl
        permits JsonArrayImpl, JsonBooleanImpl, JsonNullImpl, JsonNumberImpl, JsonObjectImpl, JsonStringImpl {

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
}
