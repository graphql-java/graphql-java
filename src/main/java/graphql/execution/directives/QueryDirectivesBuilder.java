package graphql.execution.directives;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.CoercedVariables;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.schema.GraphQLSchema;

import java.util.Locale;

@Internal
public class QueryDirectivesBuilder implements QueryDirectives.Builder {

    private MergedField mergedField;
    private GraphQLSchema schema;
    private CoercedVariables coercedVariables = CoercedVariables.emptyVariables();
    private GraphQLContext graphQLContext = GraphQLContext.getDefault();
    private Locale locale = Locale.getDefault();

    @Override
    public QueryDirectives.Builder schema(GraphQLSchema schema) {
        this.schema = schema;
        return this;
    }

    @Override
    public QueryDirectives.Builder mergedField(MergedField mergedField) {
        this.mergedField = mergedField;
        return this;
    }

    @Override
    public QueryDirectives.Builder field(Field field) {
        this.mergedField = MergedField.newMergedField(field).build();
        return this;
    }

    @Override
    public QueryDirectives.Builder coercedVariables(CoercedVariables coercedVariables) {
        this.coercedVariables = coercedVariables;
        return this;
    }

    @Override
    public QueryDirectives.Builder graphQLContext(GraphQLContext graphQLContext) {
        this.graphQLContext = graphQLContext;
        return this;
    }

    @Override
    public QueryDirectives.Builder locale(Locale locale) {
        this.locale = locale;
        return this;
    }


    @Override
    public QueryDirectives build() {
        return new QueryDirectivesImpl(mergedField, schema, coercedVariables.toMap(), graphQLContext, locale);
    }
}
