package graphql.schema.validation;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;

import java.util.List;
import java.util.Objects;

import static graphql.schema.GraphQLTypeUtil.getUnwrappedTypeName;
import static graphql.schema.validation.SchemaValidationErrorType.ObjectDoesNotImplementItsInterfaces;
import static java.lang.String.format;

/**
 * Schema validation rule ensuring object types have all the fields that they need to implement the interfaces
 * they say they implement
 */
public class ObjectsImplementInterfaces implements SchemaValidationRule {

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {
    }

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {
        if (type instanceof GraphQLObjectType) {
            check((GraphQLObjectType) type, validationErrorCollector);
        }
    }

    // visible for testing
    private void check(GraphQLObjectType objectTyoe, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLOutputType> interfaces = objectTyoe.getInterfaces();
        interfaces.forEach(interfaceType -> {
            // we have resolved the interfaces at this point and hence the cast is ok
            checkObjectImplementsInterface(objectTyoe, (GraphQLInterfaceType) interfaceType, validationErrorCollector);
        });

    }

    private void checkObjectImplementsInterface(GraphQLObjectType objectTyoe, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector) {
        List<GraphQLFieldDefinition> fieldDefinitions = interfaceType.getFieldDefinitions();
        for (GraphQLFieldDefinition interfaceFieldDef : fieldDefinitions) {
            GraphQLFieldDefinition objectFieldDef = objectTyoe.getFieldDefinition(interfaceFieldDef.getName());
            if (objectFieldDef == null) {
                validationErrorCollector.addError(
                        error(format("object type '%s' does not implement interface '%s' because field '%s' is missing",
                                objectTyoe.getName(), interfaceType.getName(), interfaceFieldDef.getName())));
            } else {
                checkFieldTypeEquivalence(objectTyoe, interfaceType, validationErrorCollector, interfaceFieldDef, objectFieldDef);
            }
        }
    }

    private void checkFieldTypeEquivalence(GraphQLObjectType objectTyoe, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef) {
        // the reference implementation has a full but complicated abstract type equivalence check
        // this is not that but we can add that later.  It does cover the major cases however
        String interfaceFieldDefStr = getUnwrappedTypeName(interfaceFieldDef.getType());
        String objectFieldDefStr = getUnwrappedTypeName(objectFieldDef.getType());

        if (!interfaceFieldDefStr.equals(objectFieldDefStr)) {
            validationErrorCollector.addError(
                    error(format("object type '%s' does not implement interface '%s' because field '%s' is defined as '%s' type and not as '%s' type",
                            objectTyoe.getName(), interfaceType.getName(), interfaceFieldDef.getName(), objectFieldDefStr, interfaceFieldDefStr)));
        } else {
            checkFieldArgumentEquivalence(objectTyoe, interfaceType, validationErrorCollector, interfaceFieldDef, objectFieldDef);
        }
    }

    private void checkFieldArgumentEquivalence(GraphQLObjectType objectTyoe, GraphQLInterfaceType interfaceType, SchemaValidationErrorCollector validationErrorCollector, GraphQLFieldDefinition interfaceFieldDef, GraphQLFieldDefinition objectFieldDef) {
        List<GraphQLArgument> interfaceArgs = interfaceFieldDef.getArguments();
        List<GraphQLArgument> objectArgs = objectFieldDef.getArguments();
        if (interfaceArgs.size() != objectArgs.size()) {
            validationErrorCollector.addError(
                    error(format("object type '%s' does not implement interface '%s' because field '%s' has a different number of arguments",
                            objectTyoe.getName(), interfaceType.getName(), interfaceFieldDef.getName())));
        } else {
            for (int i = 0; i < interfaceArgs.size(); i++) {
                GraphQLArgument interfaceArg = interfaceArgs.get(i);
                GraphQLArgument objectArg = objectArgs.get(i);

                String interfaceArgStr = makeArgStr(interfaceArg);
                String objectArgStr = makeArgStr(objectArg);

                boolean same = true;
                if (!interfaceArgStr.equals(objectArgStr)) {
                    same = false;
                }
                if (!Objects.equals(interfaceArg.getDefaultValue(), objectArg.getDefaultValue())) {
                    same = false;
                }
                if (!same) {
                    validationErrorCollector.addError(
                            error(format("object type '%s' does not implement interface '%s' because field '%s' argument '%s' is defined differently",
                                    objectTyoe.getName(), interfaceType.getName(), interfaceFieldDef.getName(), interfaceArg.getName())));
                }

            }
        }
    }

    private String makeArgStr(GraphQLArgument argument) {
        // we don't do default value checking because toString of getDefaultValue is not guaranteed to be stable
        return argument.getName() +
                ":" +
                getUnwrappedTypeName(argument.getType());

    }

    private SchemaValidationError error(String msg) {
        return new SchemaValidationError(ObjectDoesNotImplementItsInterfaces, msg);
    }

}
