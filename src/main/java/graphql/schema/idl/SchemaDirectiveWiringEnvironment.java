package graphql.schema.idl;

import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

import java.util.Optional;

public interface SchemaDirectiveWiringEnvironment<T extends GraphQLDirectiveContainer> {

    T getTypeElement();

    <P> Optional<P> getParent();

    TypeDefinitionRegistry getRegistry();

    GraphQLDirective getDirective();

}
