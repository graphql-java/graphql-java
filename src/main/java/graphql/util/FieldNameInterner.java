package graphql.util;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import graphql.Internal;

/**
 * Interner allowing object identity based comparison of field names.
 */
@Internal
public class FieldNameInterner {

    private FieldNameInterner() {
    }

    public static final Interner<String> INTERNER = Interners.newWeakInterner();

    public static String intern(String name) {
        return INTERNER.intern(name);
    }

}
