package graphql.execution;

import graphql.Assert;
import graphql.Internal;
import graphql.i18n.I18n;
import graphql.language.ArrayValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.isList;

@Internal
final class ValuesResolverOneOfValidation {

    @SuppressWarnings("unchecked")
    static void validateOneOfInputTypes(GraphQLType type, Object inputValue, Value<?> argumentValue, String argumentName, Locale locale) {
        GraphQLType unwrappedNonNullType = GraphQLTypeUtil.unwrapNonNull(type);

        if (isList(unwrappedNonNullType)
                && !ValuesResolverConversion.isNullValue(inputValue)
                && inputValue instanceof List
                && argumentValue instanceof ArrayValue) {
            GraphQLType elementType = ((GraphQLList) unwrappedNonNullType).getWrappedType();
            List<Object> inputList = (List<Object>) inputValue;
            List<Value> argumentList = ((ArrayValue) argumentValue).getValues();

            for (int i = 0; i < argumentList.size(); i++) {
                validateOneOfInputTypes(elementType, inputList.get(i), argumentList.get(i), argumentName, locale);
            }
        }

        if (unwrappedNonNullType instanceof GraphQLInputObjectType && !ValuesResolverConversion.isNullValue(inputValue)) {
            Assert.assertTrue(inputValue instanceof Map, () -> String.format("The coerced argument %s GraphQLInputObjectType is unexpectedly not a map", argumentName));
            Map<String, Object> objectMap = (Map<String, Object>) inputValue;

            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) unwrappedNonNullType;

            if (inputObjectType.isOneOf()) {
                validateOneOfInputTypesInternal(inputObjectType, argumentValue, objectMap, locale);
            }

            for (GraphQLInputObjectField fieldDefinition : inputObjectType.getFields()) {
                GraphQLInputType childFieldType = fieldDefinition.getType();
                String childFieldName = fieldDefinition.getName();
                Object childFieldInputValue = objectMap.get(childFieldName);

                if (argumentValue instanceof ObjectValue) {
                    List<Value> values = ((ObjectValue) argumentValue).getObjectFields().stream()
                            .filter(of -> of.getName().equals(childFieldName))
                            .map(ObjectField::getValue)
                            .collect(Collectors.toList());

                    if (values.size() > 1) {
                        Assert.assertShouldNeverHappen(String.format("argument %s has %s object fields with the same name: '%s'. A maximum of 1 is expected", argumentName, values.size(), childFieldName));
                    } else if (!values.isEmpty()) {
                        validateOneOfInputTypes(childFieldType, childFieldInputValue, values.get(0), argumentName, locale);
                    }
                } else {
                    validateOneOfInputTypes(childFieldType, childFieldInputValue, argumentValue, argumentName, locale);
                }
            }
        }
    }

    private static void validateOneOfInputTypesInternal(GraphQLInputObjectType oneOfInputType, Value<?> argumentValue, Map<String, Object> objectMap, Locale locale) {
        int mapSize;

        if (argumentValue instanceof ObjectValue) {
            mapSize = ((ObjectValue) argumentValue).getObjectFields().size();
        } else {
            mapSize = objectMap.size();
        }
        if (mapSize != 1) {
            String msg = I18n.i18n(I18n.BundleType.Execution, locale)
                    .msg("Execution.handleOneOfNotOneFieldError", oneOfInputType.getName());
            throw new OneOfTooManyKeysException(msg);
        }
        String fieldName = objectMap.keySet().iterator().next();
        if (objectMap.get(fieldName) == null) {
            String msg = I18n.i18n(I18n.BundleType.Execution, locale)
                    .msg("Execution.handleOneOfValueIsNullError", oneOfInputType.getName() + "." + fieldName);
            throw new OneOfNullValueException(msg);
        }
    }
}
