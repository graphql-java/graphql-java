package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;

/**
 * A GraphQLSchema can be viewed as a graph of GraphQLSchemaElement. Every node (vertex) of this graph implements
 * this interface.
 */
@PublicApi
public interface GraphQLSchemaElement {

    default List<GraphQLSchemaElement> getChildren() {
        return Collections.emptyList();
    }

    default SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return newSchemaElementChildrenContainer().build();
    }

    default GraphQLSchemaElement withNewChildren(SchemaElementChildrenContainer newChildren) {
        return this;
    }

    TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor);


    /**
     * No GraphQLSchemaElement implements `equals` because we need object identity
     * to treat a GraphQLSchema as an abstract graph.
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     */
    boolean equals(Object obj);

    /**
     * No GraphQLSchemaElement implements `equals/hashCode` because we need object identity
     * to treat a GraphQLSchema as an abstract graph.
     *
     * @return a hash code value for this object.
     */
    int hashCode();
}
