package graphql.analysis.qexectree;

import graphql.PublicApi;
import graphql.language.Document;
import graphql.language.NodeUtil;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@PublicApi
public class QueryExecutionTreeAnalyzer {

    /**
     * Creates a new Query execution tree for the provided query
     */
    public QueryExecutionTree createExecutionTree(GraphQLSchema graphQLSchema, Document document, String operationName, Map<String, Object> variables) {

        NodeUtil.GetOperationResult getOperationResult = NodeUtil.getOperation(document, operationName);

        FieldCollectorQueryExecution fieldCollector = new FieldCollectorQueryExecution();
        FieldCollectorQueryExecutionParams parameters = FieldCollectorQueryExecutionParams
                .newParameters()
                .fragments(getOperationResult.fragmentsByName)
                .schema(graphQLSchema)
                .variables(variables)
                .build();

        List<QueryExecutionField> roots = fieldCollector.collectFromOperation(parameters, getOperationResult.operationDefinition, graphQLSchema.getQueryType());

        List<QueryExecutionField> realRoots = new ArrayList<>();
        for (QueryExecutionField root : roots) {
            realRoots.add(buildFieldWithChildren(root, fieldCollector, parameters));
        }

        return new QueryExecutionTree(realRoots);
    }


    private QueryExecutionField buildFieldWithChildren(QueryExecutionField field, FieldCollectorQueryExecution fieldCollector, FieldCollectorQueryExecutionParams fieldCollectorQueryExecutionParams) {
        List<QueryExecutionField> fieldsWithoutChildren = fieldCollector.collectFields(fieldCollectorQueryExecutionParams, field);
        List<QueryExecutionField> realChildren = new ArrayList<>();
        for (QueryExecutionField fieldWithoutChildren : fieldsWithoutChildren) {
            QueryExecutionField realChild = buildFieldWithChildren(fieldWithoutChildren, fieldCollector, fieldCollectorQueryExecutionParams);
            realChildren.add(realChild);
        }
        return field.transform(builder -> builder.children(realChildren));
    }
}
