package graphql.schema;

import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.Collections;
import java.util.List;

import static graphql.schema.SchemaElementChildrenContainer.newSchemaElementChildrenContainer;

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
}
