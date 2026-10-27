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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.isList;

@Internal
final class ValuesResolverOneOfValidation {

    /**
     * Per input object type: true if the type itself or any transitively reachable input object type is a
     * @oneOf type. Input types are immutable once built, so this answer is stable for a given type instance.
     */
    private static final Map<GraphQLInputObjectType, Boolean> CONTAINS_ONE_OF = new ConcurrentHashMap<>();

    static void validateOneOfInputTypes(GraphQLType type, Object inputValue, Value<?> argumentValue, String argumentName, Locale locale) {
        // the walk below can only do something when a @oneOf type is reachable - skip it otherwise
        if (!mayContainOneOf(type)) {
            return;
        }
        validateOneOfInputTypesImpl(type, inputValue, argumentValue, argumentName, locale);
    }

    private static boolean mayContainOneOf(GraphQLType type) {
        GraphQLType unwrappedType = unwrapToNamed(type);
        return unwrappedType instanceof GraphQLInputObjectType
                && CONTAINS_ONE_OF.computeIfAbsent((GraphQLInputObjectType) unwrappedType, ValuesResolverOneOfValidation::computeContainsOneOf);
    }

    private static boolean computeContainsOneOf(GraphQLInputObjectType type) {
        return containsOneOf(type, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    // uncached recursive check with cycle protection for self-referencing input types
    private static boolean containsOneOf(GraphQLType type, Set<GraphQLType> visiting) {
        GraphQLType unwrappedType = unwrapToNamed(type);
        if (!(unwrappedType instanceof GraphQLInputObjectType)) {
            return false;
        }
        GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) unwrappedType;
        if (inputObjectType.isOneOf()) {
            return true;
        }
        if (!visiting.add(inputObjectType)) {
            return false;
        }
        for (GraphQLInputObjectField fieldDefinition : inputObjectType.getFields()) {
            if (containsOneOf(fieldDefinition.getType(), visiting)) {
                return true;
            }
        }
        return false;
    }

    private static GraphQLType unwrapToNamed(GraphQLType type) {
        GraphQLType unwrappedType = GraphQLTypeUtil.unwrapNonNull(type);
        while (isList(unwrappedType)) {
            unwrappedType = GraphQLTypeUtil.unwrapNonNull(((GraphQLList) unwrappedType).getWrappedType());
        }
        return unwrappedType;
    }

    @SuppressWarnings("unchecked")
    private static void validateOneOfInputTypesImpl(GraphQLType type, Object inputValue, Value<?> argumentValue, String argumentName, Locale locale) {
        GraphQLType unwrappedNonNullType = GraphQLTypeUtil.unwrapNonNull(type);

        if (isList(unwrappedNonNullType)
                && !ValuesResolverConversion.isNullValue(inputValue)
                && inputValue instanceof List) {
            GraphQLType elementType = ((GraphQLList) unwrappedNonNullType).getWrappedType();
            List<Object> inputList = (List<Object>) inputValue;

            if (argumentValue instanceof ArrayValue) {
                List<Value> argumentList = ((ArrayValue) argumentValue).getValues();
                for (int i = 0; i < argumentList.size(); i++) {
                    validateOneOfInputTypes(elementType, inputList.get(i), argumentList.get(i), argumentName, locale);
                }
            } else {
                // a single literal coerced into a list, or a list resolved from a variable reference
                for (Object inputElement : inputList) {
                    validateOneOfInputTypes(elementType, inputElement, argumentValue, argumentName, locale);
                }
            }
        }

        if (unwrappedNonNullType instanceof GraphQLInputObjectType && !ValuesResolverConversion.isNullValue(inputValue)) {
            Assert.assertTrue(inputValue instanceof Map, "The coerced argument %s GraphQLInputObjectType is unexpectedly not a map", argumentName);
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
                        Assert.assertShouldNeverHappen("argument %s has %s object fields with the same name: '%s'. A maximum of 1 is expected", argumentName, values.size(), childFieldName);
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

    private static void throwValueIsNullError(GraphQLInputObjectType oneOfInputType, Locale locale, String fieldName) {
        String msg = I18n.i18n(I18n.BundleType.Execution, locale)
                .msg("Execution.handleOneOfValueIsNullError", oneOfInputType.getName() + "." + fieldName);
        throw new OneOfNullValueException(msg);
    }

    private static void throwNotOneFieldError(GraphQLInputObjectType oneOfInputType, Locale locale) {
        String msg = I18n.i18n(I18n.BundleType.Execution, locale)
                .msg("Execution.handleOneOfNotOneFieldError", oneOfInputType.getName());
        throw new OneOfTooManyKeysException(msg);
    }
}
