package graphql.schema;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.UnionTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

@PublicApi
public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private List<GraphQLOutputType> types = new ArrayList<>();
    private final TypeResolver typeResolver;
    private final UnionTypeDefinition definition;


    @Internal
    public GraphQLUnionType(String name, String description, List<GraphQLOutputType> types, TypeResolver typeResolver) {
        this(name, description, types, typeResolver, null);
    }

    @Internal
    public GraphQLUnionType(String name, String description, List<GraphQLOutputType> types, TypeResolver typeResolver, UnionTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(types, "types can't be null");
        assertNotEmpty(types, "A Union type must define one or more member types.");
        assertNotNull(typeResolver, "typeResolver can't be null");
        this.name = name;
        this.description = description;
        this.types = types;
        this.typeResolver = typeResolver;
        this.definition = definition;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        this.types = this.types.stream()
                .map(type -> (GraphQLOutputType) new SchemaUtil().resolveTypeReference(type, typeMap))
                .collect(Collectors.toList());
    }

    /**
     * @return This returns GraphQLObjectType or GraphQLTypeReference instances, if the type
     * references are not resolved yet. After they are resolved it contains only GraphQLObjectType.
     * Reference resolving happens when a full schema is built.
     */
    public List<GraphQLOutputType> getTypes() {
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

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private List<GraphQLOutputType> types = new ArrayList<>();
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

        public Builder possibleType(GraphQLTypeReference reference) {
            assertNotNull(reference, "reference can't be null");
            types.add(reference);
            return this;
        }

        public Builder possibleTypes(GraphQLObjectType... type) {
            for (GraphQLObjectType graphQLType : type) {
                possibleType(graphQLType);
            }
            return this;
        }

        public Builder possibleTypes(GraphQLTypeReference... references) {
            for (GraphQLTypeReference reference : references) {
                possibleType(reference);
            }
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(name, description, types, typeResolver, definition);
        }
    }
}
