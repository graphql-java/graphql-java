package graphql.schema;


import graphql.DirectivesUtil;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.language.InputValueDefinition;
import graphql.language.Value;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.execution.ValuesResolver.getInputValueImpl;

/**
 * Input objects defined via {@link GraphQLInputObjectType} contains these input fields.
 *
 * There are similar to {@link GraphQLFieldDefinition} however they can ONLY be used on input objects, that
 * is to describe values that are fed into a graphql mutation.
 *
 * See <a href="https://graphql.org/learn/schema/#input-types">https://graphql.org/learn/schema/#input-types</a> for more details on the concept.
 */
@PublicApi
public class GraphQLInputObjectField implements GraphQLNamedSchemaElement, GraphQLInputValueDefinition {

    private final String name;
    private final String description;
    private final GraphQLInputType originalType;
    private final InputValueWithState defaultValue;

    private final String deprecationReason;
    private final InputValueDefinition definition;
    private final DirectivesUtil.DirectivesHolder directivesHolder;

    private GraphQLInputType replacedType;

    public static final String CHILD_TYPE = "type";


    private GraphQLInputObjectField(
            String name,
            String description,
            GraphQLInputType type,
            InputValueWithState defaultValue,
            List<GraphQLDirective> directives,
            List<GraphQLAppliedDirective> appliedDirectives,
            InputValueDefinition definition,
            String deprecationReason) {
        assertValidName(name);
        assertNotNull(type, () -> "type can't be null");
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.originalType = type;
        this.defaultValue = defaultValue;
        this.description = description;
        this.directivesHolder = new DirectivesUtil.DirectivesHolder(directives, appliedDirectives);
        this.definition = definition;
        this.deprecationReason = deprecationReason;
    }

    void replaceType(GraphQLInputType type) {
        this.replacedType = type;
    }

    @Override
    public String getName() {
        return name;
    }

    public GraphQLInputType getType() {
        return replacedType != null ? replacedType : originalType;
    }

    /**
     * The default value of this input field.
     *
     * The semantics of the returned Object depend on how the {@link InputValueWithState} was created.
     *
     * @return a input value with captured state
     */
    public @NotNull InputValueWithState getInputFieldDefaultValue() {
        return defaultValue;
    }

    /**
     * This static helper method will give out a java value based on the semantics captured
     * in the {@link InputValueWithState} from {@link GraphQLInputObjectField#getInputFieldDefaultValue()}
     *
     * Note : You MUST only call this on a {@link GraphQLInputObjectField} that is part of a fully formed schema.  We need
     * all of the types to be resolved in order for this work correctly.
     *
     * Note: This method will return null if the value is not set or explicitly set to null.  If you you to know the difference
     * when "not set" and "set to null" then you cant use this method.  Rather you should use {@link GraphQLInputObjectField#getInputFieldDefaultValue()}
     * and use the {@link InputValueWithState#isNotSet()} methods to decide how to handle those values.
     *
     * @param inputObjectField the fully formed {@link GraphQLInputObjectField}
     * @param <T>              the type you want it cast as
     *
     * @return a value of type T which is the java value of the input field default
     */
    public static <T> T getInputFieldDefaultValue(GraphQLInputObjectField inputObjectField) {
        return getInputValueImpl(inputObjectField.getType(), inputObjectField.getInputFieldDefaultValue(), GraphQLContext.getDefault(), Locale.getDefault());
    }


    public boolean hasSetDefaultValue() {
        return defaultValue.isSet();
    }

    public String getDescription() {
        return description;
    }

    public String getDeprecationReason() {
        return deprecationReason;
    }

    public boolean isDeprecated() {
        return deprecationReason != null;
    }

    public InputValueDefinition getDefinition() {
        return definition;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return directivesHolder.getDirectives();
    }

    @Override
    public Map<String, GraphQLDirective> getDirectivesByName() {
        return directivesHolder.getDirectivesByName();
    }

