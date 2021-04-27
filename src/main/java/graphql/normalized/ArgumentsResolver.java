package graphql.normalized;

import graphql.Assert;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.TypeFromAST;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.visibility.GraphqlFieldVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY;

public class ArgumentsResolver {

    // 6.1.2 Coercing Variable Values
    public Map<String, Object> coerceVariableValues(GraphQLSchema schema, List<VariableDefinition> variableDefinitions, Map<String, Object> variableValues) {
        GraphqlFieldVisibility fieldVisibility = schema.getCodeRegistry().getFieldVisibility();
        Map<String, Object> coercedValues = new LinkedHashMap<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            String variableName = variableDefinition.getName();
            List<Object> nameStack = new ArrayList<>();
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
            Assert.assertTrue(variableType instanceof GraphQLInputType);
            // can be NullValue
            Value defaultValue = variableDefinition.getDefaultValue();
            boolean hasValue = variableValues.containsKey(variableName);
            Object value = variableValues.get(variableName);
            if (!hasValue && defaultValue != null) {
                coercedValues.put(variableName, defaultValue);
            } else if (isNonNull(variableType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(variableDefinition, variableType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(variableName, null);
                } else {
                    Object coercedValue = coerceValue(fieldVisibility, variableDefinition, variableDefinition.getName(), variableType, value, nameStack);
                    coercedValues.put(variableName, coercedValue);
                }
            } else {
                // hasValue = false && defaultValue == null for a nullable type
                // meaning no value was provided for variableName
            }
        }

        return coercedValues;
    }


    public Map<String, Object> getArgumentValues(List<GraphQLArgument> argumentTypes, List<Argument> arguments, Map<String, Object> variables) {
        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(DEFAULT_FIELD_VISIBILITY).build();
        return getArgumentValuesImpl(codeRegistry, argumentTypes, arguments, variables);
    }

