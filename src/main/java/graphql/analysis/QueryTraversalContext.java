package graphql.analysis;

import graphql.Internal;
import graphql.language.SelectionSetContainer;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLOutputType;

/**
 * QueryTraversal helper class that maintains traversal context as
 * the query traversal algorithm traverses down the Selection AST
 */
@Internal
class QueryTraversalContext {

    private final GraphQLOutputType outputType;
    private final GraphQLCompositeType rawType;
    private final QueryVisitorFieldEnvironment environment;
    private final SelectionSetContainer selectionSetContainer;

    QueryTraversalContext(GraphQLOutputType outputType,
                          GraphQLCompositeType rawType,
                          QueryVisitorFieldEnvironment environment,
                          SelectionSetContainer selectionSetContainer) {
        this.outputType = outputType;
        this.rawType = rawType;
        this.environment = environment;
        this.selectionSetContainer = selectionSetContainer;
    }

    public GraphQLOutputType getOutputType() {
        return outputType;
    }

    public GraphQLCompositeType getRawType() {
        return rawType;
    }

    public QueryVisitorFieldEnvironment getEnvironment() {
        return environment;
    }

    public SelectionSetContainer getSelectionSetContainer() {

        return selectionSetContainer;
    }
}
