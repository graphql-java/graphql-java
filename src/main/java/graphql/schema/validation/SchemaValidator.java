package graphql.schema.validation;

import graphql.Directives;
import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.InputValueLimits;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ValuesResolver;
import graphql.language.ArrayValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.Value;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.InputValueWithState;
import graphql.validation.ValidationUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static graphql.introspection.Introspection.INTROSPECTION_SYSTEM_FIELDS;
import static graphql.introspection.Introspection.isIntrospectionTypes;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.validation.SchemaValidationErrorType.ObjectDoesNotImplementItsInterfaces;
import static java.lang.String.format;

@Internal
@NullMarked
public class SchemaValidator {

    private static final String DEPRECATED_DIRECTIVE_NAME = Directives.DEPRECATED_DIRECTIVE_DEFINITION.getName();

    private final ValidationUtil validationUtil = new ValidationUtil();

    public Set<SchemaValidationError> validateSchema(GraphQLSchema schema) {
        GraphQLContext context = GraphQLContext.getDefault();
        SchemaValidationErrorCollector errors = new SchemaValidationErrorCollector();
        new DefaultValueDepth(InputValueLimits.getDefaultLimits().getMaxDepth()).check(schema, errors);
        if (!errors.getErrors().isEmpty()) {
            return errors.getErrors();
        }
        // unbroken input cycle results only depend on the argument's input type, so each type is explored once
        Set<GraphQLInputObjectType> checkedCycleRoots = newIdentitySet();

        List<GraphQLNamedType> types = schema.getAllTypesAsList();
        checkDefaultValueCycles(types, errors);
        for (GraphQLNamedType type : types) {
            if (type instanceof GraphQLObjectType) {
                validateObjectType((GraphQLObjectType) type, checkedCycleRoots, schema, context, errors);
            } else if (type instanceof GraphQLInterfaceType) {
                validateInterfaceType((GraphQLInterfaceType) type, checkedCycleRoots, schema, context, errors);
            } else if (type instanceof GraphQLInputObjectType) {
                validateInputObjectType((GraphQLInputObjectType) type, schema, context, errors);
            } else if (type instanceof GraphQLEnumType) {
                validateEnumType((GraphQLEnumType) type, schema, context, errors);
            } else if (type instanceof GraphQLUnionType) {
                validateUnionType((GraphQLUnionType) type, schema, context, errors);
            } else if (type instanceof GraphQLScalarType) {
                checkDirectiveContainer((GraphQLScalarType) type, schema, context, errors);
            }
        }
        for (GraphQLDirective directive : schema.getDirectives()) {
            validateDirectiveDefinition(directive, schema, context, errors);
        }
        return errors.getErrors();
    }

