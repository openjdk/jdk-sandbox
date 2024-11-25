package jdk.internal.util.json;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class is used to obtain a {@code JsonValue} by parsing data that
 * adheres to the JSON syntax defined in RFC 8259. For alternative ways to obtain
 * a {@code JsonValue}, see {@link #from(Object)} and its overloads. Additionally,
 * the underlying data value of the {@code JsonValue} can be produced by {@link
 * #to(JsonValue)} and its overloads.
 *
 * <p>
 * This parser utilizes deconstructor pattern matching. For simple JSON data,
 * the underlying data of a {@code JsonValue} can be retrieved using pattern matching,
 * such as:
 * {@snippet lang=java :
 *     JsonValue doc = JsonParser.parse("{ \"name\" : \"John\", \"age\" : 40 }");
 *     if (doc instanceof JsonObject(var keys) &&
 *         keys.get("name") instanceof JsonString(var name) &&
 *         keys.get("age") instanceof JsonNumber(var age)) { ... }
 * }
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *          Object Notation (JSON) Data Interchange Format
 */
public class Json {

    private Json(){}

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @param options parsing options
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in, Option... options) {
        Objects.requireNonNull(in);

        for (var o : options) {
            if (o == Option.Parse.EAGER_PARSING) {
                return JsonParser.parseImpl(new JsonDocumentInfo(in));
            }
        }
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @param options parsing options
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in, Option... options) {
        Objects.requireNonNull(in);

        for (var o : options) {
            if (o == Option.Parse.EAGER_PARSING) {
                return JsonParser.parseImpl(new JsonDocumentInfo(in));
            }
        }
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

    /**
     * {@return a {@code JsonValue} that represents the data type of {@code from}}
     *
     * @param from the data to produce the {@code JsonValue} from. May be null.
     * @throws IllegalArgumentException if {@code from} cannot be converted
     * to any of the {@code JsonValue} subtypes.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonValue from(Object from) {
        return switch (from) {
            case String str -> from(str);
            case Map<?, ?> map -> from(map);
            case List<?> list-> from(list);
            case Object[] array -> from(Arrays.asList(array));
            case Boolean bool -> from(bool);
            case Number num-> from(num);
            case null -> JsonNull.ofNull();
            default -> throw new IllegalArgumentException("Type not recognized.");
        };
    }

    /**
     * {@return the {@code JsonArray} created from the given
     * list of {@code Object}s} {@code Element}(s) in {@code from} should be any
     * value such that {@link #from(Object) JsonValue.from(element)} does not throw
     * an exception.
     *
     * @param from the list of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonArray from(List<?> from) {
        Objects.requireNonNull(from);
        return new JsonArrayImpl(from);
    }

    /**
     * {@return the {@code JsonBoolean} created from the given
     * {@code Boolean} object}
     *
     * @param from the given {@code Boolean}. Non-null.
     */
    public static JsonBoolean from(Boolean from) {
        Objects.requireNonNull(from);
        return from ? JsonBooleanImpl.TRUE : JsonBooleanImpl.FALSE;
    }

    /**
     * {@return the {@code JsonNumber} created from the given
     * {@code Number} object}
     *
     * @implNote If the given {@code Number} has too great a magnitude represent as a
     * {@code double}, it will throw an {@code IllegalArgumentException}.
     *
     * @param num the given {@code Number}. Non-null.
     * @throws IllegalArgumentException if the given {@code num} is out
     *          of accepted range.
     */
    public static JsonNumber from(Number num) {
        Objects.requireNonNull(num);
        return switch (num) {
            case Byte b -> new JsonNumberImpl(b);
            case Short s -> new JsonNumberImpl(s);
            case Integer i -> new JsonNumberImpl(i);
            case Long l -> new JsonNumberImpl(l);
            default -> {
                // non-integral types
                var d = num.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new IllegalArgumentException("Not a valid JSON number");
                }
                yield new JsonNumberImpl(d);
            }
        };
    }

    /**
     * {@return the {@code JsonObject} created from the given
     * Map of {@code Object}s} Keys should be strings, and values should be any
     * value such that {@link #from(Object) JsonValue.from(value)} does not throw
     * an exception.
     *
     * @param from the Map of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonObject from(Map<?, ?> from) {
        Objects.requireNonNull(from);
        return new JsonObjectImpl(from);
    }

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String} object}
     *
     * @param from the given {@code String}. Non-null.
     */
    public static JsonString from(String from) {
        Objects.requireNonNull(from);
        return new JsonStringLazyImpl(from);
    }

    /**
     * {@return an {@code Object} that represents the data of the passed {@code
     * JsonValue}}. The return type depends on the subtype of {@code from}.
     */
    public static Object to(JsonValue from) {
        return switch (from) {
            case JsonString jStr -> to(jStr);
            case JsonObject jMap -> to(jMap);
            case JsonArray jList-> to(jList);
            case JsonBoolean jBool -> to(jBool);
            case JsonNumber jNum-> to(jNum);
            case JsonNull jNull -> to(jNull);
        };
    }

    /**
     * {@return the {@code String} value corresponding to {@code from}}
     */
    public static String to(JsonString from) {
        return ((JsonStringImpl) from).to();
    }

    /**
     * {@return the map composed of {@code String} to {@code Object} corresponding to
     * {@code from}}
     */
    public static Map<String, Object> to(JsonObject from) {
        return ((JsonObjectImpl) from).to();
    }

    /**
     * {@return the list of {@code Object}s corresponding to {@code from}}
     */
    public static List<Object> to(JsonArray from) {
        return ((JsonArrayImpl) from).to();
    }

    /**
     * {@return the {@code Boolean} value corresponding to {@code from}}
     */
    public static boolean to(JsonBoolean from) {
        return ((JsonBooleanImpl) from).to();
    }

    /**
     * {@return the {@code Number} value corresponding to {@code from}}.
     * The Number subtype depends on the number value in {@code from}.
     */
    public static Number to(JsonNumber from) {
        return ((JsonNumberImpl) from).to();
    }

    /**
     * {@return {@code null}}
     */
    public static Object to(JsonNull from) {
        return ((JsonNullImpl) from).to();
    }
}
