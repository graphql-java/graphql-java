package graphql.schema;


import java.util.ArrayList;
import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * <p>GraphQLUnionType class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private List<GraphQLType> types = new ArrayList<>();
    private final TypeResolver typeResolver;


    /**
     * <p>Constructor for GraphQLUnionType.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param description a {@link java.lang.String} object.
     * @param types a {@link java.util.List} object.
     * @param typeResolver a {@link graphql.schema.TypeResolver} object.
     */
    public GraphQLUnionType(String name, String description, List<GraphQLType> types, TypeResolver typeResolver) {
        assertNotNull(name, "name can't be null");
        assertNotNull(types, "types can't be null");
        assertNotNull(typeResolver, "typeResolver can't be null");
        this.name = name;
        this.description = description;
        this.types.addAll(types);
        this.typeResolver = typeResolver;
    }


    /**
     * <p>Getter for the field <code>types</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<GraphQLType> getTypes() {
        return new ArrayList<>(types);
    }

    /**
     * <p>Getter for the field <code>typeResolver</code>.</p>
     *
     * @return a {@link graphql.schema.TypeResolver} object.
     */
    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getDescription() {
        return description;
    }

    /**
     * <p>newUnionType.</p>
     *
     * @return a {@link graphql.schema.GraphQLUnionType.Builder} object.
     */
    public static Builder newUnionType() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLType> types = new ArrayList<>();
        private TypeResolver typeResolver;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }


        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }


        public Builder possibleType(GraphQLType type) {
            assertNotNull(type, "possible type can't be null");
            types.add(type);
            return this;
        }

        public Builder possibleTypes(GraphQLType... type) {
            for (GraphQLType graphQLType : type) {
                possibleType(graphQLType);
            }
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(name, description, types, typeResolver);
        }


    }
}
