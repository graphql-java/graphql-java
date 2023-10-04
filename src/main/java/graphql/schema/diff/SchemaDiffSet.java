package graphql.schema.diff;

import graphql.PublicApi;
import graphql.language.Document;

/**
 * Interface used to define 2 schemas that can be diffed by the {@link SchemaDiff} operation.
 */
@PublicApi
public interface SchemaDiffSet {

    /**
     * @return Returns a IDL document that represents the old schema as part of a SchemaDiff operation.
     */
    Document getOldSchemaDefinitionDoc();

    /**
     * @return Returns a IDL document that represents the new schema created from the introspection result.
     */
    Document getNewSchemaDefinitionDoc();

    /**
     * @return Flag indicating whether this diffset implementation can be used to enforce directives when performing schema diff.
     */
    boolean supportsEnforcingDirectives();
}
