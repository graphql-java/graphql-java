package graphql.validation;

import graphql.GraphQLError;
import graphql.language.Argument;
import graphql.language.ObjectField;
import graphql.language.Value;
import graphql.schema.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArgumentValidationUtil extends ValidationUtil {

    private final List<String> argumentNames = new ArrayList<>();
    private Value<?> argumentValue;
    private String errorMessage;
    private final List<Object> arguments = new ArrayList<>();
    private GraphQLType requiredType;
    private GraphQLType objectType;
    private Map<String, Object> errorExtensions;

    private final String argumentName;

    public ArgumentValidationUtil(Argument argument) {
        argumentName = argument.getName();
        argumentValue = argument.getValue();
    }

    @Override
    protected void handleNullError(Value value, GraphQLType type) {
        errorMessage = "Value must not be null";
        argumentValue = value;
        setObjectField(type);
    }

    @Override
    protected void handleScalarError(Value value, GraphQLScalarType type, String message) {
        errorMessage = message;
        arguments.add(type.getName());
        arguments.add(invalid.getMessage());
        argumentValue = value;
        setObjectField(type);
    }

    @Override
    protected void handleEnumError(Value<?> value, GraphQLEnumType type, GraphQLError invalid) {
        errorMessage = "is not a valid '%s' - %s";
        arguments.add(type.getName());
        arguments.add(invalid.getMessage());
        argumentValue = value;
        requiredType = type;
    }

    @Override
    protected void handleNotObjectError(Value value, GraphQLInputObjectType type) {
        errorMessage = "Value must be an object type";
        setObjectField(type);
    }

    @Override
    protected void handleMissingFieldsError(Value value, GraphQLInputObjectType type, Set<String> missingFields) {
        errorMessage = String.format("Required fields are missing: '%s'", missingFields.stream().collect(Collectors.joining(", ", "[", "]")));
        arguments.add(missingFields);
        setObjectField(type);
    }

    @Override
    protected void handleExtraFieldError(Value value, GraphQLInputObjectType type, ObjectField objectField) {
        errorMessage = String.format("Value contains a field not in '%s': '%s'", ValidationUtil.renderType(type), objectField.getName());
        arguments.add(type.getName());
        arguments.add(objectField.getName());
        setObjectField(type);
    }

    @Override
    protected void handleFieldNotValidError(ObjectField objectField, GraphQLInputObjectType type) {
        argumentNames.add(0, objectField.getName());
        setObjectField(type);
    }

    @Override
    protected void handleFieldNotValidError(Value<?> value, GraphQLType type, int index) {
        argumentNames.add(0, String.format("[%s]", index));
        setObjectField(type);
    }

    private void setObjectField(GraphQLType type) {
        objectType = type;
        if (requiredType == null) {
            requiredType = type;
        }
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Value<?> getArgumentValue() {
        return argumentValue;
    }

    public GraphQLType getRequiredType() {
        return requiredType;
    }

    public GraphQLType getObjectType() {
        return objectType;
    }

    public String renderArgument() {
        StringBuilder argument = new StringBuilder(argumentName);
        for (String name : argumentNames) {
            if (!name.startsWith("[")) {
                argument.append('.');
            }
            argument.append(name);
        }
        return argument.toString();
    }

    public Map<String, Object> getErrorExtensions() {
        return errorExtensions;
    }
}