    private <T> Set<T> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void validateObjectType(GraphQLObjectType type, Set<GraphQLInputObjectType> checkedCycleRoots, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        List<GraphQLFieldDefinition> fieldDefinitions = type.getFieldDefinitions();
        checkImplementsInterfaces(type, errors);
        if (!isIntrospectionTypes(type)) {
            checkFieldsContainer(type, fieldDefinitions, errors);
        }
        checkDirectiveContainer(type, schema, context, errors);
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            validateFieldDefinition(type, fieldDefinition, checkedCycleRoots, schema, context, errors);
        }
    }

    private void validateInterfaceType(GraphQLInterfaceType type, Set<GraphQLInputObjectType> checkedCycleRoots, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        List<GraphQLFieldDefinition> fieldDefinitions = type.getFieldDefinitions();
        checkImplementsInterfaces(type, errors);
        checkFieldsContainer(type, fieldDefinitions, errors);
        checkDirectiveContainer(type, schema, context, errors);
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            validateFieldDefinition(type, fieldDefinition, checkedCycleRoots, schema, context, errors);
        }
    }

    private void validateUnionType(GraphQLUnionType type, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        if (!isIntrospectionTypes(type)) {
            checkUnion(type, errors);
        }
        checkDirectiveContainer(type, schema, context, errors);
    }

    private void validateEnumType(GraphQLEnumType type, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        List<GraphQLEnumValueDefinition> values = type.getValues();
        if (!isIntrospectionTypes(type)) {
            checkEnum(type, values, errors);
        }
        checkDirectiveContainer(type, schema, context, errors);
        for (GraphQLEnumValueDefinition value : values) {
            checkDirectiveContainer(value, schema, context, errors);
        }
    }

    private void validateInputObjectType(GraphQLInputObjectType type, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        List<GraphQLInputObjectField> fields = type.getFieldDefinitions();
        if (!isIntrospectionTypes(type)) {
            checkInputObject(type, fields, errors);
        }
        checkDirectiveContainer(type, schema, context, errors);
        checkOneOfInhabited(type, errors);
        for (GraphQLInputObjectField field : fields) {
            validateInputObjectField(type, field, schema, context, errors);
        }
    }

    private void validateFieldDefinition(GraphQLFieldsContainer container, GraphQLFieldDefinition fieldDefinition, Set<GraphQLInputObjectType> checkedCycleRoots, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        checkNoUnbrokenInputCycles(fieldDefinition, checkedCycleRoots, errors);
        checkDirectiveContainer(fieldDefinition, schema, context, errors);
        checkFieldTypesUsedAppropriately(container, fieldDefinition, errors);
        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            validateArgument(argument, fieldDefinition, schema, context, errors);
        }
    }

    private void validateInputObjectField(GraphQLInputObjectType type, GraphQLInputObjectField field, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        if (field.hasSetDefaultValue()) {
            checkDefaultValue(field.getInputFieldDefaultValue(), field.getType(), schema, context, errors);
        }
        checkDirectiveContainer(field, schema, context, errors);
        checkInputFieldTypeUsedAppropriately(type, field, errors);
        checkOneOfField(type, field, errors);
        checkDeprecatedInputField(type, field, errors);
    }

    private void validateArgument(GraphQLArgument argument, GraphQLSchemaElement fieldOrDirective, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        if (argument.hasSetDefaultValue()) {
            checkDefaultValue(argument.getArgumentDefaultValue(), argument.getType(), schema, context, errors);
        }
        checkDirectiveContainer(argument, schema, context, errors);
        checkDeprecatedArgument(argument, fieldOrDirective, errors);
    }

    private void validateDirectiveDefinition(GraphQLDirective directive, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        checkDirectiveContainer(directive, schema, context, errors);
        for (GraphQLArgument argument : directive.getArguments()) {
            validateArgument(argument, directive, schema, context, errors);
        }
    }

    private void checkDirectiveContainer(GraphQLDirectiveContainer container, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        checkAppliedDirectives(container, schema, errors);
        for (GraphQLDirective directive : container.getDirectives()) {
            for (GraphQLArgument argument : directive.getArguments()) {
                checkAppliedDirectiveArgument(directive.getName(), argument.getName(), argument.getArgumentValue(), argument.getType(), schema, context, errors);
            }
        }
        for (GraphQLAppliedDirective directive : container.getAppliedDirectives()) {
            for (GraphQLAppliedDirectiveArgument argument : directive.getArguments()) {
                checkAppliedDirectiveArgument(directive.getName(), argument.getName(), argument.getArgumentValue(), argument.getType(), schema, context, errors);
            }
        }
    }

    private void addError(SchemaValidationErrorCollector errors, SchemaValidationErrorType errorType, String message) {
        errors.addError(new SchemaValidationError(errorType, message));
    }

    private void checkNoUnbrokenInputCycles(GraphQLFieldDefinition fieldDefinition, Set<GraphQLInputObjectType> checkedCycleRoots, SchemaValidationErrorCollector errors) {
        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            GraphQLInputType argumentType = argument.getType();
            if (argumentType instanceof GraphQLInputObjectType && checkedCycleRoots.add((GraphQLInputObjectType) argumentType)) {
                checkUnbrokenInputCycle((GraphQLInputObjectType) argumentType, newIdentitySet(), new ArrayList<>(), errors);
            }
        }
    }

    // path entries deliberately accumulate across sibling fields, which existing error messages rely on
    private void checkUnbrokenInputCycle(GraphQLInputObjectType type, Set<GraphQLInputObjectType> pathTypes, List<String> path, SchemaValidationErrorCollector errors) {
        if (!pathTypes.add(type)) {
            addError(errors, SchemaValidationErrorType.UnbrokenInputCycle, "[" + String.join(".", path) + "] forms an unsatisfiable cycle");
            return;
        }
        int pathSizeOnEntry = path.size();
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            GraphQLInputObjectType next = nonNullInputObjectType(field.getType());
            if (next != null) {
                path.add(field.getName() + "!");
                checkUnbrokenInputCycle(next, pathTypes, path, errors);
            }
        }
        path.subList(pathSizeOnEntry, path.size()).clear();
        pathTypes.remove(type);
    }

    private @Nullable GraphQLInputObjectType nonNullInputObjectType(GraphQLInputType type) {
        if (!(type instanceof GraphQLNonNull)) {
            return null;
        }
        GraphQLType wrappedType = ((GraphQLNonNull) type).getWrappedType();
        if (isList(wrappedType)) {
            return null;
        }
        GraphQLType unwrapped = unwrapAll(wrappedType);
        return unwrapped instanceof GraphQLInputObjectType ? (GraphQLInputObjectType) unwrapped : null;
    }

    private void checkDefaultValueCycles(List<GraphQLNamedType> types, SchemaValidationErrorCollector errors) {
        // coordinates already explored, so each cycle is reported only once
        Set<String> exploredDefaults = new LinkedHashSet<>();
        for (GraphQLNamedType type : types) {
            if (type instanceof GraphQLInputObjectType) {
                checkNoDefaultValueCircularRefs((GraphQLInputObjectType) type, exploredDefaults, errors);
            }
        }
    }

    private void checkNoDefaultValueCircularRefs(GraphQLInputObjectType type, Set<String> exploredDefaults, SchemaValidationErrorCollector errors) {
        @Nullable LinkedHashSet<String> visitedFields = null;
        // equivalent to InputObjectDefaultValueHasCycle(type, {}) without building the empty map
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            if (field.getInputFieldDefaultValue().isNotSet()) {
                continue;
            }
            GraphQLType namedFieldType = unwrapAll(field.getType());
            if (namedFieldType instanceof GraphQLInputObjectType) {
                visitedFields = visitedFields == null ? new LinkedHashSet<>() : visitedFields;
                inputFieldDefaultValueHasCycle(field, (GraphQLInputObjectType) namedFieldType, type.getName(), exploredDefaults, visitedFields, errors);
            }
        }
    }

    private void literalDefaultValueHasCycle(GraphQLInputObjectType inputObject, @Nullable Value<?> defaultValue, Set<String> exploredDefaults, LinkedHashSet<String> visitedFields, SchemaValidationErrorCollector errors) {
        if (defaultValue instanceof ArrayValue) {
            for (Value<?> itemValue : ((ArrayValue) defaultValue).getValues()) {
                literalDefaultValueHasCycle(inputObject, itemValue, exploredDefaults, visitedFields, errors);
            }
            return;
        }
        if (!(defaultValue instanceof ObjectValue)) {
            return;
        }
        Map<String, Value<?>> defaultValueMap = new LinkedHashMap<>();
        for (ObjectField field : ((ObjectValue) defaultValue).getObjectFields()) {
            defaultValueMap.put(field.getName(), field.getValue());
        }
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            boolean hasDefaultValue = defaultValueMap.containsKey(field.getName());
            GraphQLInputObjectType fieldInputObject = inputObjectTypeWithDefault(field, hasDefaultValue);
            if (fieldInputObject == null) {
                continue;
            }
            if (hasDefaultValue) {
                literalDefaultValueHasCycle(fieldInputObject, defaultValueMap.get(field.getName()), exploredDefaults, visitedFields, errors);
            } else {
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObject.getName(), exploredDefaults, visitedFields, errors);
            }
        }
    }

    private void externalDefaultValueHasCycle(GraphQLInputObjectType inputObject, Object defaultValue, Set<String> exploredDefaults, LinkedHashSet<String> visitedFields, SchemaValidationErrorCollector errors) {
        if (defaultValue instanceof Iterable) {
            externalListDefaultValueHasCycle(inputObject, (Iterable<?>) defaultValue, exploredDefaults, visitedFields, errors);
            return;
        }
        if (!(defaultValue instanceof Map)) {
            return;
        }
        Map<?, ?> defaultValueMap = (Map<?, ?>) defaultValue;
        for (GraphQLInputObjectField field : inputObject.getFieldDefinitions()) {
            boolean hasDefaultValue = defaultValueMap.containsKey(field.getName());
            GraphQLInputObjectType fieldInputObject = inputObjectTypeWithDefault(field, hasDefaultValue);
            if (fieldInputObject == null) {
                continue;
            }
            if (!hasDefaultValue) {
                inputFieldDefaultValueHasCycle(field, fieldInputObject, inputObject.getName(), exploredDefaults, visitedFields, errors);
                continue;
            }
            Object fieldDefaultValue = defaultValueMap.get(field.getName());
            if (fieldDefaultValue != null) {
                externalDefaultValueHasCycle(fieldInputObject, fieldDefaultValue, exploredDefaults, visitedFields, errors);
            }
        }
    }

    private void externalListDefaultValueHasCycle(GraphQLInputObjectType inputObject, Iterable<?> defaultValues, Set<String> exploredDefaults, LinkedHashSet<String> visitedFields, SchemaValidationErrorCollector errors) {
        for (Object itemValue : defaultValues) {
            if (itemValue != null) {
                externalDefaultValueHasCycle(inputObject, itemValue, exploredDefaults, visitedFields, errors);
            }
        }
    }

    private @Nullable GraphQLInputObjectType inputObjectTypeWithDefault(GraphQLInputObjectField field, boolean hasDefaultInParent) {
        if (!hasDefaultInParent && field.getInputFieldDefaultValue().isNotSet()) {
            return null;
        }
        GraphQLType namedFieldType = unwrapAll(field.getType());
        return namedFieldType instanceof GraphQLInputObjectType ? (GraphQLInputObjectType) namedFieldType : null;
    }

    private void inputFieldDefaultValueHasCycle(GraphQLInputObjectField field, GraphQLInputObjectType namedFieldType, String parentTypeName, Set<String> exploredDefaults, LinkedHashSet<String> visitedFields, SchemaValidationErrorCollector errors) {
        InputValueWithState fieldDefaultValue = field.getInputFieldDefaultValue();
        String coordinate = parentTypeName + "." + field.getName();
        if (visitedFields.contains(coordinate)) {
            addError(errors, SchemaValidationErrorType.DefaultValueCircularRef, defaultValueCycleMessage(coordinate, visitedFields));
            return;
        }
        if (!exploredDefaults.add(coordinate)) {
            return;
        }
        visitedFields.add(coordinate);
        if (fieldDefaultValue.isLiteral() && fieldDefaultValue.getValue() instanceof Value) {
            literalDefaultValueHasCycle(namedFieldType, (Value<?>) fieldDefaultValue.getValue(), exploredDefaults, visitedFields, errors);
        } else if (fieldDefaultValue.isExternal() && fieldDefaultValue.getValue() != null) {
            externalDefaultValueHasCycle(namedFieldType, fieldDefaultValue.getValue(), exploredDefaults, visitedFields, errors);
        }
        visitedFields.remove(coordinate);
    }

    private String defaultValueCycleMessage(String coordinate, LinkedHashSet<String> visitedFields) {
        List<String> intermediaries = new ArrayList<>();
        boolean found = false;
        for (String entry : visitedFields) {
            if (found) {
                intermediaries.add(entry);
            }
            if (entry.equals(coordinate)) {
                found = true;
            }
        }
        if (intermediaries.isEmpty()) {
            return "Invalid circular reference. The default value of Input Object field "
                    + coordinate + " references itself.";
        }
        return "Invalid circular reference. The default value of Input Object field "
                + coordinate + " references itself via the default values of: "
                + String.join(", ", intermediaries) + ".";
    }

    private void checkImplementsInterfaces(GraphQLImplementingType implementingType, SchemaValidationErrorCollector errors) {
        List<GraphQLNamedOutputType> interfaces = implementingType.getInterfaces();
        for (GraphQLNamedOutputType interfaceType : interfaces) {
            checkObjectImplementsInterface(implementingType, (GraphQLInterfaceType) interfaceType, errors);
        }
    }

    private void checkObjectImplementsInterface(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector errors) {
        for (GraphQLFieldDefinition interfaceFieldDef : interfaceType.getFieldDefinitions()) {
            GraphQLFieldDefinition objectFieldDef = implementingType.getFieldDefinition(interfaceFieldDef.getName());
            if (objectFieldDef == null) {
                addInterfaceError(errors, format("%s type '%s' does not implement interface '%s' because field '%s' is missing",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName()));
            } else {
                checkFieldTypeCompatibility(implementingType, interfaceType, interfaceFieldDef, objectFieldDef, errors);
            }
        }
        checkTransitiveImplementations(implementingType, interfaceType, errors);
    }

    private void checkTransitiveImplementations(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector errors) {
        List<GraphQLNamedOutputType> implementedInterfaces = implementingType.getInterfaces();
        for (GraphQLNamedOutputType transitiveInterface : interfaceType.getInterfaces()) {
            if (transitiveInterface.equals(implementingType)) {
                addInterfaceError(errors, format("%s type '%s' cannot implement '%s' because that would result on a circular reference",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName()));
            } else if (!implementedInterfaces.contains(transitiveInterface)) {
                addInterfaceError(errors, format("%s type '%s' must implement '%s' because it is implemented by '%s'",
                        typeOf(implementingType), implementingType.getName(), transitiveInterface.getName(), interfaceType.getName()));
            }
        }
    }

    private void checkFieldTypeCompatibility(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef, SchemaValidationErrorCollector errors) {
        if (isCompatible(interfaceFieldDef.getType(), objectFieldDef.getType())) {
            checkFieldArgumentEquivalence(implementingType, interfaceType, interfaceFieldDef, objectFieldDef, errors);
            return;
        }
        addInterfaceError(errors, format("%s type '%s' does not implement interface '%s' because field '%s' is defined as '%s' type and not as '%s' type",
                typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(),
                simplePrint(objectFieldDef.getType()), simplePrint(interfaceFieldDef.getType())));
    }

    private void checkFieldArgumentEquivalence(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef, SchemaValidationErrorCollector errors) {
        List<GraphQLArgument> interfaceArgs = interfaceFieldDef.getArguments();
        for (GraphQLArgument interfaceArg : interfaceArgs) {
            if (objectFieldDef.getArgument(interfaceArg.getName()) == null) {
                addInterfaceError(errors, format("%s type '%s' does not implement interface '%s' because field '%s' is missing argument(s): '%s'",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(),
                        missingArgumentNames(interfaceArgs, objectFieldDef)));
                return;
            }
        }
        for (GraphQLArgument objectArg : objectFieldDef.getArguments()) {
            GraphQLArgument interfaceArg = interfaceFieldDef.getArgument(objectArg.getName());
            if (interfaceArg == null) {
                checkAdditionalArgumentIsOptional(implementingType, interfaceType, objectFieldDef, objectArg, errors);
            } else if (!isSameType(objectArg.getType(), interfaceArg.getType())) {
                addInterfaceError(errors, format("%s type '%s' does not implement interface '%s' because field '%s' argument '%s' is defined differently",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(), objectArg.getName()));
            }
        }
    }

    private void checkAdditionalArgumentIsOptional(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, GraphQLFieldDefinition objectFieldDef, GraphQLArgument objectArg, SchemaValidationErrorCollector errors) {
        if (objectArg.getType() instanceof GraphQLNonNull) {
            addInterfaceError(errors, format("%s type '%s' field '%s' defines an additional non-optional argument '%s' which is not allowed because field is also defined in interface '%s'",
                    typeOf(implementingType), implementingType.getName(), objectFieldDef.getName(), objectArg.getName(), interfaceType.getName()));
        }
    }

    private String missingArgumentNames(List<GraphQLArgument> interfaceArgs, GraphQLFieldDefinition objectFieldDef) {
        Set<String> missing = new LinkedHashSet<>();
        for (GraphQLArgument interfaceArg : interfaceArgs) {
            if (objectFieldDef.getArgument(interfaceArg.getName()) == null) {
                missing.add(interfaceArg.getName());
            }
        }
        return String.join(", ", missing);
    }

    private void addInterfaceError(SchemaValidationErrorCollector errors, String message) {
        addError(errors, ObjectDoesNotImplementItsInterfaces, message);
    }

    private String typeOf(GraphQLImplementingType implementingType) {
        return implementingType instanceof GraphQLObjectType ? "object" : "interface";
    }

    private boolean isCompatible(GraphQLOutputType constraintType, GraphQLOutputType objectType) {
        if (isSameType(constraintType, objectType)) {
            return true;
        } else if (constraintType instanceof GraphQLUnionType) {
            return ((GraphQLUnionType) constraintType).getTypes().contains(objectType);
        } else if (constraintType instanceof GraphQLInterfaceType && objectType instanceof GraphQLObjectType) {
            return ((GraphQLObjectType) objectType).getInterfaces().contains(constraintType);
        } else if (constraintType instanceof GraphQLInterfaceType && objectType instanceof GraphQLInterfaceType) {
            return ((GraphQLInterfaceType) objectType).getInterfaces().contains(constraintType);
        } else if (isList(constraintType) && isList(objectType)) {
            return isCompatible((GraphQLOutputType) unwrapOne(constraintType), (GraphQLOutputType) unwrapOne(objectType));
        } else if (isNonNull(objectType)) {
            GraphQLOutputType nullableConstraint = isNonNull(constraintType) ? (GraphQLOutputType) unwrapOne(constraintType) : constraintType;
            return isCompatible(nullableConstraint, (GraphQLOutputType) unwrapOne(objectType));
        }
        return false;
    }

    // equivalent to comparing GraphQLTypeUtil.simplePrint() of both types
    private boolean isSameType(GraphQLType first, GraphQLType second) {
        GraphQLType left = first;
        GraphQLType right = second;
        while (true) {
            boolean leftNonNull = isNonNull(left);
            boolean leftList = isList(left);
            if (leftNonNull != isNonNull(right) || leftList != isList(right)) {
                return false;
            }
            if (!leftNonNull && !leftList) {
                return ((GraphQLNamedType) left).getName().equals(((GraphQLNamedType) right).getName());
            }
            left = unwrapOne(left);
            right = unwrapOne(right);
        }
    }

    private void checkFieldsContainer(GraphQLFieldsContainer type, List<GraphQLFieldDefinition> fieldDefinitions, SchemaValidationErrorCollector errors) {
        if (fieldDefinitions.isEmpty()) {
            addError(errors, SchemaValidationErrorType.ImplementingTypeLackOfFieldError, format("\"%s\" must define one or more fields.", type.getName()));
            return;
        }
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            checkFieldName(type.getName(), fieldDefinition.getName(), errors);
            for (GraphQLArgument argument : fieldDefinition.getArguments()) {
                checkArgumentName(type.getName(), fieldDefinition.getName(), argument.getName(), errors);
            }
        }
    }

    private void checkInputObject(GraphQLInputObjectType type, List<GraphQLInputObjectField> fields, SchemaValidationErrorCollector errors) {
        if (fields.isEmpty()) {
            addError(errors, SchemaValidationErrorType.InputObjectTypeLackOfFieldError, format("\"%s\" must define one or more fields.", type.getName()));
            return;
        }
        for (GraphQLInputObjectField field : fields) {
            checkFieldName(type.getName(), field.getName(), errors);
        }
    }

    private void checkUnion(GraphQLUnionType type, SchemaValidationErrorCollector errors) {
        List<GraphQLNamedOutputType> memberTypes = type.getTypes();
        if (memberTypes.isEmpty()) {
            addError(errors, SchemaValidationErrorType.UnionTypeLackOfTypeError, format("Union type \"%s\" must include one or more unique member types.", type.getName()));
        }
        Set<String> typeNames = new LinkedHashSet<>();
        for (GraphQLNamedOutputType memberType : memberTypes) {
            if (!(memberType instanceof GraphQLObjectType)) {
                addError(errors, SchemaValidationErrorType.InvalidUnionMemberTypeError,
                        format("The member types of a Union type must all be Object base types. member type \"%s\" in Union \"%s\" is invalid.", memberType.getName(), type.getName()));
            }
            if (!typeNames.add(memberType.getName())) {
                addError(errors, SchemaValidationErrorType.RepetitiveElementError,
                        format("The member types of a Union type must be unique. member type \"%s\" in Union \"%s\" is not unique.", memberType.getName(), type.getName()));
            }
        }
    }

    private void checkEnum(GraphQLEnumType type, List<GraphQLEnumValueDefinition> values, SchemaValidationErrorCollector errors) {
        if (values.isEmpty()) {
            addError(errors, SchemaValidationErrorType.EnumLackOfValueError, format("Enum type \"%s\" must define one or more enum values.", type.getName()));
            return;
        }
        for (GraphQLEnumValueDefinition value : values) {
            if (isReservedName(value.getName())) {
                addError(errors, SchemaValidationErrorType.InvalidCustomizedNameError,
                        format("EnumValueDefinition \"%s\" in  \"%s\"  must not begin with \"__\", which is reserved by GraphQL introspection.", value.getName(), type.getName()));
            }
        }
    }

    private void checkFieldName(String typeName, String fieldName, SchemaValidationErrorCollector errors) {
        if (isReservedName(fieldName) && !INTROSPECTION_SYSTEM_FIELDS.contains(fieldName)) {
            addError(errors, SchemaValidationErrorType.InvalidCustomizedNameError,
                    format("\"%s\" in \"%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", fieldName, typeName));
        }
    }

    private void checkArgumentName(String typeName, String fieldName, String argumentName, SchemaValidationErrorCollector errors) {
        if (isReservedName(argumentName)) {
            addError(errors, SchemaValidationErrorType.InvalidCustomizedNameError,
                    format("Argument name \"%s\" in \"%s-%s\" must not begin with \"__\", which is reserved by GraphQL introspection.", argumentName, typeName, fieldName));
        }
    }

    private boolean isReservedName(String name) {
        return name.startsWith("__");
    }

    private void checkDefaultValue(InputValueWithState defaultValue, GraphQLInputType type, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        if (isValidValue(defaultValue, type, schema, context, errors)) {
            return;
        }
        addError(errors, SchemaValidationErrorType.InvalidDefaultValue,
                format("Invalid default value %s for type %s", defaultValue.getValue(), simplePrint(type)));
    }

    private void checkAppliedDirectiveArgument(String directiveName, String argumentName, InputValueWithState argumentValue, GraphQLInputType argumentType, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        boolean invalid = !isValidValue(argumentValue, argumentType, schema, context, errors)
                || (argumentValue.isNotSet() && isNonNull(argumentType));
        if (invalid) {
            addError(errors, SchemaValidationErrorType.InvalidAppliedDirectiveArgument,
                    format("Invalid argument '%s' for applied directive of name '%s'", argumentName, directiveName));
        }
    }

    private boolean isValidValue(InputValueWithState value, GraphQLInputType type, GraphQLSchema schema, GraphQLContext context, SchemaValidationErrorCollector errors) {
        if (value.isLiteral()) {
            return validationUtil.isValidLiteralValue((Value<?>) value.getValue(), type, schema, context, Locale.getDefault());
        }
        // coercing external values expands input field defaults, which never terminates once defaults form a cycle
        if (!value.isExternal() || errors.containsValidationError(SchemaValidationErrorType.DefaultValueCircularRef)) {
            return true;
        }
        return isValidExternalValue(value.getValue(), type, schema, context);
    }

    private boolean isValidExternalValue(@Nullable Object externalValue, GraphQLInputType type, GraphQLSchema schema, GraphQLContext context) {
        try {
            ValuesResolver.externalValueToInternalValue(schema.getCodeRegistry().getFieldVisibility(), externalValue, type, context, Locale.getDefault());
            return true;
        } catch (CoercingParseValueException | NonNullableValueCoercedAsNullException e) {
            return false;
        }
    }

    private void checkAppliedDirectives(GraphQLDirectiveContainer container, GraphQLSchema schema, SchemaValidationErrorCollector errors) {
        Map<String, List<GraphQLDirective>> directivesByName = container.getAllDirectivesByName();
        if (directivesByName.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<GraphQLDirective>> entry : directivesByName.entrySet()) {
            GraphQLDirective directiveDef = schema.getDirective(entry.getKey());
            if (directiveDef == null) {
                addError(errors, SchemaValidationErrorType.InvalidAppliedDirective, format("A definition for directive '%s' could not be found", entry.getKey()));
            } else if (directiveDef.isNonRepeatable() && entry.getValue().size() > 1) {
                addError(errors, SchemaValidationErrorType.InvalidAppliedDirective,
                        format("The directive '%s' on the '%s' called '%s' is a non repeatable directive but has been applied %d times",
                                directiveDef.getName(), container.getClass().getSimpleName(), container.getName(), entry.getValue().size()));
            }
        }
    }

    private void checkFieldTypesUsedAppropriately(GraphQLFieldsContainer container, GraphQLFieldDefinition fieldDefinition, SchemaValidationErrorCollector errors) {
        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            if (hasMisusedType(argument.getType(), true)) {
                String path = container.getName() + "." + fieldDefinition.getName() + "." + argument.getName();
                addTypeContextErrors(argument.getType(), true, path, errors);
            }
        }
        if (hasMisusedType(fieldDefinition.getType(), false)) {
            String path = container.getName() + "." + fieldDefinition.getName();
            addTypeContextErrors(fieldDefinition.getType(), false, path, errors);
        }
    }

    private void checkInputFieldTypeUsedAppropriately(GraphQLInputObjectType type, GraphQLInputObjectField inputObjectField, SchemaValidationErrorCollector errors) {
        if (hasMisusedType(inputObjectField.getType(), true)) {
            String path = type.getName() + "." + inputObjectField.getName();
            addTypeContextErrors(inputObjectField.getType(), true, path, errors);
        }
    }

    private boolean hasMisusedType(GraphQLType type, boolean inputContext) {
        GraphQLType current = type;
        while (true) {
            if (isMisused(current, inputContext)) {
                return true;
            }
            if (!(current instanceof GraphQLModifiedType)) {
                return false;
            }
            current = ((GraphQLModifiedType) current).getWrappedType();
        }
    }

    private void addTypeContextErrors(GraphQLType type, boolean inputContext, String path, SchemaValidationErrorCollector errors) {
        GraphQLType current = type;
        while (true) {
            if (isMisused(current, inputContext)) {
                addTypeContextError(current, inputContext, path, errors);
            }
            if (!(current instanceof GraphQLModifiedType)) {
                return;
            }
            current = ((GraphQLModifiedType) current).getWrappedType();
        }
    }

    private void addTypeContextError(GraphQLType misusedType, boolean inputContext, String path, SchemaValidationErrorCollector errors) {
        if (inputContext) {
            addError(errors, SchemaValidationErrorType.OutputTypeUsedInInputTypeContext,
                    format("The output type '%s' has been used in an input type context : '%s'", simplePrint(misusedType), path));
        } else {
            addError(errors, SchemaValidationErrorType.InputTypeUsedInOutputTypeContext,
                    format("The input type '%s' has been used in a output type context : '%s'", simplePrint(misusedType), path));
        }
    }

    private boolean isMisused(GraphQLType type, boolean inputContext) {
        return inputContext ? !(type instanceof GraphQLInputType) : !(type instanceof GraphQLOutputType);
    }

    private void checkOneOfInhabited(GraphQLInputObjectType inputObjectType, SchemaValidationErrorCollector errors) {
        if (!inputObjectType.isOneOf()) {
            return;
        }
        if (!canBeProvidedAFiniteValue(inputObjectType, newIdentitySet())) {
            addError(errors, SchemaValidationErrorType.OneOfNotInhabited,
                    format("OneOf Input Object %s must be inhabited but all fields recursively reference only other OneOf Input Objects forming an unresolvable cycle.", inputObjectType.getName()));
        }
    }

    private boolean canBeProvidedAFiniteValue(GraphQLInputObjectType oneOfInputObject, Set<GraphQLInputObjectType> visited) {
        if (!visited.add(oneOfInputObject)) {
            return false;
        }
        boolean result = anyFieldCanBeProvidedAFiniteValue(oneOfInputObject, visited);
        visited.remove(oneOfInputObject);
        return result;
    }

    private boolean anyFieldCanBeProvidedAFiniteValue(GraphQLInputObjectType oneOfInputObject, Set<GraphQLInputObjectType> visited) {
        for (GraphQLInputObjectField field : oneOfInputObject.getFieldDefinitions()) {
            GraphQLType fieldType = field.getType();
            if (isList(fieldType)) {
                return true;
            }
            GraphQLType namedFieldType = unwrapAll(fieldType);
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                return true;
            }
            GraphQLInputObjectType inputFieldType = (GraphQLInputObjectType) namedFieldType;
            if (!inputFieldType.isOneOf() || canBeProvidedAFiniteValue(inputFieldType, visited)) {
                return true;
            }
        }
        return false;
    }

    private void checkOneOfField(GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField, SchemaValidationErrorCollector errors) {
        if (!inputObjectType.isOneOf()) {
            return;
        }
        if (inputObjectField.hasSetDefaultValue()) {
            addError(errors, SchemaValidationErrorType.OneOfDefaultValueOnField,
                    format("OneOf input field %s.%s cannot have a default value.", inputObjectType.getName(), inputObjectField.getName()));
        }
        if (isNonNull(inputObjectField.getType())) {
            addError(errors, SchemaValidationErrorType.OneOfNonNullableField,
                    format("OneOf input field %s.%s must be nullable.", inputObjectType.getName(), inputObjectField.getName()));
        }
    }

    private void checkDeprecatedInputField(GraphQLInputObjectType type, GraphQLInputObjectField inputObjectField, SchemaValidationErrorCollector errors) {
        if (!isNonNull(inputObjectField.getType()) || inputObjectField.hasSetDefaultValue()) {
            return;
        }
        if (inputObjectField.getAppliedDirective(DEPRECATED_DIRECTIVE_NAME) == null) {
            return;
        }
        addError(errors, SchemaValidationErrorType.RequiredInputFieldCannotBeDeprecated,
                format("Required input field '%s.%s' cannot be deprecated.", type.getName(), inputObjectField.getName()));
    }

    private void checkDeprecatedArgument(GraphQLArgument argument, GraphQLSchemaElement fieldOrDirective, SchemaValidationErrorCollector errors) {
        if (!argument.isDeprecated() || !isNonNull(argument.getType()) || argument.hasSetDefaultValue()) {
            return;
        }
        if (fieldOrDirective instanceof GraphQLFieldDefinition) {
            addError(errors, SchemaValidationErrorType.RequiredFieldArgumentCannotBeDeprecated,
                    format("Required argument '%s' on field '%s' cannot be deprecated.", argument.getName(), ((GraphQLFieldDefinition) fieldOrDirective).getName()));
        } else if (fieldOrDirective instanceof GraphQLDirective) {
            addError(errors, SchemaValidationErrorType.RequiredDirectiveArgumentCannotBeDeprecated,
                    format("Required argument '%s' on directive '%s' cannot be deprecated.", argument.getName(), ((GraphQLDirective) fieldOrDirective).getName()));
        }
    }
}
