package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Schema validation rule ensuring no input type forms an unbroken non-nullable recursion,
 * as such a type would be impossible to satisfy
 */
@Internal
public class InputAndOutputTypesUsedAppropriately extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition fieldDef, TraverserContext<GraphQLSchemaElement> context) {
        String typeName = getTypeName((GraphQLType) context.getParentNode());
        String fieldName = typeName + "." + fieldDef.getName();
        SchemaValidationErrorCollector validationErrorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        for (GraphQLArgument argument : fieldDef.getArguments()) {
            String argName = fieldName + "." + argument.getName();
            GraphQLInputType argumentType = argument.getType();
            checkIsAllInputTypes(argumentType, validationErrorCollector, argName);
        }
        checkIsAllOutputTypes(fieldDef.getType(), validationErrorCollector, fieldName);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField fieldDef, TraverserContext<GraphQLSchemaElement> context) {
        String typeName = getTypeName((GraphQLType) context.getParentNode());
        String fieldName = typeName + "." + fieldDef.getName();
        SchemaValidationErrorCollector validationErrorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        checkIsAllInputTypes(fieldDef.getType(), validationErrorCollector, fieldName);
        return TraversalControl.CONTINUE;
    }

    private void checkIsAllInputTypes(GraphQLInputType inputType,
                                      SchemaValidationErrorCollector validationErrorCollector,
                                      String argName) {
        checkTypeContext(inputType, validationErrorCollector, argName,
                typeToCheck -> typeToCheck instanceof GraphQLInputType,
                (typeToCheck, path) -> new SchemaValidationError(SchemaValidationErrorType.OutputTypeUsedInInputTypeContext,
                        String.format("The output type '%s' has been used in an input type context : '%s'", typeToCheck, path)));
    }

    private void checkIsAllOutputTypes(GraphQLOutputType outputType,
                                       SchemaValidationErrorCollector validationErrorCollector,
                                       String fieldName) {
        checkTypeContext(outputType, validationErrorCollector, fieldName,
                typeToCheck -> typeToCheck instanceof GraphQLOutputType,
                (typeToCheck, path) -> new SchemaValidationError(SchemaValidationErrorType.InputTypeUsedInOutputTypeContext,
                        String.format("The input type '%s' has been used in a output type context : '%s'", typeToCheck, path)));
    }

    private void checkTypeContext(GraphQLType type,
                                  SchemaValidationErrorCollector validationErrorCollector,
                                  String path,
                                  Predicate<GraphQLType> typePredicate,
                                  BiFunction<String, String, SchemaValidationError> errorMaker) {
        while (true) {
            String typeName = getTypeName(type);
            boolean isOk = typePredicate.test(type);
            if (!isOk) {
                validationErrorCollector.addError(errorMaker.apply(typeName, path));
            }
            if (type instanceof GraphQLModifiedType) {
                type = ((GraphQLModifiedType) type).getWrappedType();
            } else {
                return;
            }
        }
    }

    private String getTypeName(GraphQLType type) {
        return GraphQLTypeUtil.simplePrint(type);
    }
}
