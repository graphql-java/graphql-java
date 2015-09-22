package graphql.schema;


import graphql.GraphQLException;
import graphql.language.EnumValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertNotNull;

public class GraphQLEnumType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLEnumValueDefinition> valueDefinitionMap = new LinkedHashMap<>();

    private final Coercing coercing = new Coercing() {
        @Override
        public Object coerce(Object input) {
            return getNameByValue(input);
        }

        @Override
        public Object coerceLiteral(Object input) {
            if (!(input instanceof EnumValue)) return null;
            EnumValue enumValue = (EnumValue) input;
            GraphQLEnumValueDefinition enumValueDefinition = valueDefinitionMap.get(enumValue.getName());
            if (enumValueDefinition.getValue() != null) return enumValueDefinition.getValue();
            return enumValueDefinition.getName();
        }
    };

    private Object getNameByValue(Object value) {
        for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
            if (value.equals(valueDefinition.getValue())) return valueDefinition.getName();
        }
        throw new GraphQLException("");
    }

    public List<GraphQLEnumValueDefinition> getValues() {
        return new ArrayList<>(valueDefinitionMap.values());
    }


    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        assertNotNull(name, "name can't null");
        this.name = name;
        this.description = description;
        buildMap(values);
    }

    private void buildMap(List<GraphQLEnumValueDefinition> values) {
        for (GraphQLEnumValueDefinition valueDefinition : values) {
            valueDefinitionMap.put(valueDefinition.getName(), valueDefinition);
        }
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

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GraphQLType that = (GraphQLType) o;

        return getName().equals(that.getName());

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public static class Builder {

        private String name;
        private String description;
        private final List<GraphQLEnumValueDefinition> values = new ArrayList<>();

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
