package graphql.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertTrue;
import static graphql.Assert.assertValidName;
import static graphql.util.FpKit.getByName;
import static java.util.Collections.emptyList;

/**
 * graphql clearly delineates between the types of objects that represent the output of a query and input objects that
 * can be fed into a graphql mutation.  You can define objects as input to graphql via this class
 * <p>
 * See http://graphql.org/learn/schema/#input-types for more details on the concept
 */
@PublicApi
public class GraphQLInputObjectType implements GraphQLNamedInputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLInputFieldsContainer, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final ImmutableMap<String, GraphQLInputObjectField> fieldMap;
    private final InputObjectTypeDefinition definition;
    private final ImmutableList<InputObjectTypeExtensionDefinition> extensionDefinitions;
    private final ImmutableList<GraphQLDirective> directives;

    public static final String CHILD_FIELD_DEFINITIONS = "fieldDefinitions";
    public static final String CHILD_DIRECTIVES = "directives";

    /**
     * @param name        the name
     * @param description the description
     * @param fields      the fields
     *
     * @deprecated use the {@link #newInputObject()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields) {
        this(name, description, fields, emptyList(), null);
    }

    /**
     * @param name        the name
     * @param description the description
     * @param fields      the fields
     * @param directives  the directives on this type element
     * @param definition  the AST definition
     *
     * @deprecated use the {@link #newInputObject()} builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields, List<GraphQLDirective> directives, InputObjectTypeDefinition definition) {
        this(name, description, fields, directives, definition, emptyList());
    }

    public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields, List<GraphQLDirective> directives, InputObjectTypeDefinition definition, List<InputObjectTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(fields, () -> "fields can't be null");
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.description = description;
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directives = ImmutableList.copyOf(directives);
        this.fieldMap = buildDefinitionMap(fields);
    }

    private ImmutableMap<String, GraphQLInputObjectField> buildDefinitionMap(List<GraphQLInputObjectField> fieldDefinitions) {
        return ImmutableMap.copyOf(FpKit.getByName(fieldDefinitions, GraphQLInputObjectField::getName,
                (fld1, fld2) -> assertShouldNeverHappen("Duplicated definition for field '%s' in type '%s'", fld1.getName(), this.name)));
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<GraphQLInputObjectField> getFields() {
        return getFieldDefinitions();
    }

    public GraphQLInputObjectField getField(String name) {
        return fieldMap.get(name);
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return directives;
    }

    @Override
    public GraphQLInputObjectField getFieldDefinition(String name) {
        return fieldMap.get(name);
    }

    @Override
    public List<GraphQLInputObjectField> getFieldDefinitions() {
        return ImmutableList.copyOf(fieldMap.values());
    }

    public InputObjectTypeDefinition getDefinition() {
        return definition;
    }

    public List<InputObjectTypeExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
    }

    /**
     * This helps you transform the current GraphQLInputObjectType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLInputObjectType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newInputObject(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLInputObjectType(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(fieldMap.values());
        children.addAll(directives);
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_FIELD_DEFINITIONS, fieldMap.values())
                .children(CHILD_DIRECTIVES, directives)
                .build();
    }

    @Override
    public GraphQLInputObjectType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceFields(newChildren.getChildren(CHILD_FIELD_DEFINITIONS))
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }


    @Override
    public String toString() {
        return "GraphQLInputObjectType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", fieldMap=" + fieldMap +
                ", definition=" + definition +
                ", directives=" + directives +
                '}';
    }

    public static Builder newInputObject(GraphQLInputObjectType existing) {
        return new Builder(existing);
    }

    public static Builder newInputObject() {
        return new Builder();
    }

    @PublicApi
    public static class Builder extends GraphqlTypeBuilder {
        private InputObjectTypeDefinition definition;
        private List<InputObjectTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLInputObjectField> fields = new LinkedHashMap<>();
        private final Map<String, GraphQLDirective> directives = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLInputObjectType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.fields.putAll(getByName(existing.getFields(), GraphQLInputObjectField::getName));
            this.directives.putAll(getByName(existing.getDirectives(), GraphQLDirective::getName));
        }

        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        @Override
        public Builder comparatorRegistry(GraphqlTypeComparatorRegistry comparatorRegistry) {
            super.comparatorRegistry(comparatorRegistry);
            return this;
        }

        public Builder definition(InputObjectTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<InputObjectTypeExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }

        public Builder field(GraphQLInputObjectField field) {
            assertNotNull(field, () -> "field can't be null");
            fields.put(field.getName(), field);
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
        public Builder field(UnaryOperator<GraphQLInputObjectField.Builder> builderFunction) {
            assertNotNull(builderFunction, () -> "builderFunction should not be null");
            GraphQLInputObjectField.Builder builder = GraphQLInputObjectField.newInputObjectField();
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
        public Builder field(GraphQLInputObjectField.Builder builder) {
            return field(builder.build());
        }

        public Builder fields(List<GraphQLInputObjectField> fields) {
            fields.forEach(this::field);
            return this;
        }

        public Builder replaceFields(List<GraphQLInputObjectField> fields) {
            this.fields.clear();
            ;
            fields.forEach(this::field);
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

        public Builder withDirectives(GraphQLDirective... directives) {
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            directives.put(directive.getName(), directive);
            return this;
        }

        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            assertNotNull(directives, () -> "directive can't be null");
            this.directives.clear();
            for (GraphQLDirective directive : directives) {
                this.directives.put(directive.getName(), directive);
            }
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

        public GraphQLInputObjectType build() {
            return new GraphQLInputObjectType(
                    name,
                    description,
                    sort(fields, GraphQLInputObjectType.class, GraphQLInputObjectField.class),
                    sort(directives, GraphQLInputObjectType.class, GraphQLDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }
}
