package graphql.schema;


import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

/**
 * All types in graphql have a name
 */
@PublicApi
public interface GraphQLType {
    /**
     * @return the name of the type which MUST fit within the regular expression {@code [_A-Za-z][_0-9A-Za-z]*}
     */
    String getName();

    /**
     * @return returns all types directly associated with this node
     */
    default List<GraphQLType> getChildren() { return Collections.emptyList(); }

    TraversalControl accept(TraverserContext<GraphQLType> context, TypeVisitor visitor);
}
