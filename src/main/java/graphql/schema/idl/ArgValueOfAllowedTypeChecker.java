package graphql.schema.idl;

import graphql.AssertException;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumTypeExtensionDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputObjectTypeExtensionDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.ScalarTypeDefinition;
import graphql.language.ScalarTypeExtensionDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.collect.ImmutableKit.emptyList;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.DUPLICATED_KEYS_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_ENUM_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_LIST_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_NON_NULL_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_OBJECT_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.MISSING_REQUIRED_FIELD_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.MUST_BE_VALID_ENUM_VALUE_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.NOT_A_VALID_SCALAR_LITERAL_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.UNKNOWN_FIELDS_MESSAGE;
import static java.lang.String.format;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Class to check whether a given directive argument value
 * matches a given directive definition.
 */
@Internal
class ArgValueOfAllowedTypeChecker {

    private final Directive directive;
    private final Node<?> element;
    private final String elementName;
    private final Argument argument;
    private final TypeDefinitionRegistry typeRegistry;
    private final RuntimeWiring runtimeWiring;

    ArgValueOfAllowedTypeChecker(final Directive directive,
                                 final Node<?> element,
                                 final String elementName,
                                 final Argument argument,
                                 final TypeDefinitionRegistry typeRegistry,
                                 final RuntimeWiring runtimeWiring) {
        this.directive = directive;
        this.element = element;
        this.elementName = elementName;
        this.argument = argument;
        this.typeRegistry = typeRegistry;
        this.runtimeWiring = runtimeWiring;
    }

    /**
     * Recursively inspects an argument value given an allowed type.
     * Given the (invalid) SDL below:
     * <p>
     * directive @myDirective(arg: [[String]] ) on FIELD_DEFINITION
     * <p>
     * query {
     * f: String @myDirective(arg: ["A String"])
     * }
     * <p>
     * it will first check that the `myDirective.arg` type is an array
     * and fail when finding "A String" as it expected a nested array ([[String]]).
     *
     * @param errors         validation error collector
     * @param instanceValue  directive argument value
     * @param allowedArgType directive definition argument allowed type
     */
    void checkArgValueMatchesAllowedType(List<GraphQLError> errors, Value<?> instanceValue, Type<?> allowedArgType) {
        if (allowedArgType instanceof TypeName) {
            checkArgValueMatchesAllowedTypeName(errors, instanceValue, allowedArgType);
        } else if (allowedArgType instanceof ListType) {
            checkArgValueMatchesAllowedListType(errors, instanceValue, (ListType) allowedArgType);
        } else if (allowedArgType instanceof NonNullType) {
            checkArgValueMatchesAllowedNonNullType(errors, instanceValue, (NonNullType) allowedArgType);
        } else {
            assertShouldNeverHappen("Unsupported Type '%s' was added. ", allowedArgType);
        }
    }

    private void addValidationError(List<GraphQLError> errors, String message, Object... args) {
        errors.add(new DirectiveIllegalArgumentTypeError(element, elementName, directive.getName(), argument.getName(), format(message, args)));
    }

    private void checkArgValueMatchesAllowedTypeName(List<GraphQLError> errors, Value<?> instanceValue, Type<?> allowedArgType) {
        if (instanceValue instanceof NullValue) {
            return;
        }

        String allowedTypeName = ((TypeName) allowedArgType).getName();
        TypeDefinition<?> allowedTypeDefinition = typeRegistry.getType(allowedTypeName)
                .orElseThrow(() -> new AssertException(format("Directive unknown argument type '%s'. This should have been validated before.", allowedTypeName)));

        if (allowedTypeDefinition instanceof ScalarTypeDefinition) {
            checkArgValueMatchesAllowedScalar(errors, instanceValue, (ScalarTypeDefinition) allowedTypeDefinition);
        } else if (allowedTypeDefinition instanceof EnumTypeDefinition) {
            checkArgValueMatchesAllowedEnum(errors, instanceValue, (EnumTypeDefinition) allowedTypeDefinition);
        } else if (allowedTypeDefinition instanceof InputObjectTypeDefinition) {
            checkArgValueMatchesAllowedInputType(errors, instanceValue, (InputObjectTypeDefinition) allowedTypeDefinition);
        } else {
            assertShouldNeverHappen("'%s' must be an input type. It is %s instead. ", allowedTypeName, allowedTypeDefinition.getClass());
        }
    }