    // 6.4.1 Coercing Field Arguments
    private Map<String, Object> getArgumentValuesImpl(GraphQLCodeRegistry codeRegistry,
                                                      List<GraphQLArgument> argumentTypes,
                                                      List<Argument> arguments,
                                                      Map<String, Object> coercedVariableValues) {
        if (argumentTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> coercedValues = new LinkedHashMap<>();
        Map<String, Argument> argumentMap = argumentMap(arguments);
        for (GraphQLArgument argumentDefinition : argumentTypes) {
            GraphQLInputType argumentType = argumentDefinition.getType();
            String argumentName = argumentDefinition.getName();
            Argument argument = argumentMap.get(argumentName);
            Object defaultValue = argumentDefinition.getDefaultValue();
            boolean hasValue = argument != null;
            Object value;
            Value argumentValue = argument.getValue();
            if (argumentValue instanceof VariableReference) {
                String variableName = ((VariableReference) argumentValue).getName();
                hasValue = coercedVariableValues.containsKey(variableName);
                value = coercedVariableValues.get(variableName);
            } else {
                value = argumentValue;
            }
            if (!hasValue && argumentDefinition.hasSetDefaultValue()) {
                // default value needs to be coerced
                coercedValues.put(argumentName, defaultValue);
            } else if (isNonNull(argumentType) && (!hasValue || value == null)) {
                throw new RuntimeException();
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(argumentName, null);
                } else if (argumentValue instanceof VariableReference) {
                    coercedValues.put(argumentName, value);
                } else {
                    value = coerceValueAst(codeRegistry.getFieldVisibility(), argumentType, argument.getValue(), coercedVariableValues);
                    coercedValues.put(argumentName, value);
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for argumentName
            }

        }
        return coercedValues;

    }

    private Object coerceValueForScalar(GraphQLScalarType graphQLScalarType, Object value) {
        return graphQLScalarType.getCoercing().parseValue(value);
    }

    private Object coerceValueForEnum(GraphQLEnumType graphQLEnumType, Object value) {
        return graphQLEnumType.parseValue(value);
    }

    private List coerceValueForList(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLList graphQLList, Object value, List<Object> nameStack) {
        if (value instanceof Iterable) {
            List<Object> result = new ArrayList<>();
            for (Object val : (Iterable) value) {
                result.add(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), val, nameStack));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValue(fieldVisibility, variableDefinition, inputName, graphQLList.getWrappedType(), value, nameStack));
        }
    }

    private Object coerceValueAst(GraphqlFieldVisibility fieldVisibility, GraphQLType type, Value inputValue, Map<String, Object> coercedVariableValues) {

        if (inputValue instanceof NullValue) {
            return null;
        }
        if (type instanceof GraphQLScalarType) {
            return parseLiteral(inputValue, ((GraphQLScalarType) type).getCoercing(), coercedVariableValues);
        }
        if (isNonNull(type)) {
            return coerceValueAst(fieldVisibility, unwrapOne(type), inputValue, coercedVariableValues);
        }
        if (type instanceof GraphQLInputObjectType) {
            return coerceValueAstForInputObject(fieldVisibility, (GraphQLInputObjectType) type, (ObjectValue) inputValue, coercedVariableValues);
        }
        if (type instanceof GraphQLEnumType) {
            return ((GraphQLEnumType) type).parseLiteral(inputValue);
        }
        if (isList(type)) {
            return coerceValueAstForList(fieldVisibility, (GraphQLList) type, inputValue, coercedVariableValues);
        }
        return null;
    }

    private Object coerceValueAstForList(GraphqlFieldVisibility fieldVisibility, GraphQLList graphQLList, Value value, Map<String, Object> variables) {
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            List<Object> result = new ArrayList<>();
            for (Value singleValue : arrayValue.getValues()) {
                result.add(coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), singleValue, variables));
            }
            return result;
        } else {
            return Collections.singletonList(coerceValueAst(fieldVisibility, graphQLList.getWrappedType(), value, variables));
        }
    }

    private Map<String, ObjectField> mapObjectValueFieldsByName(ObjectValue inputValue) {
        Map<String, ObjectField> inputValueFieldsByName = new LinkedHashMap<>();
        for (ObjectField objectField : inputValue.getObjectFields()) {
            inputValueFieldsByName.put(objectField.getName(), objectField);
        }
        return inputValueFieldsByName;
    }

    private Object coerceValueAstForInputObject(GraphqlFieldVisibility fieldVisibility,
                                                GraphQLInputObjectType type,
                                                ObjectValue inputValue,
                                                Map<String, Object> coercedVariableValues) {
        Map<String, Object> coercedValues = new LinkedHashMap<>();

        Map<String, ObjectField> inputFieldsByName = mapObjectValueFieldsByName(inputValue);

        List<GraphQLInputObjectField> inputFieldTypes = fieldVisibility.getFieldDefinitions(type);
        for (GraphQLInputObjectField inputFieldType : inputFieldTypes) {

//            Object defaultValue = inputFieldType.getDefaultValue();
//            String fieldName = inputFieldType.getName();

            GraphQLInputType fieldType = inputFieldType.getType();
            String fieldName = inputFieldType.getName();
            ObjectField field = inputFieldsByName.get(fieldName);
            Object defaultValue = inputFieldType.getDefaultValue();
            boolean hasValue = field != null;
            Object value;
            Value fieldValue = field.getValue();
            if (fieldValue instanceof VariableReference) {
                String variableName = ((VariableReference) fieldValue).getName();
                hasValue = coercedVariableValues.containsKey(variableName);
                value = coercedVariableValues.get(variableName);
            } else {
                value = fieldValue;
            }
            if (!hasValue && inputFieldType.getDefaultValue() != null /*should be hasSetDefaultValue */) {
                // default value should be coerced
                coercedValues.put(fieldName, defaultValue);
            } else if (isNonNull(fieldType) && (!hasValue || value == null)) {
                throw new NonNullableValueCoercedAsNullException(inputFieldType);
            } else if (hasValue) {
                if (value == null) {
                    coercedValues.put(fieldName, null);
                } else if (fieldValue instanceof VariableReference) {
                    coercedValues.put(fieldName, value);
                } else {
                    value = coerceValueAst(fieldVisibility, fieldType, fieldValue, coercedVariableValues);
                    coercedValues.put(fieldName, value);
                }
            } else {
                // nullable type && hasValue == false && hasDefaultValue == false
                // meaning no value was provided for this field
            }

//            if (inputFieldsByName.containsKey(inputFieldType.getName())) {
//                boolean putObjectInMap = true;
//
//                ObjectField field = inputFieldsByName.get(inputFieldType.getName());
//                Value fieldInputValue = field.getValue();
//
//                Object fieldObject = null;
//                if (fieldInputValue instanceof VariableReference) {
//                    String varName = ((VariableReference) fieldInputValue).getName();
//                    if (!coercedVariableValues.containsKey(varName)) {
//                        putObjectInMap = false;
//                    } else {
//                        fieldObject = coercedVariableValues.get(varName);
//                    }
//                } else {
//                    fieldObject = coerceValueAst(fieldVisibility, inputFieldType.getType(), fieldInputValue, coercedVariableValues);
//                }
//
//                if (fieldObject == null) {
//                    if (!(field.getValue() instanceof NullValue)) {
//                        fieldObject = inputFieldType.getDefaultValue();
//                    }
//                }
//                if (putObjectInMap) {
//                    result.put(field.getName(), fieldObject);
//                } else {
//                    assertNonNullInputField(inputFieldType);
//                }
//            } else if (inputFieldType.getDefaultValue() != null) {
//                result.put(inputFieldType.getName(), inputFieldType.getDefaultValue());
//            } else {
//                assertNonNullInputField(inputFieldType);
//            }
        }
        return result;
    }

    private void assertNonNullInputField(GraphQLInputObjectField inputTypeField) {
        if (isNonNull(inputTypeField.getType())) {
            throw new NonNullableValueCoercedAsNullException(inputTypeField);
        }
    }

    private Object parseLiteral(Value inputValue, Coercing coercing, Map<String, Object> variables) {
        // the CoercingParseLiteralException exception that could happen here has been validated earlier via ValidationUtil
        return coercing.parseLiteral(inputValue, variables);
    }


    private Object coerceValue(GraphqlFieldVisibility fieldVisibility, VariableDefinition variableDefinition, String inputName, GraphQLType graphQLType, Object value, List<Object> nameStack) {

    }


    private Map<String, Argument> argumentMap(List<Argument> arguments) {
        Map<String, Argument> result = new LinkedHashMap<>(arguments.size());
        for (Argument argument : arguments) {
            result.put(argument.getName(), argument);
        }
        return result;
    }


}
