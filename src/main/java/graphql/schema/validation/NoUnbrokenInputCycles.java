package graphql.schema.validation;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLModifiedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Schema validation rule ensuring no input type forms an unbroken non-nullable recursion,
 * as such a type would be impossible to satisfy
 */
public class NoUnbrokenInputCycles implements SchemaValidationRule {

    @Override
    public void check(GraphQLType type, SchemaValidationErrorCollector validationErrorCollector) {
    }

    @Override
    public void check(GraphQLFieldDefinition fieldDef, SchemaValidationErrorCollector validationErrorCollector) {
        for (GraphQLArgument argument : fieldDef.getArguments()) {
            GraphQLInputType argumentType = argument.getType();
            if (argumentType instanceof GraphQLInputObjectType) {
                List<String> path = new ArrayList<>();
                path.add(argumentType.getName());
                check((GraphQLInputObjectType) argumentType, new HashSet<>(), path, validationErrorCollector);
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
            if (field.getType() instanceof GraphQLNonNull) {
                GraphQLType unwrapped = unwrapNonNull((GraphQLNonNull) field.getType());
                if (unwrapped instanceof GraphQLInputObjectType) {
                    path = new ArrayList<>(path);
                    path.add(field.getName() + "!");
                    check((GraphQLInputObjectType) unwrapped, new HashSet<>(seen), path, validationErrorCollector);
                }
            }
        }
    }

    private GraphQLType unwrapNonNull(GraphQLNonNull type) {
        if (type.getWrappedType() instanceof GraphQLList) {
            //we only care about [type!]! i.e. non-null lists of non-nulls
            if (((GraphQLList) type.getWrappedType()).getWrappedType() instanceof GraphQLNonNull) {
                return unwrap(((GraphQLList) type.getWrappedType()).getWrappedType());
            } else {
                return type.getWrappedType();
            }
        } else {
            return unwrap(type.getWrappedType());
        }
    }

    private GraphQLType unwrap(GraphQLType type) {
        if (type instanceof GraphQLModifiedType) {
            return unwrap(((GraphQLModifiedType) type).getWrappedType());
        }
        return type;
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
