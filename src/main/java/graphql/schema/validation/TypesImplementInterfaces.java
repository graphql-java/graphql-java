package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLImplementingType;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapOne;
import static java.lang.String.format;

/**
 * Schema validation rule ensuring object and interface types have all the fields that they need to
 * implement the interfaces they say they implement.
 */
@Internal
@NullMarked
public class TypesImplementInterfaces implements SchemaValidationRule {

    private final SchemaValidationErrorCollector errors;

    public TypesImplementInterfaces(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkObjectType(GraphQLObjectType objectType) {
        checkImplementsInterfaces(objectType);
    }

    @Override
    public void checkInterfaceType(GraphQLInterfaceType interfaceType) {
        checkImplementsInterfaces(interfaceType);
    }

    private void checkImplementsInterfaces(GraphQLImplementingType implementingType) {
        List<GraphQLNamedOutputType> interfaces = implementingType.getInterfaces();
        for (GraphQLNamedOutputType interfaceType : interfaces) {
            checkObjectImplementsInterface(implementingType, (GraphQLInterfaceType) interfaceType);
        }
    }

    private void checkObjectImplementsInterface(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType) {
        for (GraphQLFieldDefinition interfaceFieldDef : interfaceType.getFieldDefinitions()) {
            GraphQLFieldDefinition objectFieldDef = implementingType.getFieldDefinition(interfaceFieldDef.getName());
            if (objectFieldDef == null) {
                addInterfaceError(format("%s type '%s' does not implement interface '%s' because field '%s' is missing",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName()));
            } else {
                checkFieldTypeCompatibility(implementingType, interfaceType, interfaceFieldDef, objectFieldDef);
            }
        }
        checkTransitiveImplementations(implementingType, interfaceType);
    }

    private void checkTransitiveImplementations(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType) {
        List<GraphQLNamedOutputType> implementedInterfaces = implementingType.getInterfaces();
        for (GraphQLNamedOutputType transitiveInterface : interfaceType.getInterfaces()) {
            if (transitiveInterface.equals(implementingType)) {
                addInterfaceError(format("%s type '%s' cannot implement '%s' because that would result on a circular reference",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName()));
            } else if (!implementedInterfaces.contains(transitiveInterface)) {
                addInterfaceError(format("%s type '%s' must implement '%s' because it is implemented by '%s'",
                        typeOf(implementingType), implementingType.getName(), transitiveInterface.getName(), interfaceType.getName()));
            }
        }
    }

    private void checkFieldTypeCompatibility(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef) {
        if (isCompatible(interfaceFieldDef.getType(), objectFieldDef.getType())) {
            checkFieldArgumentEquivalence(implementingType, interfaceType, interfaceFieldDef, objectFieldDef);
            return;
        }
        addInterfaceError(format("%s type '%s' does not implement interface '%s' because field '%s' is defined as '%s' type and not as '%s' type",
                typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(),
                simplePrint(objectFieldDef.getType()), simplePrint(interfaceFieldDef.getType())));
    }

    private void checkFieldArgumentEquivalence(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef) {
        List<GraphQLArgument> interfaceArgs = interfaceFieldDef.getArguments();
        for (GraphQLArgument interfaceArg : interfaceArgs) {
            if (objectFieldDef.getArgument(interfaceArg.getName()) == null) {
                addInterfaceError(format("%s type '%s' does not implement interface '%s' because field '%s' is missing argument(s): '%s'",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(),
                        missingArgumentNames(interfaceArgs, objectFieldDef)));
                return;
            }
        }
        for (GraphQLArgument objectArg : objectFieldDef.getArguments()) {
            GraphQLArgument interfaceArg = interfaceFieldDef.getArgument(objectArg.getName());
            if (interfaceArg == null) {
                checkAdditionalArgumentIsOptional(implementingType, interfaceType, objectFieldDef, objectArg);
            } else if (!isSameType(objectArg.getType(), interfaceArg.getType())) {
                addInterfaceError(format("%s type '%s' does not implement interface '%s' because field '%s' argument '%s' is defined differently",
                        typeOf(implementingType), implementingType.getName(), interfaceType.getName(), interfaceFieldDef.getName(), objectArg.getName()));
            }
        }
    }

    private void checkAdditionalArgumentIsOptional(GraphQLImplementingType implementingType, GraphQLInterfaceType interfaceType, GraphQLFieldDefinition objectFieldDef, GraphQLArgument objectArg) {
        if (objectArg.getType() instanceof GraphQLNonNull) {
            addInterfaceError(format("%s type '%s' field '%s' defines an additional non-optional argument '%s' which is not allowed because field is also defined in interface '%s'",
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

    private void addInterfaceError(String message) {
        errors.addError(new SchemaValidationError(SchemaValidationErrorType.ObjectDoesNotImplementItsInterfaces, message));
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
}
