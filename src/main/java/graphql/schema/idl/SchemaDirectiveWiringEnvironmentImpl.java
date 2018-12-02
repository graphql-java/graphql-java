package graphql.schema.idl;

import graphql.Internal;
import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLFieldsContainer;

import java.util.Map;

@Internal
public class SchemaDirectiveWiringEnvironmentImpl<T extends GraphQLDirectiveContainer> implements SchemaDirectiveWiringEnvironment<T> {

    private final T element;
    private final GraphQLDirective directive;
    private final NodeParentTree<NamedNode> nodeParentTree;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final Map<String, Object> context;
    private final GraphQLCodeRegistry.Builder codeRegistry;
    private final GraphQLFieldsContainer fieldsContainer;

    public SchemaDirectiveWiringEnvironmentImpl(T element, GraphQLDirective directive, NodeParentTree<NamedNode> nodeParentTree, TypeDefinitionRegistry typeDefinitionRegistry, Map<String, Object> context, GraphQLCodeRegistry.Builder codeRegistry, GraphQLFieldsContainer fieldsContainer) {
        this.element = element;
        this.nodeParentTree = nodeParentTree;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.directive = directive;
        this.context = context;
        this.codeRegistry = codeRegistry;
        this.fieldsContainer = fieldsContainer;
    }

    @Override
    public T getElement() {
        return element;
    }

    @Override
    public GraphQLDirective getDirective() {
        return directive;
    }

    @Override
    public NodeParentTree<NamedNode> getNodeParentTree() {
        return nodeParentTree;
    }

    @Override
    public TypeDefinitionRegistry getRegistry() {
        return typeDefinitionRegistry;
    }

    @Override
    public Map<String, Object> getBuildContext() {
        return context;
    }

    @Override
    public GraphQLCodeRegistry.Builder getCodeRegistry() {
        return codeRegistry;
    }

    @Override
    public GraphQLFieldsContainer getFieldsContainer() {
        return fieldsContainer;
    }
}
