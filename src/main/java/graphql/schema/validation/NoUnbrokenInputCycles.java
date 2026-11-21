package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;

/**
 * Schema validation rule ensuring no input type forms an unbroken non-nullable recursion,
 * as such a type would be impossible to satisfy
 */
@Internal
@NullMarked
public class NoUnbrokenInputCycles implements SchemaValidationRule {

    private final SchemaValidationErrorCollector errors;
    // results only depend on the argument's input type, so each type is explored once
    private final Set<GraphQLInputObjectType> checkedArgumentTypes = Collections.newSetFromMap(new IdentityHashMap<>());

    public NoUnbrokenInputCycles(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkFieldDefinition(GraphQLFieldsContainer container, GraphQLFieldDefinition fieldDefinition) {
        for (GraphQLArgument argument : fieldDefinition.getArguments()) {
            GraphQLInputType argumentType = argument.getType();
            if (argumentType instanceof GraphQLInputObjectType && checkedArgumentTypes.add((GraphQLInputObjectType) argumentType)) {
                check((GraphQLInputObjectType) argumentType, Collections.newSetFromMap(new IdentityHashMap<>()), new ArrayList<>());
            }
        }
    }

    // path entries deliberately accumulate across sibling fields, which existing error messages rely on
    private void check(GraphQLInputObjectType type, Set<GraphQLInputObjectType> pathTypes, List<String> path) {
        if (!pathTypes.add(type)) {
            errors.addError(new SchemaValidationError(SchemaValidationErrorType.UnbrokenInputCycle, "[" + String.join(".", path) + "] forms an unsatisfiable cycle"));
            return;
        }
        int pathSizeOnEntry = path.size();
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            GraphQLInputObjectType next = nonNullInputObjectType(field.getType());
            if (next != null) {
                path.add(field.getName() + "!");
                check(next, pathTypes, path);
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
}
