package graphql.execution.preparsed.persisted;

import graphql.ExecutionInput;
import graphql.PublicApi;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;

/**
 * This persisted query support class supports the Apollo scheme where the persisted
 * query id is in {@link graphql.ExecutionInput#getExtensions()}.
 * <p>
 * You need to provide a {@link PersistedQueryCache} cache implementation
 * as the backing cache.
 * <p>
 * See <a href="https://www.apollographql.com/docs/apollo-server/performance/apq/">Apollo Persisted Queries</a>
 * <p>
 * The Apollo client sends a hash of the persisted query in the input extensions in the following form
 * <pre>
 *     {
 *      "extensions":{
 *       "persistedQuery":{
 *        "version":1,
 *        "sha256Hash":"fcf31818e50ac3e818ca4bdbc433d6ab73176f0b9d5f9d5ad17e200cdab6fba4"
 *      }
 *    }
 *  }
 * </pre>
 *
 * @see graphql.ExecutionInput#getExtensions()
 */
@PublicApi
public class ApolloPersistedQuerySupport extends PersistedQuerySupport {

    private static final String CHECKSUM_TYPE = "SHA-256";

    public ApolloPersistedQuerySupport(PersistedQueryCache persistedQueryCache) {
        super(persistedQueryCache);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Optional<Object> getPersistedQueryId(ExecutionInput executionInput) {
        Map<String, Object> extensions = executionInput.getExtensions();
        Map<String, Object> persistedQuery = (Map<String, Object>) extensions.get("persistedQuery");
        if (persistedQuery != null) {
            Object sha256Hash = persistedQuery.get("sha256Hash");
            return Optional.ofNullable(sha256Hash);
        }
        return Optional.empty();
    }

    @Override
    protected boolean persistedQueryIdIsInvalid(Object persistedQueryId, String queryText) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(CHECKSUM_TYPE);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }

        BigInteger bigInteger = new BigInteger(1, messageDigest.digest(queryText.getBytes(StandardCharsets.UTF_8)));
        String calculatedChecksum = String.format("%064x", bigInteger);
        return !calculatedChecksum.equalsIgnoreCase(persistedQueryId.toString());
    }
}
