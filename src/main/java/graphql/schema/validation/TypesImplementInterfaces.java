package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.util.FpKit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static graphql.collect.ImmutableKit.map;
import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static graphql.schema.validation.SchemaValidationErrorType.ObjectDoesNotImplementItsInterfaces;
import static java.lang.String.format;

/**
 * Schema validation rule ensuring object and interface types have all the fields that they need to
 * implement the interfaces they say they implement.
 */
@Internal
public class TypesImplementInterfaces implements SchemaValidationRule {
    private static final Map<Class<? extends GraphQLImplementingType>, String> TYPE_OF_MAP = new HashMap<>();

    static {
        TYPE_OF_MAP.put(GraphQLObjectType.class, "object");
        TYPE_OF_MAP.put(GraphQLInterfaceType.class, "interface");
    }

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {
    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {
        if (type instanceof GraphQLImplementingType) {
            check((GraphQLImplementingType) type, validationErrorCollector);
        }
    }

    @Override
    public void check(GraphQLSchema graphQLSchema, SchemaValidationErrorCollector validationErrorCollector) {
    }

    private void check(GraphQLImplementingType implementingType, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLNamedOutputType> interfaces = implementingType.getInterfaces();
        interfaces.forEach(interfaceType -> {
            // we have resolved the interfaces at this point and hence the cast is ok
            checkObjectImplementsInterface(implementingType, (GraphQLInterfaceType) interfaceType, validationErrorCollector);
        });

    }

    // this deliberately has open field visibility here since its validating the schema
    // when completely open
    private void checkObjectImplementsInterface(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLFieldDefinition> fieldDefinitions = interfaceType.getFieldDefinitions();
        for (GraphQLFieldDefinition interfaceFieldDef : fieldDefinitions) {
            GraphQLFieldDefinition objectFieldDef = implementingType.getFieldDefinition(interfaceFieldDef.getName());
            if (objectFieldDef == null) {
                validationErrorCollector.addError(
                        error(format("%s type '%s' does not implement interface '%s' because field '%s' is missing",
                                TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName())));
            } else {
                checkFieldTypeCompatibility(implementingType, interfaceType, validationErrorCollector, interfaceFieldDef, objectFieldDef);
            }
        }

        checkTransitiveImplementations(implementingType, interfaceType, validationErrorCollector);
    }

    private void checkTransitiveImplementations(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLNamedOutputType> implementedInterfaces = implementingType.getInterfaces();
        interfaceType.getInterfaces().forEach(transitiveInterface -> {
            if (transitiveInterface.equals(implementingType)) {
                validationErrorCollector.addError(
                        error(format("%s type '%s' cannot implement '%s' because that would result on a circular reference",
                                TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), interfaceType.getName()))
                );
            } else if (!implementedInterfaces.contains(transitiveInterface)) {
                validationErrorCollector.addError(
                        error(format("%s type '%s' must implement '%s' because it is implemented by '%s'",
                                TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), transitiveInterface.getName(), interfaceType.getName())));
            }
        });
    }

    private void checkFieldTypeCompatibility(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef) {
        String interfaceFieldDefStr = simplePrint(interfaceFieldDef.getType());
        String objectFieldDefStr = simplePrint(objectFieldDef.getType());

        if (!isCompatible(interfaceFieldDef.getType(), objectFieldDef.getType())) {
            validationErrorCollector.addError(
                    error(format("%s type '%s' does not implement interface '%s' because field '%s' is defined as '%s' type and not as '%s' type",
                            TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(), objectFieldDefStr, interfaceFieldDefStr)));
        } else {
            checkFieldArgumentEquivalence(implementingType, interfaceType, validationErrorCollector, interfaceFieldDef, objectFieldDef);
        }
    }

    private void checkFieldArgumentEquivalence(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef) {
        List<GraphQLArgument> interfaceArgs = interfaceFieldDef.getArguments();
        List<GraphQLArgument> objectArgs = objectFieldDef.getArguments();

        Map<String, GraphQLArgument> interfaceArgsByName = FpKit.getByName(interfaceArgs, GraphQLArgument::getName);
        List<String> objectArgsNames = map(objectArgs, GraphQLArgument::getName);

        if (!objectArgsNames.containsAll(interfaceArgsByName.keySet())) {
            final String missingArgsNames = interfaceArgsByName.keySet().stream()
                    .filter(name -> !objectArgsNames.contains(name))
                    .collect(Collectors.joining(", "));

            validationErrorCollector.addError(
                    error(format("%s type '%s' does not implement interface '%s' because field '%s' is missing argument(s): '%s'",
                            TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(), missingArgsNames)));
        } else {
            objectArgs.forEach(objectArg -> {
                GraphQLArgument interfaceArg = interfaceArgsByName.get(objectArg.getName());

                if (interfaceArg == null) {
                    if (objectArg.getType() instanceof GraphQLNonNull) {
                        validationErrorCollector.addError(
                                error(format("%s type '%s' field '%s' defines an additional non-optional argument '%s' which is not allowed because field is also defined in interface '%s'",
                                        TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), objectFieldDef.getName(), objectArg.getName(), interfaceType.getName())));
                    }
                } else {
                    String interfaceArgStr = makeArgStr(objectArg);
                    String objectArgStr = makeArgStr(interfaceArg);

                    boolean same = true;
                    if (!interfaceArgStr.equals(objectArgStr)) {
                        same = false;
                    }
                    if (!Objects.equals(objectArg.getDefaultValue(), interfaceArg.getDefaultValue())) {
                        same = false;
                    }
                    if (!same) {
                        validationErrorCollector.addError(
                                error(format("%s type '%s' does not implement interface '%s' because field '%s' argument '%s' is defined differently",
                                        TYPE_OF_MAP.get(implementingType.getClass()), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(), objectArg.getName())));
                    }
                }
            });
        }
    }

    private String makeArgStr(GraphQLArgument argument) {
        // we don't do default value checking because toString of getDefaultValue is not guaranteed to be stable
        return argument.getName() +
                ":" +
                simplePrint(argument.getType());

    }

    private SchemaValidationError error(String msg) {
        return new SchemaValidationError(ObjectDoesNotImplementItsInterfaces, msg);
    }

    /**
     * @return {@code true} if the specified implementingType satisfies the constraintType.
     */
    boolean isCompatible(GraphQLOutputType constraintType, GraphQLOutputType objectType) {
        if (isSameType(constraintType, objectType)) {
            return true;
        } else if (constraintType instanceof GraphQLUnionType) {
            return objectIsMemberOfUnion((GraphQLUnionType) constraintType, objectType);
        } else if (constraintType instanceof GraphQLInterfaceType && objectType instanceof GraphQLObjectType) {
            return objectImplementsInterface((GraphQLInterfaceType) constraintType, (GraphQLObjectType) objectType);
        } else if (constraintType instanceof GraphQLInterfaceType && objectType instanceof GraphQLInterfaceType) {
            return interfaceImplementsInterface((GraphQLInterfaceType) constraintType, (GraphQLInterfaceType) objectType);
        } else if (isList(constraintType) && isList(objectType)) {
            GraphQLOutputType wrappedConstraintType = (GraphQLOutputType) unwrapOne(constraintType);
            GraphQLOutputType wrappedObjectType = (GraphQLOutputType) unwrapOne(objectType);
            return isCompatible(wrappedConstraintType, wrappedObjectType);
        } else if (isNonNull(objectType)) {
            GraphQLOutputType nullableConstraint;
            if (isNonNull(constraintType)) {
                nullableConstraint = (GraphQLOutputType) unwrapOne(constraintType);
            } else {
                nullableConstraint = constraintType;
            }
            GraphQLOutputType nullableObjectType = (GraphQLOutputType) unwrapOne(objectType);
            return isCompatible(nullableConstraint, nullableObjectType);
        } else {
            return false;
        }
    }

    boolean isSameType(GraphQLOutputType a, GraphQLOutputType b) {
        String aDefString = simplePrint(a);
        String bDefString = simplePrint(b);
        return aDefString.equals(bDefString);
    }

    boolean objectImplementsInterface(GraphQLInterfaceType interfaceType, GraphQLObjectType objectType) {
        return objectType.getInterfaces().contains(interfaceType);
    }

    boolean interfaceImplementsInterface(GraphQLInterfaceType interfaceType, GraphQLInterfaceType implementingType) {
        return implementingType.getInterfaces().contains(interfaceType);
    }

    boolean objectIsMemberOfUnion(GraphQLUnionType unionType, GraphQLOutputType objectType) {
        return unionType.getTypes().contains(objectType);
    }

}
