package graphql.schema.idl;

import graphql.PublicApi;
import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphqlElementParentTree;

import java.util.Map;

/**
 * {@link graphql.schema.idl.SchemaDirectiveWiring} is passed this object as parameters
 * when it builds out behaviour
 *
 * @param <T> the type of the object in play
 */
@PublicApi
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
    NodeParentTree<NamedNode> getNodeParentTree();

    /**
     * The type hierarchy depends on the element in question.  For example {@link graphql.schema.GraphQLObjectType} elements
     * have no parent, however a {@link graphql.schema.GraphQLArgument} might be on a {@link graphql.schema.GraphQLFieldDefinition}
     * which in turn might be on a {@link graphql.schema.GraphQLObjectType} say
     *
     * @return hierarchical graphql type information
     */
    GraphqlElementParentTree getElementParentTree();

    /**
     * @return the type registry
     */
    TypeDefinitionRegistry getRegistry();

    /**
     * @return a mpa that can be used by implementors to hold context during the SDL build process
     */
    Map<String, Object> getBuildContext();

    /**
     * @return a builder of the current code registry builder
     */
    GraphQLCodeRegistry.Builder getCodeRegistry();

    /**
     * @return a {@link graphql.schema.GraphQLFieldsContainer} when the element is contained with a fields container
     */
    GraphQLFieldsContainer getFieldsContainer();

    /**
     * @return a {@link GraphQLFieldDefinition} when the element is one or is contained within one
     */
    GraphQLFieldDefinition getFieldDefinition();

}
