package graphql.schema.universe;

import graphql.ExperimentalApi;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertTrue;

@ExperimentalApi
public final class SUDirective extends SUVertex implements SUAppliedDirectiveContainer {

    private final boolean repeatable;
    private final long locationMask;

    @Internal
    public SUDirective(
            int id,
            int nameId,
            String name,
            @Nullable String description,
            boolean repeatable,
            Set<DirectiveLocation> validLocations) {
        super(id, nameId, SUVertexKind.DIRECTIVE, name, description);
        this.repeatable = repeatable;
        this.locationMask = packLocations(assertNotNull(validLocations));
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public EnumSet<DirectiveLocation> validLocations() {
        EnumSet<DirectiveLocation> result = EnumSet.noneOf(DirectiveLocation.class);
        for (DirectiveLocation location : DirectiveLocation.values()) {
            if ((locationMask & (1L << location.ordinal())) != 0) {
                result.add(location);
            }
        }
        return result;
    }

    private static long packLocations(Set<DirectiveLocation> validLocations) {
        assertTrue(DirectiveLocation.values().length <= Long.SIZE,
                "Too many directive locations to pack into a long");
        long result = 0;
        for (DirectiveLocation location : validLocations) {
            result |= 1L << assertNotNull(location).ordinal();
        }
        return result;
    }
}
