package graphql.schema;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.DirectivesUtil;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValue;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertShouldNeverHappen;
import static graphql.Assert.assertValidName;
import static graphql.schema.GraphQLEnumValueDefinition.newEnumValueDefinition;
import static graphql.util.FpKit.getByName;
import static java.util.Collections.emptyList;

/**
 * A graphql enumeration type has a limited set of values.
 * <p>
 * This allows you to validate that any arguments of this type are one of the allowed values
 * and communicate through the type system that a field will always be one of a finite set of values.
 * <p>
 * See http://graphql.org/learn/schema/#enumeration-types for more details
 */
@PublicApi
public class GraphQLEnumType implements GraphQLNamedInputType, GraphQLNamedOutputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final ImmutableMap<String, GraphQLEnumValueDefinition> valueDefinitionMap;
    private final EnumTypeDefinition definition;
    private final ImmutableList<EnumTypeExtensionDefinition> extensionDefinitions;
    private final DirectivesUtil.DirectivesHolder directives;

    public static final String CHILD_VALUES = "values";
    public static final String CHILD_DIRECTIVES = "directives";


    /**
     * @param name        the name
     * @param description the description
     * @param values      the values
     *
     * @deprecated use the {@link #newEnum()}  builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        this(name, description, values, emptyList(), null);
    }

    /**
     * @param name        the name
     * @param description the description
     * @param values      the values
     * @param directives  the directives on this type element
     * @param definition  the AST definition
     *
     * @deprecated use the {@link #newEnum()}  builder pattern instead, as this constructor will be made private in a future version.
     */
    @Internal
    @Deprecated
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values, List<GraphQLDirective> directives, EnumTypeDefinition definition) {
        this(name, description, values, directives, definition, emptyList());
    }

    private GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values, List<GraphQLDirective> directives, EnumTypeDefinition definition, List<EnumTypeExtensionDefinition> extensionDefinitions) {
        assertValidName(name);
        assertNotNull(directives, () -> "directives cannot be null");

        this.name = name;
        this.description = description;
        this.definition = definition;
        this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
        this.directives = new DirectivesUtil.DirectivesHolder(directives);
        this.valueDefinitionMap = buildMap(values);
    }

    @Internal
    public Object serialize(Object input) {
        return getNameByValue(input);
    }

    @Internal
    public Object parseValue(Object input) {
        return getValueByName(input);
    }

    private String typeName(Object input) {
        if (input == null) {
            return "null";
        }
        return input.getClass().getSimpleName();
    }

    @Internal
    public Object parseLiteral(Object input) {
        if (!(input instanceof EnumValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'EnumValue' but was '" + typeName(input) + "'."
            );
        }
        EnumValue enumValue = (EnumValue) input;
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(enumValue.getName());
        if (enumValueDefinition == null) {
            throw new CoercingParseLiteralException(
                    "Expected enum literal value not in allowable values -  '" + input + "'."
            );
        }
        return enumValueDefinition.getValue();
    }

    public List<GraphQLEnumValueDefinition> getValues() {
        return ImmutableList.copyOf(valueDefinitionMap.values());
    }

    public GraphQLEnumValueDefinition getValue(String name) {
        return valueDefinitionMap.get(name);
    }

    private ImmutableMap<String, GraphQLEnumValueDefinition> buildMap(List<GraphQLEnumValueDefinition> values) {
        return ImmutableMap.copyOf(FpKit.getByName(values, GraphQLEnumValueDefinition::getName,
                (fld1, fld2) -> assertShouldNeverHappen("Duplicated definition for field '%s' in type '%s'", fld1.getName(), this.name)));
    }

    private Object getValueByName(Object value) {
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(value.toString());
        if (enumValueDefinition != null) {
            return enumValueDefinition.getValue();
        }
        throw new CoercingParseValueException("Invalid input for Enum '" + name + "'. No value found for name '" + value.toString() + "'");
    }

    private Object getNameByValue(Object value) {
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
        // ok we didn't match on pure object.equals().  Lets try the Java enum strategy
        if (value instanceof Enum) {
            String enumNameValue = ((Enum<?>) value).name();
            for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
                Object definitionValue = String.valueOf(valueDefinition.getValue());
                if (enumNameValue.equals(definitionValue)) {
                    return valueDefinition.getName();
                }
            }
        }
        throw new CoercingSerializeException("Invalid input for Enum '" + name + "'. Unknown value '" + value + "'");
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
        children.addAll(directives.getDirectives());
        return children;
    }

    @Override
    public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
        return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
                .children(CHILD_VALUES, valueDefinitionMap.values())
                .children(CHILD_DIRECTIVES, directives.getDirectives())
                .build();
    }

    @Override
    public GraphQLEnumType withNewChildren(SchemaElementChildrenContainer newChildren) {
        return transform(builder ->
                builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                        .replaceValues(newChildren.getChildren(CHILD_VALUES))
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
                ", directives=" + directives +
                '}';
    }

    public static Builder newEnum() {
        return new Builder();
    }

    public static Builder newEnum(GraphQLEnumType existing) {
        return new Builder(existing);
    }

    public static class Builder extends GraphqlTypeBuilder {

        private EnumTypeDefinition definition;
        private List<EnumTypeExtensionDefinition> extensionDefinitions = emptyList();
        private final Map<String, GraphQLEnumValueDefinition> values = new LinkedHashMap<>();
        private final List<GraphQLDirective> directives = new ArrayList<>();

        public Builder() {
        }

        public Builder(GraphQLEnumType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.extensionDefinitions = existing.getExtensionDefinitions();
            this.values.putAll(getByName(existing.getValues(), GraphQLEnumValueDefinition::getName));
            DirectivesUtil.enforceAddAll(this.directives,existing.getDirectives());
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
            assertNotNull(value, () -> "value can't be null");
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
            assertNotNull(enumValueDefinition, () -> "enumValueDefinition can't be null");
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

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, () -> "directives can't be null");
            this.directives.clear();
            for (GraphQLDirective directive : directives) {
                withDirective(directive);
            }
            return this;
        }

        public Builder withDirective(GraphQLDirective directive) {
            assertNotNull(directive, () -> "directive can't be null");
            DirectivesUtil.enforceAdd(this.directives, directive);
            return this;
        }

        public Builder replaceDirectives(List<GraphQLDirective> directives) {
            assertNotNull(directives, () -> "directive can't be null");
            this.directives.clear();
            DirectivesUtil.enforceAddAll(this.directives, directives);
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

        public GraphQLEnumType build() {
            return new GraphQLEnumType(
                    name,
                    description,
                    sort(values, GraphQLEnumType.class, GraphQLEnumValueDefinition.class),
                    sort(directives, GraphQLEnumType.class, GraphQLDirective.class),
                    definition,
                    extensionDefinitions);
        }
    }
}
