package graphql.schema;


import static graphql.Assert.assertValidName;

/**
 * A special type to allow a object/interface types to reference itself. It's replaced with the real type
 * object when the schema is built.
 */
public class GraphQLTypeReference implements GraphQLType, GraphQLOutputType, GraphQLInputType {

    /**
     * A factory method for creating type references so that when used with static imports allows
     * more readable code such as
     * {@code .type(typeRef(GraphQLString)) }
     *
     * @param typeName the name of the type to reference
     *
     * @return a GraphQLTypeReference of that named type
     */
    public static GraphQLTypeReference typeRef(String typeName) {
        return new GraphQLTypeReference(typeName);
    }

    private final String name;

    public GraphQLTypeReference(String name) {
        assertValidName(name);
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
