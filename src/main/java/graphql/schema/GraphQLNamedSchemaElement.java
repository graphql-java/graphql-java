package graphql.schema;

import graphql.PublicApi;
import graphql.language.Node;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A Schema element which has a name and also a description and AST Node which it is based on.
 */
@PublicApi
public interface GraphQLNamedSchemaElement extends GraphQLSchemaElement {

    /**
     * @return the name of this element.  This cant be null
     */
    @NonNull
    String getName();

    /**
     * @return the description of this element.  This can be null
     */
    @Nullable
    String getDescription();

    /**
     * The original AST {@link Node} this schema element is based on. It can be null when there is no
     * corresponding source node or AST capture was disabled during schema generation.
     * Some elements also have additional extension nodes. See for example {@link GraphQLObjectType#getExtensionDefinitions()}.
     * <p>
     * Copying or transforming an element retains its original AST definition and extension definitions by reference,
     * unless they are explicitly replaced or cleared. The AST nodes are not updated to reflect changes to the runtime
     * element, such as renaming it or changing its type.
     *
     * @return Node which this element is based on. Can be null.
     */
    @Nullable
    Node getDefinition();
}
