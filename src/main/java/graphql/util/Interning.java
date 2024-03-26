package graphql.util;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import graphql.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * Interner allowing object-identity comparison of key entities like field names.  This is useful on hotspot
 * areas like the engine where we look up field names a lot inside maps, and those maps use object identity first
 * inside the key lookup code.
 */
@Internal
public class Interning {

    private Interning() {
    }

    private static final Interner<String> INTERNER = Interners.newWeakInterner();

    public static @NotNull String intern(@NotNull String name) {
        return INTERNER.intern(name);
    }

}
