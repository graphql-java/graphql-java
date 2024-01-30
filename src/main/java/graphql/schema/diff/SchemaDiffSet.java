package graphql.schema.diff;

import graphql.Assert;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.PublicApi;
import graphql.introspection.IntrospectionQuery;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

import java.util.Map;

/**
 * Interface used to define 2 schemas that can be diffed by the {@link SchemaDiff} operation.
 */
@PublicApi
public class SchemaDiffSet {

    private final Document oldSchemaDoc;
    private final Document newSchemaDoc;
    private final boolean supportsEnforcingDirectives;

    private SchemaDiffSet(final Document oldSchemaDoc,
                          final Document newSchemaDoc,
                          final boolean supportsEnforcingDirectives) {
        this.oldSchemaDoc = oldSchemaDoc;
        this.newSchemaDoc = newSchemaDoc;
        this.supportsEnforcingDirectives = supportsEnforcingDirectives;
    }

    /**
     * @return Returns a IDL document that represents the old schema as part of a SchemaDiff operation.
     */
    public Document getOldSchemaDefinitionDoc() {
        return this.oldSchemaDoc;
    }

    /**
     * @return Returns a IDL document that represents the new schema created from the introspection result.
     */
    public Document getNewSchemaDefinitionDoc() {
        return this.newSchemaDoc;
    }

    /**
     * @return Flag indicating whether this diffset implementation can be used to enforce directives when performing schema diff.
     */
    public boolean supportsEnforcingDirectives() {
        return this.supportsEnforcingDirectives;
    }

    /**
     * Creates an schema diff set out of the result of 2 introspection queries.
     *
     * @param introspectionOld the older introspection query
     * @param introspectionNew the newer introspection query
     *
     * @return a diff set representing them which will not support enforcing directives.
     */
    public static SchemaDiffSet diffSetFromIntrospection(final Map<String, Object> introspectionOld,
                                                         final Map<String, Object> introspectionNew) {
        final Document oldDoc = getDocumentFromIntrospection(introspectionOld);
        final Document newDoc = getDocumentFromIntrospection(introspectionNew);
        return new SchemaDiffSet(oldDoc, newDoc, false);
    }

    /**
     * Creates an schema diff set out of the result of 2 introspection queries.
     *
     * @param oldSchema the older GraphQLSchema object to introspect.
     * @param newSchema the new GraphQLSchema object to introspect.
     *
     * @return a diff set representing them which will not support enforcing directives.
     */
    public static SchemaDiffSet diffSetFromIntrospection(final GraphQLSchema oldSchema,
                                                         final GraphQLSchema newSchema) {
        final Map<String, Object> introspectionOld = introspect(oldSchema);
        final Map<String, Object> introspectionNew = introspect(newSchema);
        return diffSetFromIntrospection(introspectionOld, introspectionNew);
    }

    /**
     * Creates an schema diff set out of the two SDL definition Strings.
     *
     * @param oldSchemaSdl the older SDL definition String.
     * @param newSchemaSdl the newer SDL definition String.
     *
     * @return a diff set representing them which will support enforcing directives.
     */
    public static SchemaDiffSet diffSetFromSdl(final String oldSchemaSdl,
                                               final String newSchemaSdl) {
        final Document oldDoc = getDocumentFromSDLString(oldSchemaSdl);
        final Document newDoc = getDocumentFromSDLString(newSchemaSdl);
        return new SchemaDiffSet(oldDoc, newDoc, true);
    }

    /**
     * Creates an schema diff set out of the two SDL definition Strings.
     *
     * @param oldSchema the older SDL definition String.
     * @param newSchema the newer SDL definition String.
     *
     * @return a diff set representing them which will support enforcing directives.
     */
    public static SchemaDiffSet diffSetFromSdl(final GraphQLSchema oldSchema,
                                               final GraphQLSchema newSchema) {
        final String oldSchemaSdl = getSchemaSdl(oldSchema);
        final String newSchemaSdl = getSchemaSdl(newSchema);
        return diffSetFromSdl(oldSchemaSdl, newSchemaSdl);
    }

    private static Document getDocumentFromIntrospection(final Map<String, Object> introspectionResult) {
        return new IntrospectionResultToSchema().createSchemaDefinition(introspectionResult);
    }

    private static Document getDocumentFromSDLString(final String sdlString) {
        return Parser.parse(sdlString);
    }

    private static String getSchemaSdl(GraphQLSchema schema) {
        final SchemaPrinter schemaPrinter = new SchemaPrinter();
        return schemaPrinter.print(schema);
    }

    private static Map<String, Object> introspect(GraphQLSchema schema) {
        GraphQL gql = GraphQL.newGraphQL(schema).build();
        ExecutionResult result = gql.execute(IntrospectionQuery.INTROSPECTION_QUERY);
        Assert.assertTrue(result.getErrors().size() == 0, () -> "The schema has errors during Introspection");
        return result.getData();
    }
}
