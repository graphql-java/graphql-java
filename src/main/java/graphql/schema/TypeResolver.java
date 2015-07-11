package graphql.schema;


public interface TypeResolver {


    GraphQLObjectType getType(Object object);

}
