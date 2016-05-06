package graphql.schema;


import java.util.ArrayList;
import java.util.List;

import static graphql.Assert.*;

public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private List<GraphQLObjectType> types = new ArrayList<GraphQLObjectType>();
    private final TypeResolver typeResolver;


    public GraphQLUnionType(String name, String description, List<GraphQLObjectType> types, TypeResolver typeResolver) {
        assertNotNull(name, "name can't be null");
        assertNotNull(types, "types can't be null");
        assertNotEmpty(types, "A Union type must define one or more member types.");
        assertNotNull(typeResolver, "typeResolver can't be null");
        this.name = name;
        this.description = description;
        this.types.addAll(types);
        this.typeResolver = typeResolver;
    }


    public List<GraphQLObjectType> getTypes() {
        return new ArrayList<GraphQLObjectType>(types);
    }

    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static Builder newUnionType() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLObjectType> types = new ArrayList<GraphQLObjectType>();
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


        public Builder possibleType(GraphQLObjectType type) {
            assertNotNull(type, "possible type can't be null");
            types.add(type);
            return this;
        }

        public Builder possibleTypes(GraphQLObjectType... type) {
            for (GraphQLObjectType graphQLType : type) {
                possibleType(graphQLType);
            }
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(name, description, types, typeResolver);
        }


    }
}
