package graphql.schema.transform;

import graphql.PublicApi;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLSchemaElement;

/**
 * Container to pass additional context about a field to {@link VisibleFieldPredicate}.
 */
@PublicApi
public interface VisibleFieldPredicateEnvironment {

    GraphQLNamedSchemaElement getFieldDefinition();

    /**
     * Get the field's immediate parent node.
     *
     * @return parent node
     */
    GraphQLSchemaElement getParentElement();

    class VisibleFieldPredicateEnvironmentImpl implements VisibleFieldPredicateEnvironment {

        private final GraphQLNamedSchemaElement fieldDefinition;
        private final GraphQLSchemaElement parentElement;

        public VisibleFieldPredicateEnvironmentImpl(GraphQLNamedSchemaElement fieldDefinition,
                                                    GraphQLSchemaElement parentElement) {
            this.fieldDefinition = fieldDefinition;
            this.parentElement = parentElement;
        }

        @Override
        public GraphQLNamedSchemaElement getFieldDefinition() {
            return fieldDefinition;
        }

        @Override
        public GraphQLSchemaElement getParentElement() {
            return parentElement;
        }
    }
}
