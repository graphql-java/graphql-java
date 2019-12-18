package graphql.schema.transform;


import graphql.schema.GraphQLFieldDefinition;
import java.util.function.Predicate;

/**
 * Predicate used during a {@link FieldVisibilitySchemaTransformation} to test whether a field should be visible.
 */
public interface VisibleFieldPredicate extends Predicate<GraphQLFieldDefinition> {

    /**
     * Test whether a field should be visible. Provided as a more descriptive "test" method that describes exactly
     * what a positive result of "test" should mean.
     *
     * @param definition field definition
     * @return true if visible
     */
    boolean isVisible(GraphQLFieldDefinition definition);

    @Override
    default boolean test(GraphQLFieldDefinition definition) {
        return isVisible(definition);
    }
}
