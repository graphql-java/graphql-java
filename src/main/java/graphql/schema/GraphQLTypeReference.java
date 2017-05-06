package graphql.schema;


import static graphql.Assert.assertValidName;

/**
 * A special type to allow a object/interface types to reference itself. It's replaced with the real type
 * object when the schema is build.
 *
 */
public class GraphQLTypeReference implements GraphQLType, GraphQLOutputType, GraphQLInputType {

    private final String name;

    public GraphQLTypeReference(String name) {
    	assertValidName(name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
