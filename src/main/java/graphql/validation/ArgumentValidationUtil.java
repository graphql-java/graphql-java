package graphql.validation;

import graphql.GraphQLError;
import graphql.Internal;
import graphql.i18n.I18nMsg;
import graphql.language.Argument;
import graphql.language.ObjectField;
import graphql.language.Value;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
public class ArgumentValidationUtil extends ValidationUtil {

    private final List<String> argumentNames = new ArrayList<>();
    private Value<?> argumentValue;
    private String errMsgKey;
    private final List<Object> arguments = new ArrayList<>();
    private Map<String, Object> errorExtensions;

    private final String argumentName;

    public ArgumentValidationUtil(Argument argument) {
        argumentName = argument.getName();
        argumentValue = argument.getValue();
    }

    @Override
    protected void handleNullError(Value<?> value, GraphQLType type) {
        errMsgKey = "ArgumentValidationUtil.handleNullError";
        argumentValue = value;
    }

    @Override
    protected void handleScalarError(Value<?> value, GraphQLScalarType type, GraphQLError invalid) {
        if (invalid.getMessage() == null) {
            errMsgKey = "ArgumentValidationUtil.handleScalarError";
        } else {
            errMsgKey = "ArgumentValidationUtil.handleScalarErrorCustomMessage";
        }
        arguments.add(type.getName());
        arguments.add(invalid.getMessage());
        argumentValue = value;
        errorExtensions = invalid.getExtensions();
    }

    @Override
    protected void handleEnumError(Value<?> value, GraphQLEnumType type, GraphQLError invalid) {
        if (invalid.getMessage() == null) {
            errMsgKey = "ArgumentValidationUtil.handleEnumError";
        } else {
            errMsgKey = "ArgumentValidationUtil.handleEnumErrorCustomMessage";
        }
        arguments.add(type.getName());
        arguments.add(invalid.getMessage());
        argumentValue = value;
    }

    @Override
    protected void handleNotObjectError(Value<?> value, GraphQLInputObjectType type) {
        errMsgKey = "ArgumentValidationUtil.handleNotObjectError";
    }

    @Override
    protected void handleMissingFieldsError(Value<?> value, GraphQLInputObjectType type, Set<String> missingFields) {
        errMsgKey = "ArgumentValidationUtil.handleMissingFieldsError";
        arguments.add(missingFields);
    }

    @Override
    protected void handleExtraFieldError(Value<?> value, GraphQLInputObjectType type, ObjectField objectField) {
        errMsgKey = "ArgumentValidationUtil.handleExtraFieldError";
        arguments.add(type.getName());
        arguments.add(objectField.getName());
    }

    @Override
    protected void handleFieldNotValidError(ObjectField objectField, GraphQLInputObjectType type) {
        argumentNames.add(0, objectField.getName());
    }

    @Override
    protected void handleFieldNotValidError(Value<?> value, GraphQLType type, int index) {
        argumentNames.add(0, String.format("[%s]", index));
    }

    @Override
    protected void handleExtraOneOfFieldsError(GraphQLInputObjectType type, Value<?> value) {
        errMsgKey = "ArgumentValidationUtil.extraOneOfFieldsError";
        arguments.add(type.getName());
    }

    public I18nMsg getMsgAndArgs() {
        StringBuilder argument = new StringBuilder(argumentName);
        for (String name : argumentNames) {
            if (name.startsWith("[")) {
                argument.append(name);
            } else {
                argument.append(".").append(name);
            }
        }
        arguments.add(0, argument.toString());
        arguments.add(1, argumentValue);

        return new I18nMsg(errMsgKey, arguments);
    }

    public Map<String, Object> getErrorExtensions() {
        return errorExtensions;
    }
}