    private void checkArgValueMatchesAllowedInputType(List<GraphQLError> errors, Value<?> instanceValue, InputObjectTypeDefinition allowedTypeDefinition) {
        if (!(instanceValue instanceof ObjectValue)) {
            addValidationError(errors, EXPECTED_OBJECT_MESSAGE, instanceValue.getClass().getSimpleName());
            return;
        }

        ObjectValue objectValue = ((ObjectValue) instanceValue);
        // duck typing validation, if it looks like the definition
        // then it must be the same type as the definition

        List<ObjectField> fields = objectValue.getObjectFields();
        List<InputObjectTypeExtensionDefinition> inputObjExt = typeRegistry.inputObjectTypeExtensions().getOrDefault(allowedTypeDefinition.getName(), emptyList());
        Stream<InputValueDefinition> inputObjExtValues = inputObjExt.stream().flatMap(inputObj -> inputObj.getInputValueDefinitions().stream());
        List<InputValueDefinition> inputValueDefinitions = Stream.concat(allowedTypeDefinition.getInputValueDefinitions().stream(), inputObjExtValues).collect(toList());

        // check for duplicated fields
        Map<String, Long> fieldsToOccurrenceMap = fields.stream().map(ObjectField::getName)
                .collect(groupingBy(Function.identity(), counting()));

        if (fieldsToOccurrenceMap.values().stream().anyMatch(count -> count > 1)) {
            addValidationError(errors, DUPLICATED_KEYS_MESSAGE, fieldsToOccurrenceMap.entrySet().stream()
                    .filter(entry -> entry.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(joining(",")));
            return;
        }

        // check for unknown fields
        Map<String, InputValueDefinition> nameToInputValueDefMap = inputValueDefinitions.stream()
                .collect(toMap(InputValueDefinition::getName, inputValueDef -> inputValueDef));

        List<ObjectField> unknownFields = fields.stream()
                .filter(field -> !nameToInputValueDefMap.containsKey(field.getName()))
                .collect(toList());

        if (!unknownFields.isEmpty()) {
            addValidationError(errors, UNKNOWN_FIELDS_MESSAGE,
                    unknownFields.stream()
                            .map(ObjectField::getName)
                            .collect(joining(",")),
                    allowedTypeDefinition.getName());
            return;
        }

        // fields to map for easy access
        Map<String, ObjectField> nameToFieldsMap = fields.stream()
                .collect(toMap(ObjectField::getName, objectField -> objectField));
        // check each single field with its definition
        inputValueDefinitions.forEach(allowedValueDef -> {
            ObjectField objectField = nameToFieldsMap.get(allowedValueDef.getName());
            checkArgInputObjectValueFieldMatchesAllowedDefinition(errors, objectField, allowedValueDef);
        });
    }

    private void checkArgValueMatchesAllowedEnum(List<GraphQLError> errors, Value<?> instanceValue, EnumTypeDefinition allowedTypeDefinition) {
        if (!(instanceValue instanceof EnumValue)) {
            addValidationError(errors, EXPECTED_ENUM_MESSAGE, instanceValue.getClass().getSimpleName());
            return;
        }

        EnumValue enumValue = ((EnumValue) instanceValue);

        List<EnumTypeExtensionDefinition> enumExtensions = typeRegistry.enumTypeExtensions().getOrDefault(allowedTypeDefinition.getName(), emptyList());
        Stream<EnumValueDefinition> enumExtStream = enumExtensions.stream().flatMap(enumExt -> enumExt.getEnumValueDefinitions().stream());
        List<EnumValueDefinition> enumValueDefinitions = Stream.concat(allowedTypeDefinition.getEnumValueDefinitions().stream(), enumExtStream).collect(toList());

        boolean noneMatchAllowedEnumValue = enumValueDefinitions.stream()
                .noneMatch(enumAllowedValue -> enumAllowedValue.getName().equals(enumValue.getName()));

        if (noneMatchAllowedEnumValue) {
            addValidationError(errors, MUST_BE_VALID_ENUM_VALUE_MESSAGE, enumValue.getName(), enumValueDefinitions.stream()
                    .map(EnumValueDefinition::getName)
                    .collect(joining(",")));
        }
    }

    private void checkArgValueMatchesAllowedScalar(List<GraphQLError> errors, Value<?> instanceValue, ScalarTypeDefinition allowedTypeDefinition) {
        // scalars are allowed to accept ANY literal value - its up to their coercion to decide if its valid or not
        List<ScalarTypeExtensionDefinition> extensions = typeRegistry.scalarTypeExtensions().getOrDefault(allowedTypeDefinition.getName(), emptyList());
        ScalarWiringEnvironment environment = new ScalarWiringEnvironment(typeRegistry, allowedTypeDefinition, extensions);
        WiringFactory wiringFactory = runtimeWiring.getWiringFactory();

        GraphQLScalarType scalarType;
        if (wiringFactory.providesScalar(environment)) {
            scalarType = wiringFactory.getScalar(environment);
        } else {
            scalarType = runtimeWiring.getScalars().get(allowedTypeDefinition.getName());
        }
        // scalarType will always be present as
        // scalar implementation validation has been performed earlier
        if (!isArgumentValueScalarLiteral(scalarType, instanceValue)) {
            addValidationError(errors, NOT_A_VALID_SCALAR_LITERAL_MESSAGE, allowedTypeDefinition.getName());
        }
    }

    private void checkArgInputObjectValueFieldMatchesAllowedDefinition(List<GraphQLError> errors, ObjectField objectField, InputValueDefinition allowedValueDef) {

        if (objectField != null) {
            checkArgValueMatchesAllowedType(errors, objectField.getValue(), allowedValueDef.getType());
            return;
        }

        // check if field definition is required and has no default value
        if (allowedValueDef.getType() instanceof NonNullType && allowedValueDef.getDefaultValue() == null) {
            addValidationError(errors, MISSING_REQUIRED_FIELD_MESSAGE, allowedValueDef.getName());
        }

        // other cases are
        // - field definition is marked as non-null but has a default value, so the default value can be used
        // - field definition is nullable hence null can be used
    }

    private void checkArgValueMatchesAllowedNonNullType(List<GraphQLError> errors, Value<?> instanceValue, NonNullType allowedArgType) {
        if (instanceValue instanceof NullValue) {
            addValidationError(errors, EXPECTED_NON_NULL_MESSAGE);
            return;
        }

        Type<?> unwrappedAllowedType = allowedArgType.getType();
        checkArgValueMatchesAllowedType(errors, instanceValue, unwrappedAllowedType);
    }

    private void checkArgValueMatchesAllowedListType(List<GraphQLError> errors, Value<?> instanceValue, ListType allowedArgType) {
        if (instanceValue instanceof NullValue) {
            return;
        }

        Type<?> unwrappedAllowedType = allowedArgType.getType();
        if (!(instanceValue instanceof ArrayValue)) {
            checkArgValueMatchesAllowedType(errors, instanceValue, unwrappedAllowedType);
            return;
        }

        ArrayValue arrayValue = ((ArrayValue) instanceValue);
        boolean isUnwrappedList = unwrappedAllowedType instanceof ListType;

        // validate each instance value in the list, all instances must match for the list to match
        arrayValue.getValues().forEach(value -> {
            // restrictive check for sub-arrays
            if (isUnwrappedList && !(value instanceof ArrayValue)) {
                addValidationError(errors, EXPECTED_LIST_MESSAGE, value.getClass().getSimpleName());
            }
            checkArgValueMatchesAllowedType(errors, value, unwrappedAllowedType);
        });
    }

    private boolean isArgumentValueScalarLiteral(GraphQLScalarType scalarType, Value<?> instanceValue) {
        try {
            scalarType.getCoercing().parseLiteral(instanceValue, CoercedVariables.emptyVariables(), GraphQLContext.getDefault(), Locale.getDefault());
            return true;
        } catch (CoercingParseLiteralException ex) {
            return false;
        }
    }
}
