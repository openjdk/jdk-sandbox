package jdk.internal.util.json;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class is used to obtain a {@link JsonValue} by using {@link
 * #fromUntyped(Object)} and its overloads. Additionally, the underlying data
 * value of the {@code JsonValue} can be produced by {@link #toUntyped(JsonValue)}
 * and its overloads. See {@link JsonParser} for producing {@code JsonValue}s by
 * parsing JSON strings.
 */
public class Json {

    private Json(){}

    /**
     * {@return a {@code JsonValue} that represents the data type of {@code from}}
     *
     * @param from the data to produce the {@code JsonValue} from. May be null.
     * @throws IllegalArgumentException if {@code from} cannot be converted
     * to any of the {@code JsonValue} subtypes.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonValue fromUntyped(Object from) {
        return switch (from) {
            case String str -> fromUntyped(str);
            case Map<?, ?> map -> fromUntyped(map);
            case List<?> list-> fromUntyped(list);
            case Object[] array -> fromUntyped(Arrays.asList(array));
            case Boolean bool -> fromUntyped(bool);
            case Number num-> fromUntyped(num);
            case null -> JsonNull.ofNull();
            default -> throw new IllegalArgumentException("Type not recognized.");
        };
    }

    /**
     * {@return the {@code JsonArray} created from the given
     * list of {@code Object}s} {@code Element}(s) in {@code from} should be any
     * value such that {@link #fromUntyped(Object) JsonValue.fromUntyped(element)} does not throw
     * an exception.
     *
     * @param from the list of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonArray fromUntyped(List<?> from) {
        Objects.requireNonNull(from);
        return new JsonArrayImpl(from);
    }

    /**
     * {@return the {@code JsonBoolean} created from the given
     * {@code Boolean} object}
     *
     * @param from the given {@code Boolean}. Non-null.
     */
    public static JsonBoolean fromUntyped(Boolean from) {
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
    public static JsonNumber fromUntyped(Number num) {
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
     * value such that {@link #fromUntyped(Object) JsonValue.fromUntyped(value)} does not throw
     * an exception.
     *
     * @param from the Map of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonObject fromUntyped(Map<?, ?> from) {
        Objects.requireNonNull(from);
        return new JsonObjectImpl(from);
    }

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String} object}
     *
     * @param from the given {@code String}. Non-null.
     */
    public static JsonString fromUntyped(String from) {
        Objects.requireNonNull(from);
        return new JsonStringLazyImpl(from);
    }

    /**
     * {@return an {@code Object} that represents the data of the passed {@code
     * JsonValue}}. The return type depends on the subtype of {@code from}.
     */
    public static Object toUntyped(JsonValue from) {
        return switch (from) {
            case JsonString jStr -> toUntyped(jStr);
            case JsonObject jMap -> toUntyped(jMap);
            case JsonArray jList-> toUntyped(jList);
            case JsonBoolean jBool -> toUntyped(jBool);
            case JsonNumber jNum-> toUntyped(jNum);
            case JsonNull jNull -> toUntyped(jNull);
        };
    }

    /**
     * {@return the {@code String} value corresponding to {@code from}}
     */
    public static String toUntyped(JsonString from) {
        return ((JsonStringImpl) from).toUntyped();
    }

    /**
     * {@return the map composed of {@code String} to {@code Object} corresponding to
     * {@code from}}
     */
    public static Map<String, Object> toUntyped(JsonObject from) {
        return ((JsonObjectImpl) from).toUntyped();
    }

    /**
     * {@return the list of {@code Object}s corresponding to {@code from}}
     */
    public static List<Object> toUntyped(JsonArray from) {
        return ((JsonArrayImpl) from).toUntyped();
    }

    /**
     * {@return the {@code Boolean} value corresponding to {@code from}}
     */
    public static boolean toUntyped(JsonBoolean from) {
        return ((JsonBooleanImpl) from).toUntyped();
    }

    /**
     * {@return the {@code Number} value corresponding to {@code from}}.
     * The Number subtype depends on the number value in {@code from}.
     */
    public static Number toUntyped(JsonNumber from) {
        return ((JsonNumberImpl) from).toUntyped();
    }

    /**
     * {@return {@code null}}
     */
    public static Object toUntyped(JsonNull from) {
        return ((JsonNullImpl) from).toUntyped();
    }
}
