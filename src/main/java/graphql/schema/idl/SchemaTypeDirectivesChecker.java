package graphql.schema.idl;

import graphql.AssertException;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.introspection.Introspection.DirectiveLocation;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.Directive;
import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError;
import graphql.schema.idl.errors.DirectiveIllegalLocationError;
import graphql.schema.idl.errors.DirectiveMissingNonNullArgumentError;
import graphql.schema.idl.errors.DirectiveUndeclaredError;
import graphql.schema.idl.errors.DirectiveUnknownArgumentError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static graphql.Assert.assertShouldNeverHappen;
import static graphql.introspection.Introspection.DirectiveLocation.ARGUMENT_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM;
import static graphql.introspection.Introspection.DirectiveLocation.ENUM_VALUE;
import static graphql.introspection.Introspection.DirectiveLocation.FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION;
import static graphql.introspection.Introspection.DirectiveLocation.INPUT_OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.INTERFACE;
import static graphql.introspection.Introspection.DirectiveLocation.OBJECT;
import static graphql.introspection.Introspection.DirectiveLocation.SCALAR;
import static graphql.introspection.Introspection.DirectiveLocation.UNION;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.DUPLICATED_KEYS_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_ENUM_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_LIST_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_NON_NULL_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_OBJECT_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.EXPECTED_SCALAR_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.MISSING_REQUIRED_FIELD_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.MUST_BE_VALID_ENUM_VALUE_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.NOT_A_VALID_SCALAR_LITERAL_MESSAGE;
import static graphql.schema.idl.errors.DirectiveIllegalArgumentTypeError.UNKNOWN_FIELDS_MESSAGE;
import static graphql.util.FpKit.getByName;
import static graphql.util.FpKit.mergeFirst;

/**
 * This is responsible for traversing EVERY type and field in the registry and ensuring that
 * any directives used follow the directive definition rules, for example
 * field directives can be used on object types
 */
@Internal
class SchemaTypeDirectivesChecker {

    private static final Logger log = LoggerFactory.getLogger(SchemaTypeDirectivesChecker.class);

    private final TypeDefinitionRegistry typeRegistry;
    private final RuntimeWiring runtimeWiring;

    public SchemaTypeDirectivesChecker(final TypeDefinitionRegistry typeRegistry,
                                       final RuntimeWiring runtimeWiring) {
        this.typeRegistry = typeRegistry;
        this.runtimeWiring = runtimeWiring;
    }

