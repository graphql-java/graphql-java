package graphql.execution;

import graphql.Internal;
import graphql.TypeResolutionEnvironment;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.DataFetchingFieldSelectionSetImpl;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.TypeResolver;

import java.util.function.Supplier;

@Internal
public class ResolveType {


    public GraphQLObjectType resolveType(ExecutionContext executionContext, MergedField field, Object source, ExecutionStepInfo executionStepInfo, GraphQLType fieldType) {
        GraphQLObjectType resolvedType;
        if (fieldType instanceof GraphQLInterfaceType) {
            DataFetchingFieldSelectionSet fieldSelectionSet = buildSelectionSet(executionContext, field, (GraphQLOutputType) fieldType, executionStepInfo);
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLInterfaceType((GraphQLInterfaceType) fieldType)
                    .field(field)
                    .value(source)
                    .argumentValues(executionStepInfo.getArguments())
                    .selectionSet(fieldSelectionSet)
                    .context(executionContext.getContext())
                    .graphQLContext(executionContext.getGraphQLContext())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);

        } else if (fieldType instanceof GraphQLUnionType) {
            DataFetchingFieldSelectionSet selectionSet = buildSelectionSet(executionContext, field, (GraphQLOutputType) fieldType, executionStepInfo);
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLUnionType((GraphQLUnionType) fieldType)
                    .field(field)
                    .value(source)
                    .argumentValues(executionStepInfo.getArguments())
                    .selectionSet(selectionSet)
                    .context(executionContext.getContext())
                    .graphQLContext(executionContext.getGraphQLContext())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForUnion(resolutionParams);
        } else {
            resolvedType = (GraphQLObjectType) fieldType;
        }
        return resolvedType;
    }

    private DataFetchingFieldSelectionSet buildSelectionSet(ExecutionContext executionContext, MergedField field, GraphQLOutputType fieldType, ExecutionStepInfo executionStepInfo) {
        Supplier<ExecutableNormalizedOperation> normalizedQuery = executionContext.getNormalizedQueryTree();
        Supplier<ExecutableNormalizedField> normalizedFieldSupplier = () -> normalizedQuery.get().getNormalizedField(field, executionStepInfo.getObjectType(), executionStepInfo.getPath());
        return DataFetchingFieldSelectionSetImpl.newCollector(executionContext.getGraphQLSchema(), fieldType, normalizedFieldSupplier);
    }

    public GraphQLObjectType resolveTypeForInterface(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLInterfaceType(), params.getSchema(), params.getContext(), params.getGraphQLContext(), params.getSelectionSet());
        GraphQLInterfaceType abstractType = params.getGraphQLInterfaceType();
        TypeResolver typeResolver = params.getSchema().getCodeRegistry().getTypeResolver(abstractType);
        return resolveAbstractType(params, env, typeResolver, abstractType);
    }

    public GraphQLObjectType resolveTypeForUnion(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLUnionType(), params.getSchema(), params.getContext(), params.getGraphQLContext(), params.getSelectionSet());
        GraphQLUnionType abstractType = params.getGraphQLUnionType();
        TypeResolver typeResolver = params.getSchema().getCodeRegistry().getTypeResolver(abstractType);
        return resolveAbstractType(params, env, typeResolver, abstractType);
    }

    private GraphQLObjectType resolveAbstractType(TypeResolutionParameters params, TypeResolutionEnvironment env, TypeResolver typeResolver, GraphQLNamedOutputType abstractType) {
        GraphQLObjectType result = typeResolver.getType(env);

        if (result == null) {
            throw new UnresolvedTypeException(abstractType);
        }

        if (!params.getSchema().isPossibleType(abstractType, result)) {
            throw new UnresolvedTypeException(abstractType, result);
        }

        return result;
    }

}
