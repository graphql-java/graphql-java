package graphql.schema.validation.rules;

import graphql.schema.*;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * Schema validation rule ensuring no input type forms an unbroken non-nullable recursion,
 * as such a type would be impossible to satisfy
 */
public class NoUnbrokenInputCycles implements SchemaValidationRule {

    @Override
    public void check(GraphQLSchema schema, SchemaValidationErrorCollector validationErrorCollector) {
        traverse(schema.getQueryType(), validationErrorCollector);

        if (schema.isSupportingMutations()) {
            traverse(schema.getMutationType(), validationErrorCollector);
        }

        if (schema.isSupportingSubscriptions()) {
            traverse(schema.getSubscriptionType(), validationErrorCollector);
        }
    }

    private final Set<GraphQLOutputType> processed = new LinkedHashSet<>();

    private void traverse(GraphQLOutputType root, SchemaValidationErrorCollector validationErrorCollector) {
        if (processed.contains(root)) {
            return;
        }
        processed.add(root);
        if (root instanceof GraphQLFieldsContainer) {
//            遍历字段定义，然后应用每个规则
            for (GraphQLFieldDefinition fieldDefinition : ((GraphQLFieldsContainer) root).getFieldDefinitions()) {
                checkFieldDefinition(fieldDefinition, validationErrorCollector);
                traverse(fieldDefinition.getType(), validationErrorCollector);
            }
        }
    }

    public void checkFieldDefinition(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {

        for (GraphQLArgument argument : fieldDef.getArguments()) {

            GraphQLInputType argumentType = argument.getType();

            if (argumentType instanceof GraphQLInputObjectType) {
                GraphQLInputObjectType inputType = (GraphQLInputObjectType) argumentType;
                List<String> traversedPath = new ArrayList<>();
                LinkedHashSet<GraphQLType> traversedInputType = new LinkedHashSet();
                traverseInputType(inputType, traversedInputType, traversedPath, validationErrorCollector);
            }
        }
    }

    private void traverseInputType(GraphQLInputObjectType type, Set<GraphQLType> traversedType, List<String> path, SchemaValidationErrorCollector validationErrorCollector) {
        if (traversedType.contains(type)) {
            validationErrorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, getErrorMessage(path)));
            return;
        }

        traversedType.add(type);
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            if (isNonNull(field.getType())) {
                GraphQLType unwrappedType = unwrapNonNull((GraphQLNonNull) field.getType());
                //如果是输入类型
                if (unwrappedType instanceof GraphQLInputObjectType) {
                    GraphQLInputObjectType inputObjectType=(GraphQLInputObjectType)unwrappedType;
                    path.add(field.getName() + "!");
                    traverseInputType(inputObjectType,traversedType, path, validationErrorCollector);
                    path.remove(field.getName() + "!");
                }
            }
        }
        traversedType.remove(type);
    }

    private GraphQLType unwrapNonNull(GraphQLNonNull type) {
        if (isList(type.getWrappedType())) {
            GraphQLList listType = (GraphQLList) type.getWrappedType();
            if (isNonNull(listType.getWrappedType())) {
                return unwrapAll(listType.getWrappedType());
            } else {
                return type.getWrappedType();
            }
        } else {
            return unwrapAll(type.getWrappedType());
        }
    }

    private String getErrorMessage(List<String> path) {
        StringBuilder message = new StringBuilder();
        message.append("[");
        for (int i = 0; i < path.size(); i++) {
            if (i != 0) {
                message.append(".");
            }
            message.append(path.get(i));
        }
        message.append("] forms an unsatisfiable cycle");
        return message.toString();
    }

}
