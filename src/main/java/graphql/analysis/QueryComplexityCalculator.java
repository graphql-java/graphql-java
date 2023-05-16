package graphql.analysis;

import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static java.util.Optional.ofNullable;

public class QueryComplexityCalculator {
    private final FieldComplexityCalculator fieldComplexityCalculator;
    private final GraphQLSchema schema;
    private final Document document;
    private final String operationName;
    private final CoercedVariables variables;

    public QueryComplexityCalculator(FieldComplexityCalculator fieldComplexityCalculator, GraphQLSchema schema, Document document, String operationName, CoercedVariables variables) {
        this.fieldComplexityCalculator = assertNotNull(fieldComplexityCalculator, () -> "fieldComplexityCalculator can't be null");
        this.schema = assertNotNull(schema, () -> "schema can't be null");
        this.document = assertNotNull(document, () -> "document can't be null");
        this.operationName = assertNotNull(operationName, () -> "operationName can't be null");
        this.variables = assertNotNull(variables, () -> "variables can't be null");
    }

    int calculate() {
        QueryTraverser queryTraverser = QueryTraverser.newQueryTraverser()
                .schema(this.schema)
                .document(this.document)
                .operationName(this.operationName)
                .coercedVariables(this.variables)
                .build();


        Map<QueryVisitorFieldEnvironment, Integer> valuesByParent = new LinkedHashMap<>();
        queryTraverser.visitPostOrder(new QueryVisitorStub() {
            @Override
            public void visitField(QueryVisitorFieldEnvironment env) {
                int childComplexity = valuesByParent.getOrDefault(env, 0);
                int value = calculateComplexity(env, childComplexity);

                valuesByParent.compute(env.getParentEnvironment(), (key, oldValue) ->
                        ofNullable(oldValue).orElse(0) + value
                );
            }
        });

        return valuesByParent.getOrDefault(null, 0);
    }

    private int calculateComplexity(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment, int childComplexity) {
        if (queryVisitorFieldEnvironment.isTypeNameIntrospectionField()) {
            return 0;
        }
        FieldComplexityEnvironment fieldComplexityEnvironment = convertEnv(queryVisitorFieldEnvironment);
        return fieldComplexityCalculator.calculate(fieldComplexityEnvironment, childComplexity);
    }

    private FieldComplexityEnvironment convertEnv(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
        FieldComplexityEnvironment parentEnv = null;
        if (queryVisitorFieldEnvironment.getParentEnvironment() != null) {
            parentEnv = convertEnv(queryVisitorFieldEnvironment.getParentEnvironment());
        }
        return new FieldComplexityEnvironment(
                queryVisitorFieldEnvironment.getField(),
                queryVisitorFieldEnvironment.getFieldDefinition(),
                queryVisitorFieldEnvironment.getFieldsContainer(),
                queryVisitorFieldEnvironment.getArguments(),
                parentEnv
        );
    }
}
