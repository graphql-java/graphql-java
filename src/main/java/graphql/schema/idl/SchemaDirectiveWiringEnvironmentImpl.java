package graphql.schema.idl;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

public class SchemaDirectiveWiringEnvironmentImpl<T extends GraphQLDirectiveContainer> implements SchemaDirectiveWiringEnvironment<T> {

    private final T element;
    private final NodeInfo nodeInfo;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLDirective directive;

    public SchemaDirectiveWiringEnvironmentImpl(T element, NodeInfo nodeInfo, TypeDefinitionRegistry typeDefinitionRegistry, GraphQLDirective directive) {
        this.element = element;
        this.nodeInfo = nodeInfo;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.directive = directive;
    }

    @Override
    public T getTypeElement() {
        return element;
    }

    @Override
    public GraphQLDirective getDirective() {
        return directive;
    }

    @Override
    public NodeInfo getNodeInfo() {
        return nodeInfo;
    }

    @Override
    public TypeDefinitionRegistry getRegistry() {
        return typeDefinitionRegistry;
    }

}
