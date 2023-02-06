package graphql.schema.visitor;

import graphql.schema.GraphQLCodeRegistry;

public interface SmartTypeVisitorEnvironment {

    GraphQLCodeRegistry.Builder getCodeRegistry();
}
