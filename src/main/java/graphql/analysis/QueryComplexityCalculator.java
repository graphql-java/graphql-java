package graphql.analysis;

import graphql.PublicApi;
import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static graphql.Assert.assertNotNull;
import static java.util.Optional.ofNullable;

/**
 * This can calculate the complexity of an operation using the specified {@link FieldComplexityCalculator} you pass
 * into it.
 */
@PublicApi
@NullMarked
public class QueryComplexityCalculator {

    private final FieldComplexityCalculator fieldComplexityCalculator;
    private final GraphQLSchema schema;
    private final Document document;
    private final @Nullable String operationName;
    private final CoercedVariables variables;

    public QueryComplexityCalculator(Builder builder) {
        this.fieldComplexityCalculator = assertNotNull(builder.fieldComplexityCalculator, "fieldComplexityCalculator can't be null");
        this.schema = assertNotNull(builder.schema, "schema can't be null");
        this.document = assertNotNull(builder.document, "document can't be null");
        this.variables = assertNotNull(builder.variables, "variables can't be null");
        this.operationName = builder.operationName;
    }


    public int calculate() {
        Map<QueryVisitorFieldEnvironment, Integer> valuesByParent = calculateByParents();
        return valuesByParent.getOrDefault(null, 0);
    }

    /**
     * @return a map that shows the field complexity for each field level in the operation
     */
    public Map<QueryVisitorFieldEnvironment, Integer> calculateByParents() {
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

                QueryVisitorFieldEnvironment parentEnvironment = env.getParentEnvironment();
                valuesByParent.compute(parentEnvironment, (key, oldValue) -> {
                            Integer currentValue = ofNullable(oldValue).orElse(0);
                            return currentValue + value;
                        }
                );
            }
        });

        return valuesByParent;
    }

    private int calculateComplexity(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment, int childComplexity) {
        if (queryVisitorFieldEnvironment.isTypeNameIntrospectionField()) {
            return 0;
        }
        FieldComplexityEnvironment fieldComplexityEnvironment = convertEnv(queryVisitorFieldEnvironment);
        return fieldComplexityCalculator.calculate(fieldComplexityEnvironment, childComplexity);
    }

    private FieldComplexityEnvironment convertEnv(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {
        @Nullable FieldComplexityEnvironment parentEnv = null;
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

    public static Builder newCalculator() {
        return new Builder();
    }

    @NullUnmarked
    public static class Builder {
        private FieldComplexityCalculator fieldComplexityCalculator;
        private GraphQLSchema schema;
        private Document document;
        private String operationName;
        private CoercedVariables variables = CoercedVariables.emptyVariables();

        public Builder schema(GraphQLSchema graphQLSchema) {
            this.schema = graphQLSchema;
            return this;
        }

        public Builder fieldComplexityCalculator(FieldComplexityCalculator complexityCalculator) {
            this.fieldComplexityCalculator = complexityCalculator;
            return this;
        }

        public Builder document(Document document) {
            this.document = document;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public Builder variables(CoercedVariables variables) {
            this.variables = variables;
            return this;
        }

        public QueryComplexityCalculator build() {
            return new QueryComplexityCalculator(this);
        }
    }
}