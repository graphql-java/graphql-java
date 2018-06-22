package graphql.schema;

import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InterfaceTypeDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.valuesToList;
import static java.lang.String.format;

/**
 * In graphql, an interface is an abstract type that defines the set of fields that a type must include to
 * implement that interface.
 *
 * At runtime a {@link graphql.schema.TypeResolver} is used to take an interface object value and decide what {@link graphql.schema.GraphQLObjectType}
 * represents this interface type.
 *
 *
 * See http://graphql.org/learn/schema/#interfaces for more details on the concept.
 */
@PublicApi
public class GraphQLInterfaceType implements GraphQLType, GraphQLOutputType, GraphQLFieldsContainer, GraphQLCompositeType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final Map<String, GraphQLFieldDefinition> fieldDefinitionsByName = new LinkedHashMap<>();
    private final TypeResolver typeResolver;
    private final InterfaceTypeDefinition definition;
    private final List<GraphQLDirective> directives;

    @Internal
    public GraphQLInterfaceType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions, TypeResolver typeResolver) {
        this(name, description, fieldDefinitions, typeResolver, Collections.emptyList(), null);
    }

    @Internal
    public GraphQLInterfaceType(String name, String description, List<GraphQLFieldDefinition> fieldDefinitions, TypeResolver typeResolver, List<GraphQLDirective> directives, InterfaceTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(typeResolver, "typeResolver can't null");
        assertNotNull(fieldDefinitions, "fieldDefinitions can't null");
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        buildDefinitionMap(fieldDefinitions);
        this.typeResolver = typeResolver;
        this.definition = definition;
        this.directives = directives;
    }

    private void buildDefinitionMap(List<GraphQLFieldDefinition> fieldDefinitions) {
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            String name = fieldDefinition.getName();
            if (fieldDefinitionsByName.containsKey(name))
                throw new AssertException(format("Duplicated definition for field '%s' in interface '%s'", name, this.name));
            fieldDefinitionsByName.put(name, fieldDefinition);
        }
    }

    @Override
    public GraphQLFieldDefinition getFieldDefinition(String name) {
        return fieldDefinitionsByName.get(name);
    }


    @Override
    public List<GraphQLFieldDefinition> getFieldDefinitions() {
        return new ArrayList<>(fieldDefinitionsByName.values());
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    public InterfaceTypeDefinition getDefinition() {
        return definition;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
    }

    @Override
    public String toString() {
        return "GraphQLInterfaceType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldDefinitionsByName=" + fieldDefinitionsByName.keySet() +
                ", typeResolver=" + typeResolver +
                '}';
    }

    /**
     * This helps you transform the current GraphQLInterfaceType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLInterfaceType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newInterface(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    public static Builder newInterface() {
        return new Builder();
    }

    public static Builder newInterface(GraphQLInterfaceType existing) {
        return new Builder(existing);
    }


    @PublicApi
    public static class Builder {
        private String name;
        private String description;
        private TypeResolver typeResolver;
        private InterfaceTypeDefinition definition;
        private final Map<String, GraphQLFieldDefinition> fields = new LinkedHashMap<>();
        private final Map<String, GraphQLDirective> directives = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLInterfaceType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.typeResolver = existing.getTypeResolver();
            this.definition = existing.getDefinition();
            this.fields.putAll(getByName(existing.getFieldDefinitions(), GraphQLFieldDefinition::getName));
            this.directives.putAll(getByName(existing.getDirectives(), GraphQLDirective::getName));
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder definition(InterfaceTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder field(GraphQLFieldDefinition fieldDefinition) {
            assertNotNull(fieldDefinition, "fieldDefinition can't be null");
            this.fields.put(fieldDefinition.getName(), fieldDefinition);
            return this;
        }

        /**
         * Take a field builder in a function definition and apply. Can be used in a jdk8 lambda
         * e.g.:
         * <pre>
         *     {@code
         *      field(f -> f.name("fieldName"))
         *     }
         * </pre>
         *
         * @param builderFunction a supplier for the builder impl
         *
         * @return this
         */
        public Builder field(UnaryOperator<GraphQLFieldDefinition.Builder> builderFunction) {
            assertNotNull(builderFunction, "builderFunction can't be null");
            GraphQLFieldDefinition.Builder builder = GraphQLFieldDefinition.newFieldDefinition();
            builder = builderFunction.apply(builder);
            return field(builder);
        }

        /**
         * Same effect as the field(GraphQLFieldDefinition). Builder.build() is called
         * from within
         *
         * @param builder an un-built/incomplete GraphQLFieldDefinition
         *
         * @return this
         */
        public Builder field(GraphQLFieldDefinition.Builder builder) {
            return field(builder.build());
        }

        public Builder fields(List<GraphQLFieldDefinition> fieldDefinitions) {
            assertNotNull(fieldDefinitions, "fieldDefinitions can't be null");
            fieldDefinitions.forEach(this::field);
            return this;
        }

        public boolean hasField(String fieldName) {
            return fields.containsKey(fieldName);
        }

        /**
         * This is used to clear all the fields in the builder so far.
         *
         * @return the builder
         */
        public Builder clearFields() {
            fields.clear();
            return this;
        }


        public Builder typeResolver(TypeResolver typeResolver) {
            this.typeResolver = typeResolver;
            return this;
        }

        public Builder withDirectives(GraphQLDirective... directives) {
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, "directive can't be null");
            directives.put(directive.getName(), directive);
            return this;
        }

        public Builder withDirective(GraphQLDirective.Builder builder) {
            return withDirective(builder.build());
        }

        /**
         * This is used to clear all the directives in the builder so far.
         *
         * @return the builder
         */
        public Builder clearDirectives() {
            directives.clear();
            return this;
        }

        public GraphQLInterfaceType build() {
            return new GraphQLInterfaceType(name, description, valuesToList(fields), typeResolver, valuesToList(directives), definition);
        }
    }
}
