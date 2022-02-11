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


    public GraphQLObjectType resolveType(ExecutionContext executionContext, MergedField field, Object source, ExecutionStepInfo executionStepInfo, GraphQLType fieldType, Object localContext) {
        GraphQLObjectType resolvedType;
        DataFetchingFieldSelectionSet fieldSelectionSet = buildSelectionSet(executionContext, field, (GraphQLOutputType) fieldType, executionStepInfo);
        TypeResolutionEnvironment env = TypeResolutionParameters.newParameters()
                .field(field)
                .fieldType(fieldType)
                .value(source)
                .argumentValues(executionStepInfo.getArguments())
                .selectionSet(fieldSelectionSet)
                .context(executionContext.getContext())
                .graphQLContext(executionContext.getGraphQLContext())
                .localContext(localContext)
                .schema(executionContext.getGraphQLSchema())
                .build();
        if (fieldType instanceof GraphQLInterfaceType) {
            resolvedType = resolveTypeForInterface(env, (GraphQLInterfaceType) fieldType);
        } else if (fieldType instanceof GraphQLUnionType) {
            resolvedType = resolveTypeForUnion(env, (GraphQLUnionType) fieldType);
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

    public GraphQLObjectType resolveTypeForInterface(TypeResolutionEnvironment env, GraphQLInterfaceType abstractType) {
        TypeResolver typeResolver = env.getSchema().getCodeRegistry().getTypeResolver(abstractType);
        return resolveAbstractType(env, typeResolver, abstractType);
    }

    public GraphQLObjectType resolveTypeForUnion(TypeResolutionEnvironment env, GraphQLUnionType abstractType) {
        TypeResolver typeResolver = env.getSchema().getCodeRegistry().getTypeResolver(abstractType);
        return resolveAbstractType(env, typeResolver, abstractType);
    }

    private GraphQLObjectType resolveAbstractType(TypeResolutionEnvironment env, TypeResolver typeResolver, GraphQLNamedOutputType abstractType) {
        GraphQLObjectType result = typeResolver.getType(env);
        if (result == null) {
            throw new UnresolvedTypeException(abstractType);
        }

        if (!env.getSchema().isPossibleType(abstractType, result)) {
            throw new UnresolvedTypeException(abstractType, result);
        }

        return result;
    }

}
