package graphql.analysis;

import graphql.Internal;
import graphql.schema.GraphQLCompositeType;

/**
 * QueryTraversal helper class that maintains traversal context as
 * the query traversal algorithm traverses down the Selection AST
 */
@Internal
class QueryTraversalContext {
    
    private final GraphQLCompositeType type;
    private final QueryVisitorEnvironment environment;

    QueryTraversalContext(GraphQLCompositeType type, QueryVisitorEnvironment environment) {
        this.type = type;
        this.environment = environment;
    }

    public GraphQLCompositeType getType() {
        return type;
    }

    public QueryVisitorEnvironment getEnvironment() {
        return environment;
    }    
}
