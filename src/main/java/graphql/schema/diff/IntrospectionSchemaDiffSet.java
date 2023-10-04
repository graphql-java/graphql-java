package graphql.schema.diff;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.PublicApi;
import graphql.introspection.IntrospectionQuery;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;

import java.util.Map;

/**
 * Represents 2 schemas that can be diffed.  The {@link SchemaDiff} code
 * assumes that that schemas to be diffed are the result of a
 * {@link graphql.introspection.IntrospectionQuery}.
 */
@PublicApi
public class IntrospectionSchemaDiffSet implements SchemaDiffSet {

    private final Map<String, Object> introspectionOld;
    private final Map<String, Object> introspectionNew;

    public IntrospectionSchemaDiffSet(Map<String, Object> introspectionOld, Map<String, Object> introspectionNew) {
        this.introspectionOld = introspectionOld;
        this.introspectionNew = introspectionNew;
    }

    /**
     * @return The IDL Document representing the old schema as defined by the old introspection result map.
     */
    @Override
    public Document getOldSchemaDefinitionDoc() {
        return new IntrospectionResultToSchema().createSchemaDefinition(this.introspectionOld);
    }

    /**
     * @return The IDL Document representing the new schema as defined by the new introspection result map.
     */
    @Override
    public Document getNewSchemaDefinitionDoc() {
        return new IntrospectionResultToSchema().createSchemaDefinition(this.introspectionNew);
    }

    /**
     * Since introspection does not identify what directives are applied on which schema elements, this diff set does not
     * support enforcing directives.
     * @return False
     */
    @Override
    public boolean supportsEnforcingDirectives() {
        return false;
    }

    /**
     * Creates an introspection schema diff set out of the result of 2 introspection queries.
     *
     * @param introspectionOld the older introspection query
     * @param introspectionNew the newer introspection query
     *
     * @return a diff set representing them
     */
    public static IntrospectionSchemaDiffSet diffSet(Map<String, Object> introspectionOld, Map<String, Object> introspectionNew) {
        return new IntrospectionSchemaDiffSet(introspectionOld, introspectionNew);
    }

    /**
     * Creates an introspection schema diff set out of the result of 2 schemas.
     *
     * @param schemaOld the older schema
     * @param schemaNew the newer schema
     *
     * @return a diff set representing them
     */
    public static IntrospectionSchemaDiffSet diffSet(GraphQLSchema schemaOld, GraphQLSchema schemaNew) {
        Map<String, Object> introspectionOld = introspect(schemaOld);
        Map<String, Object> introspectionNew = introspect(schemaNew);
        return diffSet(introspectionOld, introspectionNew);
    }

    private static Map<String, Object> introspect(GraphQLSchema schema) {
        GraphQL gql = GraphQL.newGraphQL(schema).build();
        ExecutionResult result = gql.execute(IntrospectionQuery.INTROSPECTION_QUERY);
        Assert.assertTrue(result.getErrors().size() == 0, () -> "The schema has errors during Introspection");
        return result.getData();
    }
}
