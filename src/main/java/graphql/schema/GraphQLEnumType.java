package graphql.schema;


import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.valuesToList;
import static java.util.Collections.emptyList;

/**
 * A graphql enumeration type has a limited set of values.
 *
 * This allows you to validate that any arguments of this type are one of the allowed values
 * and communicate through the type system that a field will always be one of a finite set of values.
 *
 * See http://graphql.org/learn/schema/#enumeration-types for more details
 */
@PublicApi
public class GraphQLEnumType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLDirectiveContainer {

    private final String name;
    private final String description;
    private final Map<String, GraphQLEnumValueDefinition> valueDefinitionMap = new LinkedHashMap<>();
    private final EnumTypeDefinition definition;
    private final List<GraphQLDirective> directives;

    private final Coercing coercing = new Coercing() {
        @Override
        public Object serialize(Object input) {
            return getNameByValue(input);
        }

        @Override
        public Object parseValue(Object input) {
            return getValueByName(input);
        }

        private String typeName(Object input) {
            if (input == null) {
                return "null";
            }
            return input.getClass().getSimpleName();
        }

        @Override
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
                        "Expected enum literal value not in allowable values -  '" + String.valueOf(input) + "'."
                );
            }
            return enumValueDefinition.getValue();
        }
    };


    @Internal
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        this(name, description, values, emptyList(), null);
    }

    @Internal
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values, List<GraphQLDirective> directives, EnumTypeDefinition definition) {
        assertValidName(name);
        assertNotNull(directives, "directives cannot be null");

        this.name = name;
        this.description = description;
        this.definition = definition;
        this.directives = directives;
        buildMap(values);
    }

    public List<GraphQLEnumValueDefinition> getValues() {
        return new ArrayList<>(valueDefinitionMap.values());
    }

    public GraphQLEnumValueDefinition getValue(String name) {
        return valueDefinitionMap.get(name);
    }

    private void buildMap(List<GraphQLEnumValueDefinition> values) {
        for (GraphQLEnumValueDefinition valueDefinition : values) {
            String name = valueDefinition.getName();
            if (valueDefinitionMap.containsKey(name))
                throw new AssertException("value " + name + " redefined");
            valueDefinitionMap.put(name, valueDefinition);
        }
    }

    private Object getValueByName(Object value) {
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(value.toString());
        if (enumValueDefinition != null) return enumValueDefinition.getValue();
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Coercing getCoercing() {
        return coercing;
    }

    public EnumTypeDefinition getDefinition() {
        return definition;
    }

    @Override
    public List<GraphQLDirective> getDirectives() {
        return new ArrayList<>(directives);
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

    public static Builder newEnum() {
        return new Builder();
    }

    public static Builder newEnum(GraphQLEnumType existing) {
        return new Builder(existing);
    }

    public static class Builder {

        private String name;
        private String description;
        private EnumTypeDefinition definition;
        private final Map<String, GraphQLEnumValueDefinition> values = new LinkedHashMap<>();
        private final Map<String, GraphQLDirective> directives = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder(GraphQLEnumType existing) {
            this.name = existing.getName();
            this.description = existing.getDescription();
            this.definition = existing.getDefinition();
            this.values.putAll(getByName(existing.getValues(), GraphQLEnumValueDefinition::getName));
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

        public Builder definition(EnumTypeDefinition definition) {
            this.definition = definition;
            return this;
        }


        public Builder value(String name, Object value, String description, String deprecationReason) {
            value(new GraphQLEnumValueDefinition(name, description, value, deprecationReason));
            return this;
        }

        public Builder value(String name, Object value, String description) {
            return value(new GraphQLEnumValueDefinition(name, description, value));
        }

        public Builder value(String name, Object value) {
            assertNotNull(value, "value can't be null");
            return value(new GraphQLEnumValueDefinition(name, null, value));
        }

        public Builder value(String name) {
            return value(new GraphQLEnumValueDefinition(name, null, name));
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

        public Builder withDirectives(GraphQLDirective... directives) {
            assertNotNull(directives, "directives can't be null");
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

        public GraphQLEnumType build() {
            return new GraphQLEnumType(name, description, valuesToList(values), valuesToList(directives), definition);
        }
    }
}
