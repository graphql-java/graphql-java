package graphql.schema.validation.rule;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLFieldsContainer;

import graphql.schema.validation.exception.SchemaValidationError;
import graphql.schema.validation.exception.SchemaValidationErrorCollector;
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

    private final Set<GraphQLOutputType> processed = new LinkedHashSet<>();

    @Override
    public void apply(GraphQLSchema graphQLSchema, SchemaValidationErrorCollector validationErrorCollector) {

        traverse(graphQLSchema.getQueryType(), validationErrorCollector);

        if (graphQLSchema.isSupportingMutations()) {
            traverse(graphQLSchema.getMutationType(), validationErrorCollector);
        }
        if (graphQLSchema.isSupportingSubscriptions()) {
            traverse(graphQLSchema.getSubscriptionType(), validationErrorCollector);
        }
    }

    private void traverse(GraphQLOutputType root, SchemaValidationErrorCollector validationErrorCollector) {
        if (processed.contains(root)) {
            return;
        }
        processed.add(root);
        if (root instanceof GraphQLFieldsContainer) {
            // this deliberately has open field visibility here since its validating the schema
            // when completely open
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
                List<String> path = new ArrayList<>();
                check((GraphQLInputObjectType) argumentType, new LinkedHashSet<>(), path, validationErrorCollector);
            }
        }
    }

    private void check(GraphQLInputObjectType type, Set<GraphQLType> seen, List<String> path, SchemaValidationErrorCollector validationErrorCollector) {
        if (seen.contains(type)) {
            validationErrorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, getErrorMessage(path)));
            return;
        }
        seen.add(type);

        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            if (isNonNull(field.getType())) {
                GraphQLType unwrapped = unwrapNonNull((GraphQLNonNull) field.getType());
                if (unwrapped instanceof GraphQLInputObjectType) {
                    path = new ArrayList<>(path);
                    path.add(field.getName() + "!");
                    check((GraphQLInputObjectType) unwrapped, new LinkedHashSet<>(seen), path, validationErrorCollector);
                }
            }
        }
    }

    private GraphQLType unwrapNonNull(GraphQLNonNull type) {
        if (isList(type.getWrappedType())) {
            //we only care about [type!]! i.e. non-null lists of non-nulls
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
