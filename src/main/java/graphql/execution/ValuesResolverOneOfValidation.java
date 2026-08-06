package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.ArrayValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.SchemaInputField;
import graphql.schema.SchemaInputObject;
import graphql.schema.SchemaList;
import graphql.schema.SchemaNonNull;
import graphql.schema.SchemaType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.isList;

@Internal
final class ValuesResolverOneOfValidation {

    @SuppressWarnings("unchecked")
    static void validateOneOfInputTypes(
            InputValueSchema schema,
            SchemaType type,
            Object inputValue,
            Value<?> argumentValue,
            String argumentName,
            Locale locale) {
        SchemaType unwrappedNonNullType = type instanceof SchemaNonNull
                ? GraphQLTypeUtil.unwrapOne(type)
                : type;

        if (isList(unwrappedNonNullType)
                && !ValuesResolverConversion.isNullValue(inputValue)
                && inputValue instanceof List
                && argumentValue instanceof ArrayValue) {
            SchemaType elementType =
                    ((SchemaList) unwrappedNonNullType).getWrappedType();
            List<Object> inputList = (List<Object>) inputValue;
            List<Value> argumentList = ((ArrayValue) argumentValue).getValues();

            for (int i = 0; i < argumentList.size(); i++) {
                validateOneOfInputTypes(
                        schema,
                        elementType,
                        inputList.get(i),
                        argumentList.get(i),
                        argumentName,
                        locale);
            }
        }

        if (unwrappedNonNullType instanceof SchemaInputObject
                && !ValuesResolverConversion.isNullValue(inputValue)) {
            Assert.assertTrue(
                    inputValue instanceof Map,
                    "The coerced argument %s SchemaInputObject is unexpectedly not a map",
                    argumentName);
            Map<String, Object> objectMap = (Map<String, Object>) inputValue;

            SchemaInputObject inputObjectType =
                    (SchemaInputObject) unwrappedNonNullType;

            if (inputObjectType.isOneOf()) {
                validateOneOfInputTypesInternal(inputObjectType, argumentValue, objectMap, locale);
            }

            for (SchemaInputField fieldDefinition :
                    schema.getInputFields(inputObjectType)) {
                SchemaType childFieldType = fieldDefinition.getType();
                String childFieldName = fieldDefinition.getName();
                Object childFieldInputValue = objectMap.get(childFieldName);

                if (argumentValue instanceof ObjectValue) {
                    List<Value> values = ((ObjectValue) argumentValue).getObjectFields().stream()
                            .filter(of -> of.getName().equals(childFieldName))
                            .map(ObjectField::getValue)
                            .collect(Collectors.toList());

                    if (values.size() > 1) {
                        Assert.assertShouldNeverHappen("argument %s has %s object fields with the same name: '%s'. A maximum of 1 is expected", argumentName, values.size(), childFieldName);
                    } else if (!values.isEmpty()) {
                        validateOneOfInputTypes(
                                schema,
                                childFieldType,
                                childFieldInputValue,
                                values.get(0),
                                argumentName,
                                locale);
                    }
                } else {
                    validateOneOfInputTypes(
                            schema,
                            childFieldType,
                            childFieldInputValue,
                            argumentValue,
                            argumentName,
                            locale);
                }
            }
        }
    }

    private static void validateOneOfInputTypesInternal(
            SchemaInputObject oneOfInputType,
            Value<?> argumentValue,
            Map<String, Object> objectMap,
            Locale locale) {
        final String fieldName;
        if (argumentValue instanceof ObjectValue) {
            List<ObjectField> objectFields = ((ObjectValue) argumentValue).getObjectFields();
            if (objectFields.size() != 1) {
                throwNotOneFieldError(oneOfInputType, locale);
            }

            fieldName = objectFields.iterator().next().getName();
        } else {
            if (objectMap.size() != 1) {
                throwNotOneFieldError(oneOfInputType, locale);
            }

            fieldName = objectMap.keySet().iterator().next();
        }

        if (objectMap.get(fieldName) == null) {
            throwValueIsNullError(oneOfInputType, locale, fieldName);
        }
    }

    private static void throwValueIsNullError(
            SchemaInputObject oneOfInputType,
            Locale locale,
            String fieldName) {
        String msg = I18n.i18n(I18n.BundleType.Execution, locale)
                .msg("Execution.handleOneOfValueIsNullError", oneOfInputType.getName() + "." + fieldName);
        throw new OneOfNullValueException(msg);
    }

    private static void throwNotOneFieldError(
            SchemaInputObject oneOfInputType,
            Locale locale) {
        String msg = I18n.i18n(I18n.BundleType.Execution, locale)
                .msg("Execution.handleOneOfNotOneFieldError", oneOfInputType.getName());
        throw new OneOfTooManyKeysException(msg);
    }
}
