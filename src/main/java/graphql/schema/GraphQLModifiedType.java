package graphql.schema;


public interface GraphQLModifiedType extends GraphQLType {

    GraphQLType getWrappedType();
}
