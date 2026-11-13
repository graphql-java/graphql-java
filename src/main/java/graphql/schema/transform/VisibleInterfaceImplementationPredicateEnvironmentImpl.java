package graphql.schema.transform;

import graphql.Internal;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInterfaceType;

import static graphql.Assert.assertNotNull;

@Internal
public final class VisibleInterfaceImplementationPredicateEnvironmentImpl implements VisibleInterfaceImplementationPredicateEnvironment {

    private final GraphQLImplementingType implementingType;
    private final GraphQLInterfaceType interfaceType;

    public VisibleInterfaceImplementationPredicateEnvironmentImpl(GraphQLImplementingType implementingType,
                                                                   GraphQLInterfaceType interfaceType) {
        this.implementingType = assertNotNull(implementingType);
        this.interfaceType = assertNotNull(interfaceType);
    }

    @Override
    public GraphQLImplementingType getImplementingType() {
        return implementingType;
    }

    @Override
    public GraphQLInterfaceType getInterfaceType() {
        return interfaceType;
    }
}
