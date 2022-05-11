package graphql.execution.preparsed.persisted;

import graphql.ExecutionInput;
import graphql.PublicSpi;
import graphql.execution.preparsed.PreparsedDocumentEntry;

import java.util.concurrent.CompletableFuture;

/**
 * This interface is used to abstract an actual cache that can cache parsed persistent queries.
 */
@PublicSpi
public interface PersistedQueryCache {

    /**
     * This is called to get a persisted query from cache.
     * <p>
     * If its present in cache then  it must return a PreparsedDocumentEntry where {@link graphql.execution.preparsed.PreparsedDocumentEntry#getDocument()}
     * is already parsed and validated.  This will be passed onto the graphql engine as is.
     * <p>
     * If its a valid query id but its no present in cache, (cache miss) then you need to call back the "onCacheMiss" function with associated query text.
     * This will be compiled and validated by the graphql engine and the a PreparsedDocumentEntry will be passed back ready for you to cache it.
     * <p>
     * If its not a valid query id then throw a {@link graphql.execution.preparsed.persisted.PersistedQueryNotFound} to indicate this.
     *
     * @param persistedQueryId the persisted query id
     * @param executionInput   the original execution input
     * @param onCacheMiss      the call back should it be a valid query id but its not currently not in the cache
     * @return a parsed and validated PreparsedDocumentEntry where {@link graphql.execution.preparsed.PreparsedDocumentEntry#getDocument()} is set
     * @throws graphql.execution.preparsed.persisted.PersistedQueryNotFound if the query id is not know at all and you have no query text
     *
     * @deprecated - use {@link #getPersistedQueryDocumentAsync(Object persistedQueryId, ExecutionInput executionInput, PersistedQueryCacheMiss onCacheMiss)}
     */
    @Deprecated
    PreparsedDocumentEntry getPersistedQueryDocument(Object persistedQueryId, ExecutionInput executionInput, PersistedQueryCacheMiss onCacheMiss) throws PersistedQueryNotFound;

    /**
     * This is called to get a persisted query from cache.
     * <p>
     * If its present in cache then  it must return a PreparsedDocumentEntry where {@link graphql.execution.preparsed.PreparsedDocumentEntry#getDocument()}
     * is already parsed and validated.  This will be passed onto the graphql engine as is.
     * <p>
     * If its a valid query id but its no present in cache, (cache miss) then you need to call back the "onCacheMiss" function with associated query text.
     * This will be compiled and validated by the graphql engine and the a PreparsedDocumentEntry will be passed back ready for you to cache it.
     * <p>
     * If its not a valid query id then throw a {@link graphql.execution.preparsed.persisted.PersistedQueryNotFound} to indicate this.
     *
     * @param persistedQueryId the persisted query id
     * @param executionInput   the original execution input
     * @param onCacheMiss      the call back should it be a valid query id but its not currently not in the cache
     * @return a promise to parsed and validated {@link PreparsedDocumentEntry} where {@link graphql.execution.preparsed.PreparsedDocumentEntry#getDocument()} is set
     * @throws graphql.execution.preparsed.persisted.PersistedQueryNotFound if the query id is not know at all and you have no query text
     */
    default CompletableFuture<PreparsedDocumentEntry> getPersistedQueryDocumentAsync(Object persistedQueryId, ExecutionInput executionInput, PersistedQueryCacheMiss onCacheMiss) throws PersistedQueryNotFound{
        return CompletableFuture.completedFuture(getPersistedQueryDocument(persistedQueryId, executionInput, onCacheMiss));
    }
}
