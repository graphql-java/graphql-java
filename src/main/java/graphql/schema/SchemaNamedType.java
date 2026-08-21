package graphql.schema;

import graphql.ExperimentalApi;
import graphql.language.TypeDefinition;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A named type exposed by an {@link ExecutableSchema}.
 */
@ExperimentalApi
@NullMarked
public interface SchemaNamedType
        extends SchemaType, SchemaDirectiveContainer {

    /**
     * @return source extension definitions for this type
     */
    default List<? extends TypeDefinition<?>> getExtensionDefinitions() {
        return List.of();
    }
}
