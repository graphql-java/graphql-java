package graphql.schema;

public interface GraphQLInputObjectFieldDataTransformer {

    Object transform(Object value);
}
