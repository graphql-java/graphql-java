package graphql.schema;


import java.util.ArrayList;
import java.util.List;

public class GraphQLUnionType implements GraphQLType, GraphQLOutputType {

    private final List<GraphQLType> types = new ArrayList<>();
    private final TypeResolver typeResolver;

    public GraphQLUnionType(List<GraphQLType> possibleTypes, TypeResolver typeResolver) {
        this.types.addAll(possibleTypes);
        this.typeResolver = typeResolver;
    }

    public List<GraphQLType> getTypes() {
        return types;
    }

    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    public static Builder newUnionType() {
        return new Builder();
    }

    public static class Builder {

        private List<GraphQLType> types = new ArrayList<>();
        private TypeResolver typeResolver;


        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }


        public Builder possibleType(GraphQLType type) {
            types.add(type);
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(types, typeResolver);
        }


    }
}
