package graphql.schema.transform;

import graphql.PublicApi;
import graphql.schema.GraphQLSchemaElement;

/**
 * Container to pass additional context about a field to {@link VisibleFieldPredicate}.
 */
@PublicApi
public interface VisibleFieldPredicateEnvironment {

    /**
     * Get the field's immediate parent node.
     *
     * @return parent node
     */
    GraphQLSchemaElement getParentNode();

    class VisibleFieldPredicateEnvironmentImpl implements VisibleFieldPredicateEnvironment {

        private final GraphQLSchemaElement parentNode;

        public VisibleFieldPredicateEnvironmentImpl(GraphQLSchemaElement parentNode) {
            this.parentNode = parentNode;
        }

        @Override
        public GraphQLSchemaElement getParentNode() {
            return parentNode;
        }
    }
}
