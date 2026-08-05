package graphql.schema.universe;

import graphql.Internal;
import graphql.introspection.Introspection;

import static graphql.Assert.assertNotNull;

/**
 * Imports the canonical GraphQL introspection type graph into a schema universe schema.
 */
@Internal
public final class SUIntrospectionSchemaType {

    private final SchemaUniverse universe;

    @Internal
    public SUIntrospectionSchemaType(SchemaUniverse universe) {
        this.universe = assertNotNull(universe);
    }

    /**
     * Imports and attaches the graph rooted at {@link Introspection#__Schema}.
     *
     * @param builder the target schema builder
     *
     * @return the imported introspection graph root
     */
    @Internal
    public SUObjectType addTo(SUSchemaBuilder builder) {
        return new SUImporter(universe).importIntrospectionSchemaType(
                assertNotNull(builder),
                Introspection.__Schema);
    }
}
