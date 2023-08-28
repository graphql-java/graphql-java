package graphql.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.Directives;
import graphql.DirectivesUtil;
import graphql.ExperimentalApi;
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
import static graphql.Assert.assertValidName;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.util.FpKit.getByName;

/**
 * graphql clearly delineates between the types of objects that represent the output of a query and input objects that
 * can be fed into a graphql mutation.  You can define objects as input to graphql via this class
 * <p>
 * See <a href="https://graphql.org/learn/schema/#input-types">https://graphql.org/learn/schema/#input-types</a> for more details on the concept
 */
@PublicApi
public class GraphQLInputObjectType implements GraphQLNamedInputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLInputFieldsContainer, GraphQLDirectiveContainer {

    private final String name;
    private final boolean isOneOf;
    private final String description;
    private final ImmutableMap<String, GraphQLInputObjectField> fieldMap;
    private final InputObjectTypeDefinition definition;
    private final ImmutableList<InputObjectTypeExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directives;

    public static final String CHILD_FIELD_DEFINITIONS = "fieldDefinitions";

    @Internal
    private GraphQLInputObjectType(String name,
                                   String description,
                                   List<GraphQLInputObjectField> fields,
                                   List<GraphQLDirective> directives,
                                   List<GraphQLAppliedDirective> appliedDirectives,
                                   InputObjectTypeDefinition definition,
                                   List<InputObjectTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(fields, () -> "fields can't be null");
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.description = description;
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directives = new DirectivesUtil.DirectivesHolder(directives, appliedDirectives);
        this.fieldMap = buildDefinitionMap(fields);
        this.isOneOf = hasOneOf(directives, appliedDirectives);
    }

    private ImmutableMap<String, GraphQLInputObjectField> buildDefinitionMap(List<GraphQLInputObjectField> fieldDefinitions) {
        return ImmutableMap.copyOf(FpKit.getByName(fieldDefinitions, GraphQLInputObjectField::getName,
                (fld1, fld2) -> assertShouldNeverHappen("Duplicated definition for field '%s' in type '%s'", fld1.getName(), this.name)));
    }

    private boolean hasOneOf(List<GraphQLDirective> directives, List<GraphQLAppliedDirective> appliedDirectives) {
        if (appliedDirectives.stream().anyMatch(d -> Directives.OneOfDirective.getName().equals(d.getName()))) {
            return true;
        }
        // eventually GraphQLDirective as applied directive goes away
        return directives.stream().anyMatch(d -> Directives.OneOfDirective.getName().equals(d.getName()));
    }

    @Override
    public String getName() {
        return name;
    }


    /**
     * An Input Object is considered a OneOf Input Object if it has the `@oneOf` directive applied to it.
     * <p>
     * This API is currently considered experimental since the graphql specification has not yet ratified
     * this approach.
     *
     * @return true if it's a OneOf Input Object
     */
    @ExperimentalApi
    public boolean isOneOf() {
        return isOneOf;
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
        return directives.getDirectives();
    }

    @Override
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directives.getDirectivesByName();
    }

    @Override
    public Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
        return directives.getAllDirectivesByName();
    }

    @Override
    public GraphQLDirective getDirective(String directiveName) {
        return directives.getDirective(directiveName);
    }

    @Override
    public List<GraphQLAppliedDirective> getAppliedDirectives() {
        return directives.getAppliedDirectives();
    }

    @Override
    public Map<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName() {
        return directives.getAllAppliedDirectivesByName();
    }

    @Override
    public GraphQLAppliedDirective getAppliedDirective(String directiveName) {
        return directives.getAppliedDirective(directiveName);
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
    public GraphQLSchemaElement copy() {
        return newInputObject(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLInputObjectType(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(fieldMap.values());
        children.addAll(directives.getDirectives());
        children.addAll(directives.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_FIELD_DEFINITIONS, fieldMap.values())
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directives.getAppliedDirectives())
                .build();
    }

    @Override
    public GraphQLInputObjectType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceFields(newChildren.getChildren(CHILD_FIELD_DEFINITIONS))
                        .replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceAppliedDirectives(newChildren.getChildren(CHILD_APPLIED_DIRECTIVES))
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
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder, Builder> {
        private InputObjectTypeDefinition definition;
        private List<InputObjectTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLInputObjectField> fields = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLInputObjectType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.fields.putAll(getByName(existing.getFields(), GraphQLInputObjectField::getName));
            copyExistingDirectives(existing);
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

        // -- the following are repeated to avoid a binary incompatibility problem --

        @Override
        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            return super.replaceDirectives(directives);
        }

        @Override
        public Builder withDirectives(GraphQLDirective... directives) {
            return super.withDirectives(directives);
        }

        @Override
        public Builder withDirective(GraphQLDirective directive) {
            return super.withDirective(directive);
        }

        @Override
        public Builder withDirective(GraphQLDirective.Builder builder) {
            return super.withDirective(builder);
        }

        @Override
        public Builder clearDirectives() {
            return super.clearDirectives();
        }

        @Override
        public Builder name(String name) {
            return super.name(name);
        }

        @Override
        public Builder description(String description) {
            return super.description(description);
        }

        public GraphQLInputObjectType build() {
            return new GraphQLInputObjectType(
                    name,
                    description,
                    sort(fields, GraphQLInputObjectType.class, GraphQLInputObjectField.class),
                    sort(directives, GraphQLInputObjectType.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLInputObjectType.class, GraphQLAppliedDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }
}
