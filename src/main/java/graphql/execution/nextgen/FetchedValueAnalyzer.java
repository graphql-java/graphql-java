package graphql.execution.nextgen;

import graphql.Internal;
import graphql.SerializationError;
import graphql.TypeMismatchError;
import graphql.UnresolvedTypeError;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.FieldCollector;
import graphql.execution.FieldCollectorParameters;
import graphql.execution.MergedFields;
import graphql.execution.NonNullableFieldWasNullException;
import graphql.execution.ResolveType;
import graphql.execution.UnresolvedTypeException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.util.FpKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static graphql.execution.FieldCollectorParameters.newParameters;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.ENUM;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.LIST;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.OBJECT;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR;
import static graphql.execution.nextgen.FetchedValueAnalysis.newFetchedValueAnalysis;
import static graphql.schema.GraphQLTypeUtil.isList;

@Internal
public class FetchedValueAnalyzer {

    private final ExecutionContext executionContext;
    ResolveType resolveType;
    FieldCollector fieldCollector = new FieldCollector();
    ExecutionStepInfoFactory executionInfoFactory;


    public FetchedValueAnalyzer(ExecutionContext executionContext) {
        this.executionContext = executionContext;
        this.resolveType = new ResolveType();
        this.executionInfoFactory = new ExecutionStepInfoFactory();
    }

    private static final Logger log = LoggerFactory.getLogger(FetchedValueAnalyzer.class);


    /*
     * scalar: the value, null and/or error
     * enum: same as scalar
     * list: list of X: X can be list again, list of scalars or enum or objects
     */
    public FetchedValueAnalysis analyzeFetchedValue(FetchedValue fetchedValue, String name, MergedFields field, ExecutionStepInfo executionInfo) throws NonNullableFieldWasNullException {
        return analyzeFetchedValueImpl(fetchedValue, fetchedValue.getFetchedValue(), name, field, executionInfo);
    }

    private FetchedValueAnalysis analyzeFetchedValueImpl(FetchedValue fetchedValue, Object toAnalyze, String name, MergedFields field, ExecutionStepInfo executionInfo) throws NonNullableFieldWasNullException {
        GraphQLType fieldType = executionInfo.getUnwrappedNonNullType();

        if (isList(fieldType)) {
            return analyzeList(fetchedValue, toAnalyze, name, executionInfo);
        } else if (fieldType instanceof GraphQLScalarType) {
            return analyzeScalarValue(fetchedValue, toAnalyze, name, (GraphQLScalarType) fieldType, executionInfo);
        } else if (fieldType instanceof GraphQLEnumType) {
            return analyzeEnumValue(fetchedValue, toAnalyze, name, (GraphQLEnumType) fieldType, executionInfo);
        }

        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper
        //
        GraphQLObjectType resolvedObjectType;
        try {
            if (toAnalyze == null) {
                return newFetchedValueAnalysis(OBJECT)
                        .fetchedValue(fetchedValue)
                        .executionStepInfo(executionInfo)
                        .name(name)
                        .nullValue()
                        .build();
            }
            resolvedObjectType = resolveType.resolveType(executionContext, field, toAnalyze, executionInfo.getArguments(), fieldType);
            return analyzeObject(fetchedValue, toAnalyze, name, resolvedObjectType, executionInfo);
        } catch (UnresolvedTypeException ex) {
            return handleUnresolvedTypeProblem(fetchedValue, name, executionInfo, ex);
        }
    }


    private FetchedValueAnalysis handleUnresolvedTypeProblem(FetchedValue fetchedValue, String name, ExecutionStepInfo executionInfo, UnresolvedTypeException e) {
        UnresolvedTypeError error = new UnresolvedTypeError(executionInfo.getPath(), executionInfo, e);
        return newFetchedValueAnalysis(OBJECT)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .name(name)
                .nullValue()
                .error(error)
                .build();
    }

    private FetchedValueAnalysis analyzeList(FetchedValue fetchedValue, Object toAnalyze, String name, ExecutionStepInfo executionInfo) {
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(LIST)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .name(name)
                    .nullValue()
                    .build();
        }

