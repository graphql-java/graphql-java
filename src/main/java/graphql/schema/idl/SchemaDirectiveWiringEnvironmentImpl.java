package graphql.schema.idl;

import graphql.Internal;
import graphql.language.NamedNode;
import graphql.language.NodeParentTree;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

import java.util.Map;

@Internal
public class SchemaDirectiveWiringEnvironmentImpl<T extends GraphQLDirectiveContainer> implements SchemaDirectiveWiringEnvironment<T> {

    private final T element;
    private final GraphQLDirective directive;
    private final NodeParentTree<NamedNode> nodeParentTree;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final Map<String, Object> context;

    public SchemaDirectiveWiringEnvironmentImpl(T element, GraphQLDirective directive, NodeParentTree<NamedNode> nodeParentTree, TypeDefinitionRegistry typeDefinitionRegistry, Map<String, Object> context) {
        this.element = element;
        this.nodeParentTree = nodeParentTree;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.directive = directive;
        this.context = context;
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
}
