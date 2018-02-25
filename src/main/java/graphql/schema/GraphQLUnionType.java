package graphql.schema;


import graphql.Internal;
import graphql.PublicApi;
import graphql.language.UnionTypeDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.Assert.assertNotEmpty;
import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static java.util.Collections.emptyList;

/**
 * A union type is a polymorphic type that dynamically represents one of more concrete object types.
 *
 * At runtime a {@link graphql.schema.TypeResolver} is used to take an union object value and decide what {@link graphql.schema.GraphQLObjectType}
 * represents this union of types.
 *
 * Note that members of a union type need to be concrete object types; you can't create a union type out of interfaces or other unions.
 *
 * See http://graphql.org/learn/schema/#union-types for more details on the concept.
 */
@PublicApi
public class GraphQLUnionType implements GraphQLType, GraphQLOutputType, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private List<GraphQLOutputType> types = new ArrayList<>();
    private final TypeResolver typeResolver;
    private final UnionTypeDefinition definition;
    private final List<GraphQLDirective> directives;


    @Internal
    public GraphQLUnionType(String name, String description, List<GraphQLOutputType> types, TypeResolver typeResolver) {
        this(name, description, types, typeResolver, emptyList(), null);
    }

    @Internal
    public GraphQLUnionType(String name, String description, List<GraphQLOutputType> types, TypeResolver typeResolver, List<GraphQLDirective> directives, UnionTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(types, "types can't be null");
        assertNotEmpty(types, "A Union type must define one or more member types.");
        assertNotNull(typeResolver, "typeResolver can't be null");
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        this.types = types;
        this.typeResolver = typeResolver;
        this.definition = definition;
        this.directives = directives;
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

    @Override
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
    }

    public static Builder newUnionType() {
        return new Builder();
    }

    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private final List<GraphQLOutputType> types = new ArrayList<>();
        private TypeResolver typeResolver;
        private UnionTypeDefinition definition;
        private final List<GraphQLDirective> directives = new ArrayList<>();

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

        public boolean containType(String name) {
            return types.stream().anyMatch(type -> type.getName().equals(name));
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            Collections.addAll(this.directives, directives);
            return this;
        }

        public GraphQLUnionType build() {
            return new GraphQLUnionType(name, description, types, typeResolver, directives, definition);
        }
    }
}
