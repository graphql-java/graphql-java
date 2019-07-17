package graphql.schema;

import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

public interface GraphQLSchemaElement {

    default List<GraphQLSchemaElement> getChildren() {
        return Collections.emptyList();
    }

    default List<GraphQLSchemaElement> getChildrenWithTypeReferences() {
        return getChildren();
    }

    TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor);
}
