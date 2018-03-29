package graphql.schema.idl;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

/**
 * {@link graphql.schema.idl.SchemaDirectiveWiring} is passed this object as parameters
 * when it builds out behaviour
 *
 * @param <T> the type of the object in play
 */
public interface SchemaDirectiveWiringEnvironment<T extends GraphQLDirectiveContainer> {

    /**
     * @return the runtime element in play
     */
    T getElement();

    /**
     * @return the directive that is being examined
     */
    GraphQLDirective getDirective();

    /**
     * The node hierarchy depends on the element in question.  For example {@link graphql.language.ObjectTypeDefinition} nodes
     * have no parent, however a {@link graphql.language.Argument} might be on a {@link graphql.language.FieldDefinition}
     * which in turn might be on a {@link graphql.language.ObjectTypeDefinition} say
     *
     * @return hierarchical graphql language node information
     */
    NodeInfo getNodeInfo();

    /**
     * @return the type registry
     */
    TypeDefinitionRegistry getRegistry();

}
