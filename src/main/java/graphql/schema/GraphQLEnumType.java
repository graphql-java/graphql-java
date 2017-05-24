package graphql.schema;


import graphql.AssertException;
import graphql.GraphQLException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static graphql.Assert.assertValidName;

@PublicApi
public class GraphQLEnumType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLEnumValueDefinition> valueDefinitionMap = new LinkedHashMap<>();
    private final EnumTypeDefinition definition;

    private final Coercing coercing = new Coercing() {
        @Override
        public Object serialize(Object input) {
            return getNameByValue(input);
        }

        @Override
        public Object parseValue(Object input) {
            return getValueByName(input);
        }

        @Override
        public Object parseLiteral(Object input) {
            if (!(input instanceof EnumValue)) return null;
            EnumValue enumValue = (EnumValue) input;
            GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(enumValue.getName());
            if (enumValueDefinition == null) return null;
            return enumValueDefinition.getValue();
        }
    };


    @Internal
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        this(name, description, values, null);
    }

    @Internal
    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values, EnumTypeDefinition definition) {
        assertValidName(name);
        this.name = name;
        this.description = description;
        this.definition = definition;
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
        throw new GraphQLException("Invalid input for Enum '" + name + "'. No value found for name " + value.toString());
    }

    private Object getNameByValue(Object value) {
        for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
            if (value.equals(valueDefinition.getValue())) return valueDefinition.getName();
        }
        throw new GraphQLException("Invalid input for Enum '" + name + "'. Unknown value " + value);
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

    public static Builder newEnum() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private EnumTypeDefinition definition;
        private final List<GraphQLEnumValueDefinition> values = new ArrayList<>();

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
            values.add(new GraphQLEnumValueDefinition(name, description, value, deprecationReason));
            return this;
        }

        public Builder value(String name, Object value, String description) {
            values.add(new GraphQLEnumValueDefinition(name, description, value));
            return this;
        }

        public Builder value(String name, Object value) {
            assertNotNull(value, "value can't be null");
            values.add(new GraphQLEnumValueDefinition(name, null, value));
            return this;
        }

        public Builder value(String name) {
            values.add(new GraphQLEnumValueDefinition(name, null, name));
            return this;
        }


        public GraphQLEnumType build() {
            return new GraphQLEnumType(name, description, values, definition);
        }

    }
}
