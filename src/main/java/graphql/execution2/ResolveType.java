package graphql.execution2;

import graphql.TypeResolutionEnvironment;
import graphql.execution.ExecutionContext;
import graphql.execution.TypeResolutionParameters;
import graphql.execution.UnresolvedTypeException;
import graphql.language.Field;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;

import java.util.Map;

public class ResolveType {

    private final ExecutionContext executionContext;

    public ResolveType(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public GraphQLObjectType resolveType(Field field, Object source, Map<String, Object> arguments, GraphQLType fieldType) {
        GraphQLObjectType resolvedType;
        if (fieldType instanceof GraphQLInterfaceType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLInterfaceType((GraphQLInterfaceType) fieldType)
                    .field(field)
                    .value(source)
                    .argumentValues(arguments)
                    .context(executionContext.getContext())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForInterface(resolutionParams);

        } else if (fieldType instanceof GraphQLUnionType) {
            TypeResolutionParameters resolutionParams = TypeResolutionParameters.newParameters()
                    .graphQLUnionType((GraphQLUnionType) fieldType)
                    .field(field)
                    .value(source)
                    .argumentValues(arguments)
                    .context(executionContext.getContext())
                    .schema(executionContext.getGraphQLSchema()).build();
            resolvedType = resolveTypeForUnion(resolutionParams);
        } else {
            resolvedType = (GraphQLObjectType) fieldType;
        }

        return resolvedType;
    }

    public GraphQLObjectType resolveTypeForInterface(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLInterfaceType(), params.getSchema(), params.getContext());
        GraphQLInterfaceType abstractType = params.getGraphQLInterfaceType();
        GraphQLObjectType result = abstractType.getTypeResolver().getType(env);
        if (result == null) {
            throw new UnresolvedTypeException(abstractType);
        }

        if (!params.getSchema().isPossibleType(abstractType, result)) {
            throw new UnresolvedTypeException(abstractType, result);
        }

        return result;
    }

    public GraphQLObjectType resolveTypeForUnion(TypeResolutionParameters params) {
        TypeResolutionEnvironment env = new TypeResolutionEnvironment(params.getValue(), params.getArgumentValues(), params.getField(), params.getGraphQLUnionType(), params.getSchema(), params.getContext());
        GraphQLUnionType abstractType = params.getGraphQLUnionType();
        GraphQLObjectType result = abstractType.getTypeResolver().getType(env);
        if (result == null) {
            throw new UnresolvedTypeException(abstractType);
        }

        if (!params.getSchema().isPossibleType(abstractType, result)) {
            throw new UnresolvedTypeException(abstractType, result);
        }

        return result;
    }

}
