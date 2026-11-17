package graphql.schema.validation;

import graphql.Internal;
import graphql.language.ArrayValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.InputValueWithState;
import graphql.util.FpKit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static graphql.schema.GraphQLTypeUtil.unwrapNonNull;

@Internal
@NullMarked
public class DefaultValueDepth {

    private final int maxDepth;

    public DefaultValueDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void check(GraphQLSchema schema, SchemaValidationErrorCollector errors) {
        for (GraphQLNamedType type : schema.getAllTypesAsList()) {
            checkType(type, errors);
        }
        for (GraphQLDirective directive : schema.getDirectives()) {
            checkArguments(directive.getArguments(), "@" + directive.getName(), errors);
        }
    }

    private void checkType(GraphQLNamedType type, SchemaValidationErrorCollector errors) {
        if (type instanceof GraphQLInputObjectType) {
            checkInputFields((GraphQLInputObjectType) type, errors);
        }
        if (type instanceof GraphQLFieldsContainer) {
            checkFieldArguments((GraphQLFieldsContainer) type, errors);
        }
    }

    private void checkInputFields(GraphQLInputObjectType type, SchemaValidationErrorCollector errors) {
        for (GraphQLInputObjectField field : type.getFieldDefinitions()) {
            if (field.hasSetDefaultValue()) {
                checkDefault(field.getInputFieldDefaultValue(), field.getType(), type.getName() + "." + field.getName(), errors);
            }
        }
    }

    private void checkFieldArguments(GraphQLFieldsContainer type, SchemaValidationErrorCollector errors) {
        for (GraphQLFieldDefinition field : type.getFieldDefinitions()) {
            if (!field.getArguments().isEmpty()) {
                checkArguments(field.getArguments(), type.getName() + "." + field.getName(), errors);
            }
        }
    }

    private void checkArguments(List<GraphQLArgument> arguments, String coordinate, SchemaValidationErrorCollector errors) {
        for (GraphQLArgument argument : arguments) {
            if (argument.hasSetDefaultValue()) {
                checkDefault(argument.getArgumentDefaultValue(), argument.getType(), coordinate + "(" + argument.getName() + ":)", errors);
            }
        }
    }

    private void checkDefault(InputValueWithState value, GraphQLType type, String coordinate, SchemaValidationErrorCollector errors) {
        String error = checkValue(value.getValue(), type, value.isLiteral(), 0);
        if (error != null) {
            errors.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue,
                    "Invalid default value for " + coordinate + ": " + error));
        }
    }

    private @Nullable String checkValue(@Nullable Object value, GraphQLType type, boolean literal, int depth) {
        if (value == null || (literal && value instanceof NullValue)) {
            return null;
        }
        GraphQLType nullableType = unwrapNonNull(type);
        if (!(nullableType instanceof GraphQLList) && !(nullableType instanceof GraphQLInputObjectType)) {
            return null;
        }
        if (depth >= maxDepth) {
            return "Input value exceeds the maximum depth of " + maxDepth + ".";
        }
        if (nullableType instanceof GraphQLList) {
            return checkList(value, (GraphQLList) nullableType, literal, depth + 1);
        }
        if (literal && value instanceof ObjectValue) {
            return checkObjectLiteral((ObjectValue) value, (GraphQLInputObjectType) nullableType, depth + 1);
        }
        if (!literal && value instanceof Map) {
            return checkMap((Map<?, ?>) value, (GraphQLInputObjectType) nullableType, depth + 1);
        }
        return null;
    }

    private @Nullable String checkList(Object value, GraphQLList type, boolean literal, int depth) {
        if (!literal && FpKit.isIterable(value) && !FpKit.isCollectionOrArray(value)) {
            return "List defaults must use collections or arrays, not iterators, streams or other iterables.";
        }
        Iterable<?> values = listValues(value, literal);
        for (Object element : values) {
            String error = checkValue(element, type.getWrappedType(), literal, depth);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private Iterable<?> listValues(Object value, boolean literal) {
        if (literal && value instanceof ArrayValue) {
            return ((ArrayValue) value).getValues();
        }
        if (!literal && FpKit.isCollectionOrArray(value)) {
            return FpKit.toIterable(value);
        }
        return Collections.singletonList(value);
    }

    private @Nullable String checkObjectLiteral(ObjectValue value, GraphQLInputObjectType type, int depth) {
        for (ObjectField field : value.getObjectFields()) {
            String error = checkField(field.getValue(), type.getFieldDefinition(field.getName()), true, depth);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private @Nullable String checkMap(Map<?, ?> value, GraphQLInputObjectType type, int depth) {
        for (Map.Entry<?, ?> field : value.entrySet()) {
            GraphQLInputObjectField definition = field.getKey() instanceof String ? type.getFieldDefinition((String) field.getKey()) : null;
            String error = checkField(field.getValue(), definition, false, depth);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private @Nullable String checkField(@Nullable Object value, @Nullable GraphQLInputObjectField field, boolean literal, int depth) {
        if (field == null) {
            return null;
        }
        return checkValue(value, field.getType(), literal, depth);
    }
}
