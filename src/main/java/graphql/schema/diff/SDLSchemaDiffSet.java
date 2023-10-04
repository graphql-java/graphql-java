package graphql.schema.diff;

import graphql.PublicApi;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

/**
 * Represents 2 schemas that can be diffed which are defined in SDL.
 */
@PublicApi
public class SDLSchemaDiffSet implements SchemaDiffSet {

    private final String oldSchemaSdl;

    private final String newSchemaSdl;

    public SDLSchemaDiffSet(final String oldSchemaSdl, final String newSchemaSdl) {
        this.oldSchemaSdl = oldSchemaSdl;
        this.newSchemaSdl = newSchemaSdl;
    }

    /**
     * @return The IDL Document representing the old schema as defined by the old schema sdl.
     */
    @Override
    public Document getOldSchemaDefinitionDoc() {
        return Parser.parse(this.oldSchemaSdl);
    }

    /**
     * @return The IDL Document representing the old schema as defined by the new schema sdl.
     */
    @Override
    public Document getNewSchemaDefinitionDoc() {
        return Parser.parse(this.newSchemaSdl);
    }

    /**
     * Since SDL will identify which directives are applied on elements in the schema, this diff set supports enforcing
     * directives.
     * @return True
     */
    @Override
    public boolean supportsEnforcingDirectives() {
        return true;
    }

    /**
     * Creates an sdl schema diff set out of the two sdl definitions.
     *
     * @param oldSchemaSdl the older schema sdl string.
     * @param newSchemaSdl the newer schema sdl string.
     *
     * @return a diff set representing them
     */
    public static SDLSchemaDiffSet diffSet(String oldSchemaSdl, String newSchemaSdl) {
        return new SDLSchemaDiffSet(oldSchemaSdl, newSchemaSdl);
    }

    /**
     * Creates an introspection schema diff set out of the result of 2 schemas.
     *
     * @param schemaOld the older schema
     * @param schemaNew the newer schema
     *
     * @return a diff set representing them
     */
    public static SDLSchemaDiffSet diffSet(GraphQLSchema schemaOld, GraphQLSchema schemaNew) {
        SchemaPrinter printer = new SchemaPrinter(SchemaPrinter.Options.defaultOptions());
        return diffSet(printer.print(schemaOld), printer.print(schemaNew));
    }
}
