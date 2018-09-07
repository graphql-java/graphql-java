package graphql.execution.streaming;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.AsyncSerialExecutionStrategy;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.FieldValueInfo;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.streaming.JsonStream;
import graphql.language.Field;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static graphql.Assert.assertNotNull;

public class AsyncStreamingExecutionStrategy extends AsyncSerialExecutionStrategy {

    private static final String STREAMING_STATE = "asyncStreamingExecutionStrategy.streamingState";

    static class StreamingState {
        final JsonStream jsonStream;
        final Map<ExecutionPath, Boolean> openedPaths = new HashMap<>();
        final Map<ExecutionPath, Boolean> visitedPaths = new HashMap<>();

        StreamingState(JsonStream jsonStream) {
            this.jsonStream = jsonStream;
        }

        boolean hasOpenedPath(ExecutionPath executionPath) {
            return openedPaths.containsKey(executionPath);
        }

        void openPath(ExecutionPath executionPath) {
            openedPaths.put(executionPath, true);
        }

        boolean hasVisitedPath(ExecutionPath executionPath) {
            return visitedPaths.containsKey(executionPath);
        }

        void visitedPath(ExecutionPath executionPath) {
            visitedPaths.put(executionPath, true);
        }

        public JsonStream out() {
            return jsonStream;
        }
    }


    private final Supplier<JsonStream> streamSupplier;

    public AsyncStreamingExecutionStrategy(Supplier<JsonStream> streamSupplier) {
        this.streamSupplier = streamSupplier;
    }

    private List<ExecutionTypeInfo> buildExecutionHierarchy(ExecutionTypeInfo typeInf) {
        List<ExecutionTypeInfo> types = new ArrayList<>();
        while (typeInf.hasParentType()) {
            typeInf = typeInf.getParentTypeInfo();
            types.add(typeInf);
        }
        // the execution is a stack - so reverse the list to start from the top down
        Collections.reverse(types);
        return types;
    }

    private StreamingState state(ExecutionContext executionContext) {
        return (StreamingState) executionContext.getStrategyContext().get(STREAMING_STATE);
    }

    private boolean areAllSiblingsHandled(StreamingState state, ExecutionStrategyParameters parameters) {
        Map<String, List<Field>> fields = parameters.getFields();
        ExecutionPath path = parameters.getTypeInfo().getPath();
        //
        // have we handled all the sibling fields
        boolean allSiblingsHandled = true;
        for (List<Field> field : fields.values()) {
            ExecutionPath siblingPath = path.sibling(ExecutionStrategy.mkNameForPath(field));
            if (!state.hasVisitedPath(siblingPath)) {
                allSiblingsHandled = false;
                break;
            }
        }
        return allSiblingsHandled;
    }


    private void printOpeningTokensToLeaf(ExecutionTypeInfo executionTypeInfo, StreamingState state) {
        JsonStream out = state.out();

        List<ExecutionTypeInfo> typeHierarchy = buildExecutionHierarchy(executionTypeInfo);
        for (ExecutionTypeInfo typeInfo : typeHierarchy) {
            ExecutionPath path = typeInfo.getPath();
            if (state.hasOpenedPath(path)) {
                continue;
            }
            state.openPath(path);
            GraphQLType unwrappedType = GraphQLTypeUtil.unwrapAll(typeInfo.getType());

            if (!typeInfo.hasParentType()) {
                // opening query type so opening json object
                out.writeStartObject();
            } else {
                String fieldName = ExecutionStrategy.mkNameForPath(typeInfo.getField());
                if (typeInfo.isListType()) {
                    out.writeArrayFieldStart(fieldName);
                } else if (unwrappedType instanceof GraphQLCompositeType) {
                    // if the parent type is a list then we want to an object without a name
                    if (typeInfo.hasParentType() && typeInfo.getParentTypeInfo().isListType()) {
                        out.writeStartObject();
                    } else {
                        out.writeObjectFieldStart(fieldName);
                    }
                }
            }
        }
    }


    @Override
    protected FieldValueInfo completeValue(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        StreamingState state = state(executionContext);
        FieldValueInfo fieldValueInfo = super.completeValue(executionContext, parameters);
        ExecutionTypeInfo typeInfo = parameters.getTypeInfo();
        ExecutionPath path = typeInfo.getPath();
        String fieldName = mkNameForPath(typeInfo.getField());

        fieldValueInfo.getFieldValue().thenAccept(executionResult -> {
            if (GraphQLTypeUtil.isLeaf(typeInfo.getType())) {
                // lookup and see if we are in a list or object etc.. so we can start opening braces
                printOpeningTokensToLeaf(typeInfo, state);

                Object fieldValue = executionResult.getData();
                state.out().writeJavaObject(fieldName, fieldValue);
            }
            state.visitedPath(path);
            boolean allSiblingsHandled = areAllSiblingsHandled(state, parameters);
            if (allSiblingsHandled) {
                state.out().writeEndObject();
            }
        });
        return fieldValueInfo;
    }

    @Override
    protected FieldValueInfo completeValueForList(ExecutionContext executionContext, ExecutionStrategyParameters parameters, Object result) {
        FieldValueInfo fieldValueInfo = super.completeValueForList(executionContext, parameters, result);
        ExecutionTypeInfo typeInfo = parameters.getTypeInfo();
        fieldValueInfo.getFieldValue().thenAccept(executionResult -> {
            boolean isObjectType = !GraphQLTypeUtil.isLeaf(typeInfo.getType());
            if (typeInfo.isListType() && isObjectType) {
                state(executionContext).out().writeEndArray();
            }
        });
        return fieldValueInfo;
    }

    @Override
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        ExecutionPath path = parameters.getPath();
        if (path.equals(ExecutionPath.rootPath())) {
            StreamingState streamingState = new StreamingState(streamSupplier.get());
            executionContext.getStrategyContext().put(STREAMING_STATE, assertNotNull(streamingState));
        }
        CompletableFuture<ExecutionResult> result = super.execute(executionContext, parameters);
        return result.thenApply(er -> {
            if (clearExecutionData()) {
                return new ExecutionResultImpl(null, er.getErrors(), er.getExtensions());
            }
            return er;
        });
    }

    // for testing
    boolean clearExecutionData() {
        return false;
    }
}
