package graphql.analysis.usage;

import graphql.PublicApi;
import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.normalized.ExecutableNormalizedField;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedOutputType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;

import java.util.List;

/**
 * This class can give you the types in the schema that are used by a
 * given query.  This may be useful to try and track what types are being
 * used in your schema.
 */
@PublicApi
public class QueryTypeUsageSupport {

    public static QueryTypeUsage getQueryTypeUsage(
            GraphQLSchema graphQLSchema,
            Document document,
            String operationName,
            CoercedVariables coercedVariableValues
    ) {
        ExecutableNormalizedOperation normalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(
                graphQLSchema,
                document,
                operationName,
                coercedVariableValues
        );
        return getQueryTypeUsage(graphQLSchema, normalizedOperation);
    }

    public static QueryTypeUsage getQueryTypeUsage(
            GraphQLSchema graphQLSchema,
            ExecutableNormalizedOperation normalizedOperation
    ) {
        QueryTypeUsage queryTypeUsage = new QueryTypeUsage(graphQLSchema);
        recordFields(graphQLSchema, queryTypeUsage, normalizedOperation.getTopLevelFields());
        return queryTypeUsage;
    }

    private static void recordFields(GraphQLSchema graphQLSchema,
                                     QueryTypeUsage typeUsage,
                                     List<ExecutableNormalizedField> enfs) {
        for (ExecutableNormalizedField enf : enfs) {
            recordField(graphQLSchema, typeUsage, enf);
            List<ExecutableNormalizedField> children = enf.getChildren();
            recordFields(graphQLSchema, typeUsage, children);
        }
    }

    private static void recordField(GraphQLSchema graphQLSchema, QueryTypeUsage typeUsage, ExecutableNormalizedField enf) {
        List<GraphQLFieldDefinition> fieldDefinitions = enf.getFieldDefinitions(graphQLSchema);
        for (GraphQLFieldDefinition fieldDefinition : fieldDefinitions) {
            GraphQLOutputType fieldType = fieldDefinition.getType();
            if (!isTypeNameIntrospectionField(graphQLSchema, fieldDefinition)) {
                for (GraphQLObjectType objectType : enf.getObjectTypes(graphQLSchema)) {
                    typeUsage.addOutputReference(objectType);
                }
            }
            typeUsage.addOutputReference(fieldType);
            if (fieldType instanceof GraphQLInterfaceType) {
                // the interface
                breakDownInterfaces(typeUsage, (GraphQLInterfaceType) fieldType, graphQLSchema);
            }
            if (fieldType instanceof GraphQLUnionType) {
                breakDownUnions(typeUsage, (GraphQLUnionType) fieldType, graphQLSchema);
            }
        }

    }

    private static void breakDownInterfaces(QueryTypeUsage queryTypeUsage, GraphQLInterfaceType interfaceType, GraphQLSchema graphQLSchema) {
        List<GraphQLObjectType> implementations = graphQLSchema.getImplementations(interfaceType);
        implementations.forEach(queryTypeUsage::addOutputReference);
        List<GraphQLNamedOutputType> interfaces = interfaceType.getInterfaces();
        interfaces.forEach(iType ->
                breakDownInterfaces(queryTypeUsage, (GraphQLInterfaceType) iType, graphQLSchema));
    }

    private static void breakDownUnions(QueryTypeUsage queryTypeUsage, GraphQLUnionType unionType, GraphQLSchema graphQLSchema) {
        List<GraphQLNamedOutputType> types = unionType.getTypes();
        types.forEach(namedOutputType ->
                queryTypeUsage.addOutputReference(graphQLSchema.getTypeAs(namedOutputType.getName())));
    }

    private static boolean isTypeNameIntrospectionField(GraphQLSchema graphQLSchema, GraphQLFieldDefinition fieldDefinition) {
        return graphQLSchema.getIntrospectionTypenameFieldDefinition().getName().equals(fieldDefinition.getName());
    }
}
