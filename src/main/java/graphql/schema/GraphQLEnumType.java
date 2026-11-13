package graphql.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.DirectivesUtil;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValue;
import graphql.language.Value;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertValidName;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.scalar.CoercingUtil.i18nMsg;
import static graphql.scalar.CoercingUtil.typeName;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.util.FpKit.getByName;

/**
 * A graphql enumeration type has a limited set of values.
 * <p>
 * This allows you to validate that any arguments of this type are one of the allowed values
 * and communicate through the type system that a field will always be one of a finite set of values.
 * <p>
 * See <a href="https://graphql.org/learn/schema/#enumeration-types">https://graphql.org/learn/schema/#enumeration-types</a> for more details
 */
@PublicApi
@NullMarked
public class GraphQLEnumType implements GraphQLNamedInputType, GraphQLNamedOutputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final ImmutableMap<String, GraphQLEnumValueDefinition> valueDefinitionMap;
    private final EnumTypeDefinition definition;
    private final ImmutableList<EnumTypeExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directivesHolder;

    public static final String CHILD_VALUES = "values";

    @Internal
    private GraphQLEnumType(String name,
                            String description,
                            List<GraphQLEnumValueDefinition> values,
                            List<GraphQLDirective> directives,
                            List<GraphQLAppliedDirective> appliedDirectives,
                            EnumTypeDefinition definition,
                            List<EnumTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directivesHolder = DirectivesUtil.DirectivesHolder.create(directives, appliedDirectives);
        this.valueDefinitionMap = buildMap(values);
    }

    @Internal
    @Deprecated
    public Object serialize(Object input) {
        return serialize(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Internal
    public Object serialize(Object input, GraphQLContext graphQLContext, Locale locale) {
        return getNameByValue(input, graphQLContext, locale);
    }

    @Internal
    @Deprecated
    public Object parseValue(Object input) {
        return getValueByName(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Internal
    public Object parseValue(Object input, GraphQLContext graphQLContext, Locale locale) {
        return getValueByName(input, graphQLContext, locale);
    }


    @Internal
    @Deprecated
    public Object parseLiteral(Object input) {
        return parseLiteralImpl(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Internal
    public Object parseLiteral(Value<?> input, GraphQLContext graphQLContext, Locale locale) {
        return parseLiteralImpl(input, graphQLContext, locale);
    }

    private Object parseLiteralImpl(Object input, GraphQLContext graphQLContext, Locale locale) {
        if (!(input instanceof EnumValue)) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Scalar.unexpectedAstType", "EnumValue", typeName(input))
            );
        }
        EnumValue enumValue = (EnumValue) input;
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(enumValue.getName());
        if (enumValueDefinition == null) {
            throw new CoercingParseLiteralException(
                    i18nMsg(locale, "Enum.unallowableValue", getName(), input)
            );
        }
        return enumValueDefinition.getValue();
    }


    @Internal
    @Deprecated
    public Value valueToLiteral(Object input) {
        return valueToLiteral(input, GraphQLContext.getDefault(), Locale.getDefault());
    }

    @Internal
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(input.toString());
        if (enumValueDefinition == null) {
            assertShouldNeverHappen(i18nMsg(locale, "Enum.badName", name, input.toString()));
        }
        return EnumValue.newEnumValue(enumValueDefinition.getName()).build();

    }

    public List<GraphQLEnumValueDefinition> getValues() {
        return ImmutableList.copyOf(valueDefinitionMap.values());
    }

    public @Nullable GraphQLEnumValueDefinition getValue(String name) {
        return valueDefinitionMap.get(name);
    }

    private ImmutableMap<String, GraphQLEnumValueDefinition> buildMap(List<GraphQLEnumValueDefinition> values) {
        return ImmutableMap.copyOf(FpKit.getByName(values, GraphQLEnumValueDefinition::getName,
                (fld1, fld2) -> assertShouldNeverHappen("Duplicated definition for field '%s' in type '%s'", fld1.getName(), this.name)));
    }

    private Object getValueByName(Object value, GraphQLContext graphQLContext, Locale locale) {
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(value.toString());
        if (enumValueDefinition != null) {
            return enumValueDefinition.getValue();
        }
        throw new CoercingParseValueException(i18nMsg(locale, "Enum.badName", name, value.toString()));
    }

    private Object getNameByValue(Object value, GraphQLContext graphQLContext, Locale locale) {
        for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
            Object definitionValue = valueDefinition.getValue();
            if (value.equals(definitionValue)) {
                return valueDefinition.getName();
            }
            // we can treat enum backing values as strings in effect
            if (definitionValue instanceof Enum && value instanceof String) {
                if (value.equals(((Enum) definitionValue).name())) {
                    return valueDefinition.getName();
                }
            }
        }
        // ok we didn't match on pure object.equals().  Let's try the Java enum strategy
        if (value instanceof Enum) {
            String enumNameValue = ((Enum<?>) value).name();
            for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
                Object definitionValue = String.valueOf(valueDefinition.getValue());
                if (enumNameValue.equals(definitionValue)) {
                    return valueDefinition.getName();
                }
            }
        }
        throw new CoercingSerializeException(i18nMsg(locale, "Enum.badInput", name, value));
    }

    @Override
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public EnumTypeDefinition getDefinition() {
        return definition;
    }

    public List<EnumTypeExtensionDefinition> getExtensionDefinitions() {
        return extensionDefinitions;
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
     * This helps you transform the current GraphQLEnumType into another one by starting a builder with all
     * the current values and allows you to transform it how you want.
     *
     * @param builderConsumer the consumer code that will be given a builder to transform
     *
     * @return a new field based on calling build on that builder
     */
    public GraphQLEnumType transform(Consumer<Builder> builderConsumer) {
        Builder builder = newEnum(this);
        builderConsumer.accept(builder);
        return builder.build();
    }

    @Override
    public GraphQLSchemaElement copy() {
        return newEnum(this).build();
    }


    @Override
    public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
        return visitor.visitGraphQLEnumType(this, context);
    }

    @Override
    public List<GraphQLSchemaElement> getChildren() {
        List<GraphQLSchemaElement> children = new ArrayList<>(valueDefinitionMap.values());
        children.addAll(directivesHolder.getDirectives());
        children.addAll(directivesHolder.getAppliedDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_VALUES, valueDefinitionMap.values())
                .children(CHILD_DIRECTIVES, directivesHolder.getDirectives())
                .children(CHILD_APPLIED_DIRECTIVES, directivesHolder.getAppliedDirectives())
                .build();
    }

    @Override
    public GraphQLEnumType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceValues(newChildren.getChildren(CHILD_VALUES))
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
        return "GraphQLEnumType{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", valueDefinitionMap=" + valueDefinitionMap +
                ", definition=" + definition +
                ", extensionDefinitions=" + extensionDefinitions +
                ", directives=" + directivesHolder +
                '}';
    }

    public static Builder newEnum() {
        return new Builder();
    }

    public static Builder newEnum(GraphQLEnumType existing) {
        return new Builder(existing);
    }

    @NullUnmarked
    public static class Builder extends GraphqlDirectivesContainerTypeBuilder<Builder, Builder> {

        private EnumTypeDefinition definition;
        private List<EnumTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLEnumValueDefinition> values = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLEnumType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.values.putAll(getByName(existing.getValues(), GraphQLEnumValueDefinition::getName));
            copyExistingDirectives(existing);
        }

        public Builder definition(EnumTypeDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder extensionDefinitions(List<EnumTypeExtensionDefinition> extensionDefinitions) {
            this.extensionDefinitions = extensionDefinitions;
            return this;
        }


        public Builder value(String name, Object value, String description, String deprecationReason) {
            return value(newEnumValueDefinition().name(name)
                    .description(description).value(value)
                    .deprecationReason(deprecationReason).build());
        }

        public Builder value(String name, Object value, String description) {
            return value(newEnumValueDefinition().name(name)
                    .description(description).value(value).build());
        }

        public Builder value(String name, Object value) {
            assertNotNull(value, "value can't be null");
            return value(newEnumValueDefinition().name(name)
                    .value(value).build());
        }


        public Builder value(String name) {
            return value(newEnumValueDefinition().name(name)
                    .value(name).build());
        }

        public Builder values(List<GraphQLEnumValueDefinition> valueDefinitions) {
            valueDefinitions.forEach(this::value);
            return this;
        }

        public Builder replaceValues(List<GraphQLEnumValueDefinition> valueDefinitions) {
            this.values.clear();
            valueDefinitions.forEach(this::value);
            return this;
        }

        public Builder value(GraphQLEnumValueDefinition enumValueDefinition) {
            assertNotNull(enumValueDefinition, "enumValueDefinition can't be null");
            values.put(enumValueDefinition.getName(), enumValueDefinition);
            return this;
        }

        public boolean hasValue(String name) {
            return values.containsKey(name);
        }

        /**
         * This is used to clear all the values in the builder so far.
         *
         * @return the builder
         */
        public Builder clearValues() {
            values.clear();
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

        public GraphQLEnumType build() {
            return new GraphQLEnumType(
                    name,
                    description,
                    sort(values, GraphQLEnumType.class, GraphQLEnumValueDefinition.class),
                    sort(directives, GraphQLEnumType.class, GraphQLDirective.class),
                    sort(appliedDirectives, GraphQLScalarType.class, GraphQLAppliedDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }
}
