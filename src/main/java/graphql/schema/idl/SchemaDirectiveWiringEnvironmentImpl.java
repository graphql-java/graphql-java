package graphql.schema.idl;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

import java.util.Map;

public class SchemaDirectiveWiringEnvironmentImpl<T extends GraphQLDirectiveContainer> implements SchemaDirectiveWiringEnvironment<T> {

    private final T element;
    private final GraphQLDirective directive;
    private final NodeInfo nodeInfo;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final Map<String,Object> context;

    public SchemaDirectiveWiringEnvironmentImpl(T element, GraphQLDirective directive, NodeInfo nodeInfo, TypeDefinitionRegistry typeDefinitionRegistry, Map<String, Object> context) {
        this.element = element;
        this.nodeInfo = nodeInfo;
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
    public NodeInfo getNodeInfo() {
        return nodeInfo;
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
