package graphql.schema;


import graphql.language.UnionTypeDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.*;

public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType {

    private final String name;
    private final String description;
    private List<GraphQLObjectType> types;
    private final List<TypeOrReference<GraphQLObjectType>> tmpTypes = new ArrayList<>();
    private final TypeResolver typeResolver;
    private final UnionTypeDefinition definition;


    public GraphQLUnionType(String name, String description, List<TypeOrReference<GraphQLObjectType>> tmpTypes, TypeResolver typeResolver) {
        this(name, description, tmpTypes, typeResolver, null);
    }

    public GraphQLUnionType(String name, String description, List<TypeOrReference<GraphQLObjectType>> tmpTypes, TypeResolver typeResolver, UnionTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(tmpTypes, "types can't be null");
        assertNotEmpty(tmpTypes, "A Union type must define one or more member types.");
        assertNotNull(typeResolver, "typeResolver can't be null");
        this.name = name;
        this.description = description;
        this.tmpTypes.addAll(tmpTypes);
        this.typeResolver = typeResolver;
        this.definition = definition;
    }

    void replaceTypeReferences(Map<String, GraphQLType> typeMap) {
        this.types = this.tmpTypes.stream()
                .map(TypeOrReference::getTypeOrReference)
                .map(type -> (GraphQLObjectType) new SchemaUtil().resolveTypeReference(type, typeMap))
                .collect(Collectors.toList());
    }


    public List<GraphQLObjectType> getTypes() {
        if (this.types == null) {
            return this.tmpTypes.stream()
                    .filter(TypeOrReference::isType)
                    .map(TypeOrReference::getType)
                    .collect(Collectors.toList());
        }
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
        private List<TypeOrReference<GraphQLObjectType>> types = new ArrayList<>();
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
            types.add(new TypeOrReference<>(type));
            return this;
        }

        public Builder possibleType(GraphQLTypeReference reference) {
            assertNotNull(reference, "reference can't be null");
            types.add(new TypeOrReference<>(reference));
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
