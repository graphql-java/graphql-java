package graphql.schema2;


import graphql.AssertException;
import graphql.Internal;
import graphql.PublicApi;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNullableType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnmodifiedType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.Assert.assertValidName;

/**
 * A graphql enumeration type has a limited set of values.
 *
 * This allows you to validate that any arguments of this type are one of the allowed values
 * and communicate through the type system that a field will always be one of a finite set of values.
 *
 * See http://graphql.org/learn/schema/#enumeration-types for more details
 */
@PublicApi
public class GraphQLEnumType implements GraphQLType, GraphQLInputType, GraphQLOutputType, GraphQLUnmodifiedType, GraphQLNullableType {

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
        throw new CoercingParseValueException("Invalid input for Enum '" + name + "'. No value found for name '" + value.toString() + "'");
    }

    private Object getNameByValue(Object value) {
        for (GraphQLEnumValueDefinition valueDefinition : valueDefinitionMap.values()) {
            Object definitionValue = valueDefinition.getValue();
            if (value.equals(definitionValue)) {
                return valueDefinition.getName();
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

}
