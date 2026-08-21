package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * An object or interface type that directly implements interfaces.
 */
@ExperimentalApi
@NullMarked
public interface SchemaImplementingType extends SchemaFieldsContainer {

    /**
     * Returns the interfaces directly implemented by this type.
     *
     * <p>Elements obtained from an {@link ExecutableSchema} return
     * {@link SchemaInterface} instances.</p>
     *
     * @return the implemented interface types
     */
    List<? extends SchemaNamedType> getInterfaces();
}
