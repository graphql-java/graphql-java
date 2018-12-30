package graphql.execution.nextgen;

import graphql.Internal;
import graphql.SerializationError;
import graphql.TypeMismatchError;
import graphql.UnresolvedTypeError;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ExecutionStepInfoFactory;
import graphql.execution.MergedField;
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

import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.ENUM;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.LIST;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.OBJECT;
import static graphql.execution.nextgen.FetchedValueAnalysis.FetchedValueType.SCALAR;
import static graphql.execution.nextgen.FetchedValueAnalysis.newFetchedValueAnalysis;
import static graphql.schema.GraphQLTypeUtil.isList;

@Internal
public class FetchedValueAnalyzer {

    ExecutionStepInfoFactory executionInfoFactory = new ExecutionStepInfoFactory();
    ResolveType resolveType = new ResolveType();


    private static final Logger log = LoggerFactory.getLogger(FetchedValueAnalyzer.class);


    /*
     * scalar: the value, null and/or error
     * enum: same as scalar
     * list: list of X: X can be list again, list of scalars or enum or objects
     */
    public FetchedValueAnalysis analyzeFetchedValue(ExecutionContext executionContext, FetchedValue fetchedValue, ExecutionStepInfo executionInfo) throws NonNullableFieldWasNullException {
        return analyzeFetchedValueImpl(executionContext, fetchedValue, fetchedValue.getFetchedValue(), executionInfo);
    }

    private FetchedValueAnalysis analyzeFetchedValueImpl(ExecutionContext executionContext, FetchedValue fetchedValue, Object toAnalyze, ExecutionStepInfo executionInfo) throws NonNullableFieldWasNullException {
        GraphQLType fieldType = executionInfo.getUnwrappedNonNullType();
        MergedField field = executionInfo.getField();
        String name = field.getResultKey();

        if (isList(fieldType)) {
            return analyzeList(executionContext, fetchedValue, toAnalyze, name, executionInfo);
        } else if (fieldType instanceof GraphQLScalarType) {
            return analyzeScalarValue(fetchedValue, toAnalyze, name, (GraphQLScalarType) fieldType, executionInfo);
        } else if (fieldType instanceof GraphQLEnumType) {
            return analyzeEnumValue(fetchedValue, toAnalyze, name, (GraphQLEnumType) fieldType, executionInfo);
        }

        // when we are here, we have a complex type: Interface, Union or Object
        // and we must go deeper
        //
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(OBJECT)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .field(field)
                    .nullValue()
                    .build();
        }
        try {
            GraphQLObjectType resolvedObjectType = resolveType.resolveType(executionContext, field, toAnalyze, executionInfo.getArguments(), fieldType);
            FetchedValueAnalysis result = newFetchedValueAnalysis(OBJECT)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .field(field)
                    .completedValue(toAnalyze)
                    .resolvedType(resolvedObjectType)
                    .build();
            return result;
        } catch (UnresolvedTypeException ex) {
            return handleUnresolvedTypeProblem(fetchedValue, executionInfo, ex);
        }
    }


    private FetchedValueAnalysis handleUnresolvedTypeProblem(FetchedValue fetchedValue, ExecutionStepInfo executionInfo, UnresolvedTypeException e) {
        UnresolvedTypeError error = new UnresolvedTypeError(executionInfo.getPath(), executionInfo, e);
        return newFetchedValueAnalysis(OBJECT)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .nullValue()
                .error(error)
                .build();
    }

    private FetchedValueAnalysis analyzeList(ExecutionContext executionContext, FetchedValue fetchedValue, Object toAnalyze, String name, ExecutionStepInfo executionInfo) {
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(LIST)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .nullValue()
                    .build();
        }

        if (toAnalyze.getClass().isArray() || toAnalyze instanceof Iterable) {
            Collection<Object> collection = FpKit.toCollection(toAnalyze);
            return analyzeIterable(executionContext, fetchedValue, collection, name, executionInfo);
        } else {
            TypeMismatchError error = new TypeMismatchError(executionInfo.getPath(), executionInfo.getType());
            return newFetchedValueAnalysis(LIST)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .nullValue()
                    .error(error)
                    .build();
        }

    }


    private FetchedValueAnalysis analyzeIterable(ExecutionContext executionContext, FetchedValue fetchedValue, Iterable<Object> iterableValues, String name, ExecutionStepInfo executionInfo) {

        Collection<Object> values = FpKit.toCollection(iterableValues);
        List<FetchedValueAnalysis> children = new ArrayList<>();
        int index = 0;
        for (Object item : values) {
            ExecutionStepInfo executionInfoForListElement = executionInfoFactory.newExecutionStepInfoForListElement(executionInfo, index);
            children.add(analyzeFetchedValueImpl(executionContext, fetchedValue, item, executionInfoForListElement));
            index++;
        }
        return newFetchedValueAnalysis(LIST)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .children(children)
                .build();

    }


    private FetchedValueAnalysis analyzeScalarValue(FetchedValue fetchedValue, Object toAnalyze, String name, GraphQLScalarType scalarType, ExecutionStepInfo executionInfo) {
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
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
                    .nullValue()
                    .build();
        }

        // TODO: fix that: this should not be handled here
        //6.6.1 http://facebook.github.io/graphql/#sec-Field-entries
        if (serialized instanceof Double && ((Double) serialized).isNaN()) {
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .nullValue()
                    .build();
        }
        // handle non null

        return newFetchedValueAnalysis(SCALAR)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .completedValue(serialized)
                .build();
    }

    private FetchedValueAnalysis analyzeEnumValue(FetchedValue fetchedValue, Object toAnalyze, String name, GraphQLEnumType enumType, ExecutionStepInfo executionInfo) {
        if (toAnalyze == null) {
            return newFetchedValueAnalysis(SCALAR)
                    .fetchedValue(fetchedValue)
                    .executionStepInfo(executionInfo)
                    .nullValue()
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
                    .build();
        }
        // handle non null values
        return newFetchedValueAnalysis(ENUM)
                .fetchedValue(fetchedValue)
                .executionStepInfo(executionInfo)
                .completedValue(serialized)
                .build();
    }

}
