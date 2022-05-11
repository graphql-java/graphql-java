package graphql.schema;

import graphql.PublicApi;
import graphql.language.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Schema element which has a name and also a description and AST Node which it is based on.
 */
@PublicApi
public interface GraphQLNamedSchemaElement extends GraphQLSchemaElement {

    /**
     * @return the name of this element.  This cant be null
     */
    @NotNull
    String getName();

    /**
     * @return the description of this element.  This can be null
     */
    @Nullable
    String getDescription();

    /**
     * The AST {@link Node} this schema element is based on. Is null if the GraphQLSchema
     * is not based on a SDL document.
     * Some elements also have additional extension Nodes. See for example {@link GraphQLObjectType#getExtensionDefinitions()}
     *
     * @return Node which this element is based on. Can be null.
     */
    @Nullable
    Node getDefinition();
}