        if (toAnalyze.getClass().isArray() || toAnalyze instanceof Iterable) {
            Collection<Object> collection = FpKit.toCollection(toAnalyze);
            return analyzeIterable(fetchedValue, collection, name, executionInfo);
        } else {
            TypeMismatchError error = new TypeMismatchError(executionInfo.getPath(), executionInfo.getType());
            return newFetchedValueAnalysis(LIST)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .name(name)
                    .nullValue()
                    .error(error)
                    .build();
        }

    }


    private FetchedValueAnalysis analyzeIterable(FetchedValue fetchedValue, Iterable<Object> iterableValues, String name, ExecutionStepInfo executionInfo) {

        Collection<Object> values = FpKit.toCollection(iterableValues);
        List<FetchedValueAnalysis> children = new ArrayList<>();
        int index = 0;
        for (Object item : values) {
            ExecutionStepInfo executionInfoForListElement = executionInfoFactory.newExecutionStepInfoForListElement(executionInfo, index);
            children.add(analyzeFetchedValueImpl(fetchedValue, item, name, executionInfo.getField(), executionInfoForListElement));
            index++;
        }
        return newFetchedValueAnalysis(LIST)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .name(name)
                .children(children)
                .build();

    }


    private FetchedValueAnalysis analyzeScalarValue(FetchedValue fetchedValue, Object toAnalyze, String name, GraphQLScalarType scalarType, ExecutionStepInfo executionInfo) {
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .name(name)
                    .nullValue()
                    .build();
        }
        Object serialized;
        try {
            serialized = scalarType.getCoercing().serialize(toAnalyze);
        } catch (CoercingSerializeException e) {
            SerializationError error = new SerializationError(executionInfo.getPath(), e);
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .error(error)
                    .name(name)
                    .nullValue()
                    .build();
        }

        // TODO: fix that: this should not be handled here
        //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
        if (serialized instanceof Double && ((Double) serialized).isNaN()) {
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .name(name)
                    .nullValue()
                    .build();
        }
        // handle non null

        return newFetchedValueAnalysis(SCALAR)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .completedValue(serialized)
                .name(name)
                .build();
    }

    private FetchedValueAnalysis analyzeEnumValue(FetchedValue fetchedValue, Object toAnalyze, String name, GraphQLEnumType enumType, ExecutionStepInfo executionInfo) {
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .nullValue()
                    .name(name)
                    .build();

        }
        Object serialized;
        try {
            serialized = enumType.getCoercing().serialize(toAnalyze);
        } catch (CoercingSerializeException e) {
            SerializationError error = new SerializationError(executionInfo.getPath(), e);
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .nullValue()
                    .error(error)
                    .name(name)
                    .build();
        }
        // handle non null values
        return newFetchedValueAnalysis(ENUM)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .name(name)
                .completedValue(serialized)
                .build();
    }

    private FetchedValueAnalysis analyzeObject(FetchedValue fetchedValue, Object toAnalyze, String name, GraphQLObjectType resolvedObjectType, ExecutionStepInfo executionInfo) {

        FieldCollectorParameters collectorParameters = newParameters()
                .schema(executionContext.getGraphQLSchema())
                .objectType(resolvedObjectType)
                .fragments(executionContext.getFragmentsByName())
                .variables(executionContext.getVariables())
                .build();
        Map<String, MergedFields> subFields = fieldCollector.collectFields(collectorParameters,
                executionInfo.getField());

        // it is not really a new step but rather a refinement
        ExecutionStepInfo newExecutionStepInfoWithResolvedType = executionInfo.changeTypeWithPreservedNonNull(resolvedObjectType);

        FieldSubSelection fieldSubSelection = new FieldSubSelection();
        fieldSubSelection.setSource(toAnalyze);
        fieldSubSelection.setExecutionStepInfo(newExecutionStepInfoWithResolvedType);
        fieldSubSelection.setFields(subFields);


        FetchedValueAnalysis result = newFetchedValueAnalysis(OBJECT)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .name(name)
                .completedValue(toAnalyze)
                .fieldSubSelection(fieldSubSelection)
                .build();
        return result;
    }
}
