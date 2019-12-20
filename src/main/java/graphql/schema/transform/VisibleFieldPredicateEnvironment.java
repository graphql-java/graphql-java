package graphql.schema.transform;

import graphql.PublicApi;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchemaElement;

/**
 * Container to pass additional context about a field to {@link VisibleFieldPredicate}.
 */
@PublicApi
public interface VisibleFieldPredicateEnvironment {

    GraphQLFieldDefinition getFieldDefinition();

    /**
     * Get the field's immediate parent node.
     *
     * @return parent node
     */
    GraphQLSchemaElement getParentElement();

    class VisibleFieldPredicateEnvironmentImpl implements VisibleFieldPredicateEnvironment {

        private final GraphQLFieldDefinition fieldDefinition;
        private final GraphQLSchemaElement parentElement;

        public VisibleFieldPredicateEnvironmentImpl(GraphQLFieldDefinition fieldDefinition,
                                                    GraphQLSchemaElement parentElement) {
            this.fieldDefinition = fieldDefinition;
            this.parentElement = parentElement;
        }

        @Override
        public GraphQLFieldDefinition getFieldDefinition() {
            return fieldDefinition;
        }

        @Override
        public GraphQLSchemaElement getParentElement() {
            return parentElement;
        }
    }
}
