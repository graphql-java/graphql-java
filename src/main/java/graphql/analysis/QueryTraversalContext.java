package graphql.analysis;

import graphql.Internal;
import graphql.language.SelectionSetContainer;
import graphql.schema.GraphQLCompositeType;

/**
 * QueryTraversal helper class that maintains traversal context as
 * the query traversal algorithm traverses down the Selection AST
 */
@Internal
class QueryTraversalContext {
    
    private final GraphQLCompositeType type;
    private final QueryVisitorEnvironment environment;
    private final SelectionSetContainer selectionSetContainer;

    QueryTraversalContext(GraphQLCompositeType type, QueryVisitorEnvironment environment, SelectionSetContainer selectionSetContainer) {
        this.type = type;
        this.environment = environment;
        this.selectionSetContainer = selectionSetContainer;
    }

    public GraphQLCompositeType getType() {
        return type;
    }

    public QueryVisitorEnvironment getEnvironment() {
        return environment;
    }

    public SelectionSetContainer getSelectionSetContainer() {

        return selectionSetContainer;
    }
}
