package graphql.execution.preparsed;


import graphql.ExecutionInput;
import graphql.PublicSpi;

import java.util.function.Function;

/**
 * Interface that allows clients to hook in Document caching and/or the whitelisting of queries.
 */
@PublicSpi
public interface PreparsedDocumentProvider {
    /**
     * This is called to get a "cached" pre-parsed query and if its not present, then the "parseAndValidateFunction"
     * can be called to parse and validate the query.
     * <p>
     * Note - the "parseAndValidateFunction" MUST be called if you dont have a per parsed version of the query because it not only parses
     * and validates the query, it invokes {@link graphql.execution.instrumentation.Instrumentation} calls as well for parsing and validation.
     * if you dont make a call back on this then these wont happen.
     *
     * @param executionInput           The {@link graphql.ExecutionInput} containing the query
     * @param parseAndValidateFunction If the query has not be pre-parsed, this function MUST be called to parse and validate it
     * @return an instance of {@link PreparsedDocumentEntry}
     */
    PreparsedDocumentEntry getDocument(ExecutionInput executionInput, Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction);
}


