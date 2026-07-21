package graphql.schema.transform;

import graphql.PublicApi;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInterfaceType;

/**
 * Provides the two types participating in an interface implementation relationship.
 */
@PublicApi
public interface VisibleInterfaceImplementationPredicateEnvironment {

    /**
     * @return the object or interface that implements another interface
     */
    GraphQLImplementingType getImplementingType();

    /**
     * @return the implemented interface
     */
    GraphQLInterfaceType getInterfaceType();
}
