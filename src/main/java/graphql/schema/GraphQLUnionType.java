package graphql.schema;


import java.util.ArrayList;
import java.util.List;

public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private List<GraphQLType> types = new ArrayList<>();
    private final TypeResolver typeResolver;


    public GraphQLUnionType(String name, String description, List<GraphQLType> possibleTypes, TypeResolver typeResolver) {
        this.name = name;
        this.description = description;
        this.types.addAll(possibleTypes);
        this.typeResolver = typeResolver;
    }


    public List<GraphQLType> getTypes() {
        return new ArrayList<>(types);
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
            types.add(type);
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(name, description, types, typeResolver);
        }


    }
}
