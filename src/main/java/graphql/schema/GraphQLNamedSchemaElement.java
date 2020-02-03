package graphql.schema;

import graphql.PublicApi;
import graphql.language.Node;

/**
 * A Schema element which has a name and also a description and AST Node which it is based on.
 */
@PublicApi
public interface GraphQLNamedSchemaElement extends GraphQLSchemaElement {

    String getName();

    String getDescription();

    /**
     * The AST {@link Node} this schema element is based on. Is null if the GraphQLSchema
     * is not based on a SDL document.
     * Some elements also have additional extension Nodes. See for example {@link GraphQLObjectType#getExtensionDefinitions()}
     *
     * @return Node which this element is based on. Can be null.
     */
    Node getDefinition();
}
