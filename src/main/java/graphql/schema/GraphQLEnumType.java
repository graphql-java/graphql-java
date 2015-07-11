package graphql.schema;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphQLEnumType implements GraphQLType, GraphQLInputType, GraphQLOutputType {

    private final String name;
    private final String description;
    private final Map<String, GraphQLEnumValueDefinition> valueDefinitionMap = new LinkedHashMap<>();

    private Coercing coercing;

    public GraphQLEnumType(String name, String description, List<GraphQLEnumValueDefinition> values) {
        this.name = name;
        this.description = description;
        buildMap(values);
    }

    private void buildMap(List<GraphQLEnumValueDefinition> values) {
        for (GraphQLEnumValueDefinition valueDefinition : values) {
            valueDefinitionMap.put(valueDefinition.getName(), valueDefinition);
        }
    }

    public String getName() {
        return name;
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

        public Builder value(String name) {
            values.add(new GraphQLEnumValueDefinition(name, null, null));
            return this;
        }


        public GraphQLEnumType build() {
            return new GraphQLEnumType(name, description, values);
        }

    }
}