    @Override
    public Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
        return directivesHolder.getAllDirectivesByName();
    }

    @Override
    public GraphQLDirective getDirective(String directiveName) {
        return directivesHolder.getDirective(directiveName);
    }

    @Override
    public List<GraphQLAppliedDirective> getAppliedDirectives() {
        return directivesHolder.getAppliedDirectives();
    }

    @Override
    public Map<String, List<GraphQLAppliedDirective>> getAllAppliedDirectivesByName() {
        return directivesHolder.getAllAppliedDirectivesByName();
    }

    @Override
    public GraphQLAppliedDirective getAppliedDirective(String directiveName) {
        return directivesHolder.getAppliedDirective(directiveName);
    }

    /**
     * This helps you transform the current GraphQLInputObjectField into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new object based on calling build on that builder
     */
    public GraphQLInputObjectField transform(Consumer<Builder> builderConsumer) {
        Builder builder = newInputObjectField(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newInputObjectField(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLInputObjectField(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>();
        children.add(getType());
        children.addAll(directivesHolder.getDirectives());
        children.addAll(directivesHolder.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .child(CHILD_TYPE, originalType)
                .build();
    }

    @Override
    public GraphQLInputObjectField withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.type((GraphQLInputType) newChildren.getChildOrNull(CHILD_TYPE))
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
        return "GraphQLInputObjectField{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", originalType=" + inputTypeToStringAvoidingCircularReference(originalType) +
                ", defaultValue=" + defaultValue +
                ", definition=" + definition +
                ", directives=" + directivesHolder +
                ", replacedType=" + inputTypeToStringAvoidingCircularReference(replacedType) +
                '}';
    }

    private static Object inputTypeToStringAvoidingCircularReference(GraphQLInputType graphQLInputType) {
        return (graphQLInputType instanceof GraphQLInputObjectType)
                ? String.format("[%s]", GraphQLInputObjectType.class.getSimpleName())
                : graphQLInputType;
    }

    public static Builder newInputObjectField(GraphQLInputObjectField existing) {
        return new Builder(existing);
    }


    public static Builder newInputObjectField() {
        return new Builder();
    }

    @PublicApi
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder,Builder> {
        private InputValueWithState defaultValue = InputValueWithState.NOT_SET;
        private GraphQLInputType type;
        private InputValueDefinition definition;
        private String deprecationReason;


        public Builder() {
        }

        public Builder(GraphQLInputObjectField existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.defaultValue = existing.getInputFieldDefaultValue();
            this.type = existing.originalType;
            this.definition = existing.getDefinition();
            this.deprecationReason = existing.deprecationReason;
            copyExistingDirectives(existing);
        }

        public Builder definition(InputValueDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder deprecate(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public Builder type(GraphQLInputObjectType.Builder type) {
            return type(type.build());
        }

        public Builder type(GraphQLInputType type) {
            this.type = type;
            return this;
        }

        /**
         * A legacy method for setting a default value
         *
         * @param defaultValue the value to set
         *
         * @return this builder
         *
         * @deprecated use {@link #defaultValueLiteral(Value)}
         */
        @Deprecated(since = "2021-05-10")
        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = InputValueWithState.newInternalValue(defaultValue);
            return this;
        }

        public Builder defaultValueLiteral(Value defaultValue) {
            this.defaultValue = InputValueWithState.newLiteralValue(defaultValue);
            return this;
        }

        public Builder defaultValueProgrammatic(Object defaultValue) {
            this.defaultValue = InputValueWithState.newExternalValue(defaultValue);
            return this;
        }

        public Builder clearDefaultValue() {
            this.defaultValue = InputValueWithState.NOT_SET;
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

        public GraphQLInputObjectField build() {
            assertNotNull(type, () -> "type can't be null");
            return new GraphQLInputObjectField(
                    name,
                    description,
                    type,
                    defaultValue,
                    sort(directives, GraphQLInputObjectField.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLScalarType.class, GraphQLAppliedDirective.class),
                    definition,
                    deprecationReason);
        }
    }
}