    void checkTypeDirectives(List<GraphQLError> errors) {
        typeRegistry.objectTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(OBJECT, errors, ext)));
        typeRegistry.interfaceTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(INTERFACE, errors, ext)));
        typeRegistry.unionTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(UNION, errors, ext)));
        typeRegistry.enumTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(ENUM, errors, ext)));
        typeRegistry.scalarTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(SCALAR, errors, ext)));
        typeRegistry.inputObjectTypeExtensions().values()
                .forEach(extDefinitions -> extDefinitions.forEach(ext -> checkDirectives(INPUT_OBJECT, errors, ext)));

        typeRegistry.getTypes(ObjectTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(OBJECT, errors, typeDef));
        typeRegistry.getTypes(InterfaceTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(INTERFACE, errors, typeDef));
        typeRegistry.getTypes(UnionTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(UNION, errors, typeDef));
        typeRegistry.getTypes(EnumTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(ENUM, errors, typeDef));
        typeRegistry.getTypes(InputObjectTypeDefinition.class)
                .forEach(typeDef -> checkDirectives(INPUT_OBJECT, errors, typeDef));

        typeRegistry.scalars().values()
                .forEach(typeDef -> checkDirectives(SCALAR, errors, typeDef));

    }


    private void checkDirectives(DirectiveLocation expectedLocation, List<GraphQLError> errors, TypeDefinition<?> typeDef) {
        checkDirectives(expectedLocation, errors, typeRegistry, typeDef, typeDef.getName(), typeDef.getDirectives());

        if (typeDef instanceof ObjectTypeDefinition) {
            List<FieldDefinition> fieldDefinitions = ((ObjectTypeDefinition) typeDef).getFieldDefinitions();
            checkFieldsDirectives(errors, typeRegistry, fieldDefinitions);
        }
        if (typeDef instanceof InterfaceTypeDefinition) {
            List<FieldDefinition> fieldDefinitions = ((InterfaceTypeDefinition) typeDef).getFieldDefinitions();
            checkFieldsDirectives(errors, typeRegistry, fieldDefinitions);
        }
        if (typeDef instanceof EnumTypeDefinition) {
            List<EnumValueDefinition> enumValueDefinitions = ((EnumTypeDefinition) typeDef).getEnumValueDefinitions();
            enumValueDefinitions.forEach(definition -> checkDirectives(ENUM_VALUE, errors, typeRegistry, definition, definition.getName(), definition.getDirectives()));
        }
        if (typeDef instanceof InputObjectTypeDefinition) {
            List<InputValueDefinition> inputValueDefinitions = ((InputObjectTypeDefinition) typeDef).getInputValueDefinitions();
            inputValueDefinitions.forEach(definition -> checkDirectives(INPUT_FIELD_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives()));
        }
    }

    private void checkFieldsDirectives(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, List<FieldDefinition> fieldDefinitions) {
        fieldDefinitions.forEach(definition -> {
            checkDirectives(FIELD_DEFINITION, errors, typeRegistry, definition, definition.getName(), definition.getDirectives());
            //
            // and check its arguments
            definition.getInputValueDefinitions().forEach(arg -> checkDirectives(ARGUMENT_DEFINITION, errors, typeRegistry, arg, arg.getName(), arg.getDirectives()));
        });
    }

    private void checkDirectives(DirectiveLocation expectedLocation, List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Node<?> element, String elementName, List<Directive> directives) {
        directives.forEach(directive -> {
            Optional<DirectiveDefinition> directiveDefinition = typeRegistry.getDirectiveDefinition(directive.getName());
            if (!directiveDefinition.isPresent()) {
                errors.add(new DirectiveUndeclaredError(element, elementName, directive.getName()));
            } else {
                if (!inRightLocation(expectedLocation, directiveDefinition.get())) {
                    errors.add(new DirectiveIllegalLocationError(element, elementName, directive.getName(), expectedLocation.name()));
                }
                checkDirectiveArguments(errors, typeRegistry, element, elementName, directive, directiveDefinition.get());
            }
        });
    }

    private boolean inRightLocation(DirectiveLocation expectedLocation, DirectiveDefinition directiveDefinition) {
        List<String> names = directiveDefinition.getDirectiveLocations()
                .stream().map(graphql.language.DirectiveLocation::getName)
                .map(String::toUpperCase)
                .collect(Collectors.toList());

        return names.contains(expectedLocation.name().toUpperCase());
    }

    private void checkDirectiveArguments(List<GraphQLError> errors, TypeDefinitionRegistry typeRegistry, Node element, String elementName, Directive directive, DirectiveDefinition directiveDefinition) {
        Map<String, InputValueDefinition> allowedArgs = getByName(directiveDefinition.getInputValueDefinitions(), (InputValueDefinition::getName), mergeFirst());
        Map<String, Argument> providedArgs = getByName(directive.getArguments(), (Argument::getName), mergeFirst());
        directive.getArguments().forEach(argument -> {
            InputValueDefinition allowedArg = allowedArgs.get(argument.getName());
            if (allowedArg == null) {
                errors.add(new DirectiveUnknownArgumentError(element, elementName, directive.getName(), argument.getName()));
            } else {
                ArgValueOfAllowedTypeChecker argValueOfAllowedTypeChecker = new ArgValueOfAllowedTypeChecker(directive, element, elementName, argument);
                argValueOfAllowedTypeChecker.checkArgValueMatchesAllowedType(errors, argument.getValue(), allowedArg.getType());
            }
        });
        allowedArgs.forEach((argName, definitionArgument) -> {
            if (isNoNullArgWithoutDefaultValue(definitionArgument)) {
                if (!providedArgs.containsKey(argName)) {
                    errors.add(new DirectiveMissingNonNullArgumentError(element, elementName, directive.getName(), argName));
                }
            }
        });
    }

    private boolean isNoNullArgWithoutDefaultValue(InputValueDefinition definitionArgument) {
        return definitionArgument.getType() instanceof NonNullType && definitionArgument.getDefaultValue() == null;
    }


    /**
     * Class to check whether a given directive argument value
     * matches a given directive definition.
     *
     */
    private class ArgValueOfAllowedTypeChecker {

        private final Directive directive;
        private final Node element;
        private final String elementName;
        private final Argument argument;

        ArgValueOfAllowedTypeChecker(Directive directive,
                                     Node element,
                                     String elementName,
                                     Argument argument) {
            this.directive = directive;
            this.element = element;
            this.elementName = elementName;
            this.argument = argument;
        }

        /**
         * Recursively inspects an argument value given an allowed type.
         * Given the (invalid) SDL below:
         *
         *      directive @myDirective(arg: [[String]] ) on FIELD_DEFINITION
         *
         *      query {
         *          f: String @myDirective(arg: ["A String"])
         *      }
         *
         * it will first check that the `myDirective.arg` type is an array
         * and fail when finding "A String" as it expected a nested array ([[String]]).
         * @param errors validation error collector
         * @param instanceValue directive argument value
         * @param allowedArgType directive definition argument allowed type
         */
        void checkArgValueMatchesAllowedType(List<GraphQLError> errors, Value instanceValue, Type allowedArgType) {
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
            errors.add(new DirectiveIllegalArgumentTypeError(element, elementName, directive.getName(), argument.getName(), String.format(message, args)));
        }

        private void checkArgValueMatchesAllowedTypeName(List<GraphQLError> errors, Value instanceValue, Type allowedArgType) {
            if (instanceValue instanceof NullValue) {
                return;
            }

            String allowedTypeName = ((TypeName) allowedArgType).getName();
            TypeDefinition allowedTypeDefinition = typeRegistry.getType(allowedTypeName)
                    .orElseThrow(() -> new AssertException("Directive unknown argument type '%s'. This should have been validated before."));

            if (allowedTypeDefinition instanceof ScalarTypeDefinition) {
                checkArgValueMatchesAllowedScalar(errors, instanceValue, allowedTypeName);
            } else if (allowedTypeDefinition instanceof EnumTypeDefinition) {
                checkArgValueMatchesAllowedEnum(errors, instanceValue, (EnumTypeDefinition) allowedTypeDefinition);
            } else if (allowedTypeDefinition instanceof InputObjectTypeDefinition) {
                checkArgValueMatchesAllowedInputType(errors, instanceValue, (InputObjectTypeDefinition) allowedTypeDefinition);
            } else {
                assertShouldNeverHappen("'%s' must be an input type. It is %s instead. ", allowedTypeName, allowedTypeDefinition.getClass());
            }
        }

        private void checkArgValueMatchesAllowedInputType(List<GraphQLError> errors, Value instanceValue, InputObjectTypeDefinition allowedTypeDefinition) {
            if (!(instanceValue instanceof ObjectValue)) {
                addValidationError(errors, EXPECTED_OBJECT_MESSAGE, instanceValue.getClass().getSimpleName());
                return;
            }

            ObjectValue objectValue = ((ObjectValue) instanceValue);
            // duck typing validation, if it looks like the definition
            // then it must be the same type as the definition

            List<ObjectField> fields = objectValue.getObjectFields();
            List<InputValueDefinition> inputValueDefinitions = allowedTypeDefinition.getInputValueDefinitions();

            // check for duplicated fields
            Map<String, Long> fieldsToOccurrenceMap = fields.stream().map(ObjectField::getName)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            if (fieldsToOccurrenceMap.values().stream().anyMatch(count -> count > 1)) {
                addValidationError(errors, DUPLICATED_KEYS_MESSAGE, fieldsToOccurrenceMap.entrySet().stream()
                        .filter(entry -> entry.getValue() > 1)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.joining(",")));
                return;
            }

            // check for unknown fields
            Map<String, InputValueDefinition> nameToInputValueDefMap = inputValueDefinitions.stream()
                    .collect(Collectors.toMap(InputValueDefinition::getName, inputValueDef -> inputValueDef));

            List<ObjectField> unknownFields = fields.stream()
                    .filter(field -> !nameToInputValueDefMap.containsKey(field.getName()))
                    .collect(Collectors.toList());

            if (!unknownFields.isEmpty()) {
                addValidationError(errors, UNKNOWN_FIELDS_MESSAGE,
                        unknownFields.stream()
                                .map(ObjectField::getName)
                                .collect(Collectors.joining(",")),
                        allowedTypeDefinition.getName());
                return;
            }

            // fields to map for easy access
            Map<String, ObjectField> nameToFieldsMap = fields.stream()
                    .collect(Collectors.toMap(ObjectField::getName, objectField -> objectField));
            // check each single field with its definition
            inputValueDefinitions.forEach(allowedValueDef -> {
                ObjectField objectField = nameToFieldsMap.get(allowedValueDef.getName());
                checkArgInputObjectValueFieldMatchesAllowedDefinition(errors, objectField, allowedValueDef);
            });
        }

        private void checkArgValueMatchesAllowedEnum(List<GraphQLError> errors, Value instanceValue, EnumTypeDefinition allowedTypeDefinition) {
            if (!(instanceValue instanceof EnumValue)) {
                addValidationError(errors, EXPECTED_ENUM_MESSAGE, instanceValue.getClass().getSimpleName());
                return;
            }

            EnumValue enumValue = ((EnumValue) instanceValue);

            boolean noneMatchAllowedEnumValue = allowedTypeDefinition.getEnumValueDefinitions().stream()
                    .noneMatch(enumAllowedValue -> enumAllowedValue.getName().equals(enumValue.getName()));

            if (noneMatchAllowedEnumValue) {
                addValidationError(errors, MUST_BE_VALID_ENUM_VALUE_MESSAGE, enumValue.getName(), allowedTypeDefinition.getEnumValueDefinitions().stream()
                                .map(EnumValueDefinition::getName)
                                .collect(Collectors.joining(",")));
            }
        }

        private void checkArgValueMatchesAllowedScalar(List<GraphQLError> errors, Value instanceValue, String allowedTypeName) {
            if (instanceValue instanceof ArrayValue
                    || instanceValue instanceof EnumValue
                    || instanceValue instanceof ObjectValue) {
                addValidationError(errors, EXPECTED_SCALAR_MESSAGE, instanceValue.getClass().getSimpleName());
                return;
            }

            GraphQLScalarType scalarType = runtimeWiring.getScalars().get(allowedTypeName);
            // scalarType will always be present as
            // scalar implementation validation has been performed earlier
            if (!isArgumentValueScalarLiteral(scalarType, instanceValue)) {
                addValidationError(errors, NOT_A_VALID_SCALAR_LITERAL_MESSAGE, allowedTypeName);
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

        private void checkArgValueMatchesAllowedNonNullType(List<GraphQLError> errors, Value instanceValue, NonNullType allowedArgType) {
            if (instanceValue instanceof NullValue) {
                addValidationError(errors, EXPECTED_NON_NULL_MESSAGE);
                return;
            }

            Type unwrappedAllowedType = allowedArgType.getType();
            checkArgValueMatchesAllowedType(errors, instanceValue, unwrappedAllowedType);
        }

        private void checkArgValueMatchesAllowedListType(List<GraphQLError> errors, Value instanceValue, ListType allowedArgType) {
            if (instanceValue instanceof NullValue) {
                return;
            }

            if (!(instanceValue instanceof ArrayValue)) {
                addValidationError(errors, EXPECTED_LIST_MESSAGE, instanceValue.getClass().getSimpleName());
                return;
            }

            ArrayValue arrayValue = ((ArrayValue) instanceValue);
            Type unwrappedAllowedType = allowedArgType.getType();

            // validate each instance value in the list, all instances must match for the list to match
            arrayValue.getValues().forEach(value -> checkArgValueMatchesAllowedType(errors, value, unwrappedAllowedType));
        }

        private boolean isArgumentValueScalarLiteral(GraphQLScalarType scalarType, Value instanceValue) {
            try {
                scalarType.getCoercing().parseLiteral(instanceValue);
                return true;
            } catch (CoercingParseLiteralException ex) {
                log.debug("Attempted parsing literal into '{}' but got the following error: ", scalarType.getName(), ex);
                return false;
            }
        }
    }
}
