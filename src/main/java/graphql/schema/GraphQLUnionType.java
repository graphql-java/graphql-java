package graphql.schema;


import graphql.language.UnionTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private final List<GraphQLObjectType> types = new ArrayList<>();
    private final TypeResolver typeResolver;
    private final UnionTypeDefinition definition;


    public GraphQLUnionType(String name, String description, List<GraphQLObjectType> types, TypeResolver typeResolver) {
        this(name, description, types, typeResolver, null);
    }

    public GraphQLUnionType(String name, String description, List<GraphQLObjectType> types, TypeResolver typeResolver, UnionTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(types, "types can't be null");
        assertNotEmpty(types, "A Union type must define one or more member types.");
        assertNotNull(typeResolver, "typeResolver can't be null");
        this.name = name;
        this.description = description;
        this.types.addAll(types);
        this.typeResolver = typeResolver;
        this.definition = definition;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        for (int i = 0; i < types.size(); i++) {
            GraphQLObjectType type = types.get(i);
            if (type instanceof TypeReference) {
                this.types.set(i, (GraphQLObjectType) new SchemaUtil().resolveTypeReference(type, typeMap));
            }
        }
    }

    public List<GraphQLObjectType> getTypes() {
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

    public UnionTypeDefinition getDefinition() {
        return definition;
    }

    public static Builder newUnionType() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLObjectType> types = new ArrayList<>();
        private TypeResolver typeResolver;
        private UnionTypeDefinition definition;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder definition(UnionTypeDefinition definition) {
            this.definition = definition;
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
            return new GraphQLUnionType(name, description, types, typeResolver, definition);
        }
    }
}
