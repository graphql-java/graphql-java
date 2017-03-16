package graphql.schema;


import graphql.TypeResolutionEnvironment;

public interface TypeResolver {


    default GraphQLObjectType getType(TypeResolutionEnvironment env) {
        return getType(env.getObject());
    }

    GraphQLObjectType getType(Object object);

}
