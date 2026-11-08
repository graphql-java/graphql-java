package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.util.FpKit;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.BaseStream;

@Internal
public class DefaultValueContainersAreValid extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument argument, TraverserContext<GraphQLSchemaElement> context) {
        checkDefaultValue(argument.getArgumentDefaultValue(), argument.getName(), context);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField field, TraverserContext<GraphQLSchemaElement> context) {
        checkDefaultValue(field.getInputFieldDefaultValue(), field.getName(), context);
        return TraversalControl.CONTINUE;
    }

    private void checkDefaultValue(InputValueWithState defaultValue, String name, TraverserContext<GraphQLSchemaElement> context) {
        if (!defaultValue.isExternal() && !defaultValue.isInternal()) {
            return;
        }
        if (hasValidContainers(defaultValue.getValue(), null)) {
            return;
        }
        SchemaValidationErrorCollector collector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        collector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue,
                "Invalid default value for '" + name + "': default values must not contain an Iterator or a Stream"));
    }

    private boolean hasValidContainers(@Nullable Object value, @Nullable Set<Object> visited) {
        if (value == null) {
            return true;
        }
        if (value instanceof Iterator || value instanceof BaseStream) {
            return false;
        }
        if (!(value instanceof Map) && !FpKit.isIterableOrArray(value)) {
            return true;
        }
        if (visited == null) {
            visited = Collections.newSetFromMap(new IdentityHashMap<>());
        }
        if (!visited.add(value)) {
            return true;
        }
        Iterable<?> values = value instanceof Map ? ((Map<?, ?>) value).values() : FpKit.toIterable(value);
        for (Object item : values) {
            if (!hasValidContainers(item, visited)) {
                return false;
            }
        }
        return true;
    }
}
