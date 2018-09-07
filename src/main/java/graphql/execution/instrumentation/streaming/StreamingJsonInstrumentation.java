package graphql.execution.instrumentation.streaming;

import graphql.ExecutionResult;
import graphql.execution.ExecutionPath;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionTypeInfo;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldCompleteParameters;
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
import java.util.function.Supplier;

public class StreamingJsonInstrumentation extends SimpleInstrumentation {

    private final Supplier<JsonStream> generatorSupplier;

    static class StreamingInstrumentationState implements InstrumentationState {
        final JsonStream jsonStream;
        Map<ExecutionPath, Boolean> openedPaths = new HashMap<>();
        Map<ExecutionPath, Boolean> visitedPaths = new HashMap<>();

        StreamingInstrumentationState(JsonStream jsonStream) {
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

    public StreamingJsonInstrumentation(Supplier<JsonStream> generatorSupplier) {
        this.generatorSupplier = generatorSupplier;
    }

    @Override
    public InstrumentationState createState() {
        return new StreamingInstrumentationState(generatorSupplier.get());
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

    private boolean areAllSiblingsHandled(StreamingInstrumentationState state, InstrumentationFieldCompleteParameters parameters) {
        Map<String, List<Field>> fields = parameters.getExecutionStrategyParameters().getFields();
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


    private void printOpeningTokensToLeaf(ExecutionTypeInfo executionTypeInfo, StreamingInstrumentationState state) {
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
    public InstrumentationContext<ExecutionResult> beginFieldComplete(InstrumentationFieldCompleteParameters parameters) {
        StreamingInstrumentationState state = parameters.getInstrumentationState();
        ExecutionTypeInfo typeInfo = parameters.getTypeInfo();
        ExecutionPath path = typeInfo.getPath();
        String fieldName = ExecutionStrategy.mkNameForPath(parameters.getExecutionStrategyParameters().getField());

        return new SimpleInstrumentationContext<ExecutionResult>() {

            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {

                JsonStream out = state.out();

                if (GraphQLTypeUtil.isLeaf(typeInfo.getType())) {
                    // lookup and see if we are in a list or object etc.. so we can start opening braces
                    printOpeningTokensToLeaf(typeInfo, state);

                    Object fieldValue = result.getData();
                    out.writeJavaObject(fieldName, fieldValue);
                }
                state.visitedPath(path);
                boolean allSiblingsHandled = areAllSiblingsHandled(state, parameters);
                if (allSiblingsHandled) {
                    out.writeEndObject();
                }
            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginFieldListComplete(InstrumentationFieldCompleteParameters parameters) {
        StreamingInstrumentationState state = parameters.getInstrumentationState();
        ExecutionTypeInfo typeInfo = parameters.getTypeInfo();
        return new SimpleInstrumentationContext<ExecutionResult>() {
            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
                boolean isObjectType = !GraphQLTypeUtil.isLeaf(typeInfo.getType());
                if (typeInfo.isListType() && isObjectType) {
                    state.out().writeEndArray();
                }
            }
        };
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
        StreamingInstrumentationState state = parameters.getInstrumentationState();
        return new SimpleInstrumentationContext<ExecutionResult>() {
            @Override
            public void onCompleted(ExecutionResult result, Throwable t) {
                JsonStream out = state.out();

                Map<String, Object> specResult = result.toSpecification();
                Object errors = specResult.get("errors");
                if (errors != null) {
                    out.writeJavaObject("errors", errors);
                }
                Object extensions = specResult.get("extensions");
                if (extensions != null) {
                    out.writeJavaObject("extensions", extensions);
                }
                out.finished();
            }
        };
    }
}
