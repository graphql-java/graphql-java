package graphql.schema;

import graphql.Internal;
import graphql.util.FpKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.BaseStream;

import static graphql.Assert.assertTrue;

@Internal
@NullMarked
public class DefaultValueSupport {

    public static void assertValidDefaultValue(@Nullable Object value) {
        assertValidDefaultValue(value, null);
    }

    private static void assertValidDefaultValue(@Nullable Object value, @Nullable Set<Object> visited) {
        if (value == null) {
            return;
        }
        assertTrue(!(value instanceof Iterator) && !(value instanceof BaseStream),
                "Default values must not contain an Iterator or a Stream");
        if (!(value instanceof Map) && !FpKit.isIterableOrArray(value)) {
            return;
        }
        if (visited == null) {
            visited = Collections.newSetFromMap(new IdentityHashMap<>());
        }
        if (!visited.add(value)) {
            return;
        }
        Iterable<?> values = value instanceof Map ? ((Map<?, ?>) value).values() : FpKit.toIterable(value);
        for (Object item : values) {
            assertValidDefaultValue(item, visited);
        }
    }
}
