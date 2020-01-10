package graphql.analysis.qexectree;

import graphql.PublicApi;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.List;

@PublicApi
public class QueryExecutionTreeAnalyzer {

    /**
     * Creates a new Query execution tree for the provided query
     *
     * @param graphQLSchema
     * @param operationDefinition
     *
     * @return
     */
    public QueryExecutionTree createExecutionTree(GraphQLSchema graphQLSchema, OperationDefinition operationDefinition) {

        FieldCollectorQueryExecution fieldCollector = new FieldCollectorQueryExecution();
        FieldCollectorQueryExecutionParams parameters = FieldCollectorQueryExecutionParams
                .newParameters()
                .schema(graphQLSchema)
                .build();

        List<QueryExecutionField> roots = fieldCollector.collectFromOperation(parameters, operationDefinition, graphQLSchema.getQueryType());

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
