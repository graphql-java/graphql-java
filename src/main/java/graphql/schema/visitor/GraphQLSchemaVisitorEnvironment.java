package graphql.schema.visitor;

import graphql.schema.GraphQLCodeRegistry;

public interface GraphQLSchemaVisitorEnvironment {

    GraphQLCodeRegistry.Builder getCodeRegistry();
}
