package graphql.schema;


import graphql.TypeResolutionEnvironment;

public interface TypeResolver {


    GraphQLObjectType getType(TypeResolutionEnvironment env);

}
