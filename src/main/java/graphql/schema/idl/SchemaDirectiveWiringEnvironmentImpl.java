package graphql.schema.idl;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

import java.util.Optional;

public class SchemaDirectiveWiringEnvironmentImpl<T extends GraphQLDirectiveContainer> implements SchemaDirectiveWiringEnvironment<T> {

    private final T element;
    private final Object parent;
    private final TypeDefinitionRegistry typeDefinitionRegistry;
    private final GraphQLDirective directive;

    public SchemaDirectiveWiringEnvironmentImpl(T element, Object parent, TypeDefinitionRegistry typeDefinitionRegistry, GraphQLDirective directive) {
        this.element = element;
        this.parent = parent;
        this.typeDefinitionRegistry = typeDefinitionRegistry;
        this.directive = directive;
    }

    @Override
    public T getTypeElement() {
        return element;
    }

    @Override
    public <P> Optional<P> getParent() {
        @SuppressWarnings("unchecked")
        P parentObj = (P) parent;
        return Optional.ofNullable(parentObj);
    }

    @Override
    public TypeDefinitionRegistry getRegistry() {
        return typeDefinitionRegistry;
    }

    @Override
    public GraphQLDirective getDirective() {
        return directive;
    }

}
