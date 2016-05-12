package graphql.schema;


import graphql.language.EnumValue;
import graphql.AssertException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQLEnumType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLEnumValueDefinition> valueDefinitionMap = new LinkedHashMap<String, GraphQLEnumValueDefinition>();

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

    private Object getValueByName(Object value) {
        GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(value);
        if (enumValueDefinition != null) return enumValueDefinition.getValue();
        return null;
    }

    private Object getNameByValue(Object value) {
        if (value == null) {
            for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
                if (valueDefinition.getValue() == null) return valueDefinition.getName();
            }
        } else {
            for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
                if (value.equals(valueDefinition.getValue())) return valueDefinition.getName();
            }
        }
        return null;
    }

    public List<GraphQLEnumValueDefinition> getValues() {
        return new ArrayList<GraphQLEnumValueDefinition>(valueDefinitionMap.values());
    }


    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        assertNotNull(name, "name can't be null");
        this.name = name;
        this.description = description;
        buildMap(values);
    }

    private void buildMap(List<GraphQLEnumValueDefinition> values) {
        for (GraphQLEnumValueDefinition valueDefinition : values) {
            String name = valueDefinition.getName();
            if (valueDefinitionMap.containsKey(name))
                throw new AssertException("value " + name + " redefined");
            valueDefinitionMap.put(name, valueDefinition);
        }
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


    public static Builder newEnum() {
        return new Builder();
    }

    public static class Builder {

        private String name;
        private String description;
        private final List<GraphQLEnumValueDefinition> values = new ArrayList<GraphQLEnumValueDefinition>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder value(String name, Object value, String description) {
            values.add(new GraphQLEnumValueDefinition(name, description, value));
            return this;
        }

        public Builder value(String name, Object value) {
            values.add(new GraphQLEnumValueDefinition(name, null, value));
            return this;
        }

        public Builder value(String name) {
            values.add(new GraphQLEnumValueDefinition(name, null, name));
            return this;
        }


        public GraphQLEnumType build() {
            return new GraphQLEnumType(name, description, values);
        }

    }
}
