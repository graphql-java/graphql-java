package graphql.schema.bytecode;

import graphql.Internal;

@Internal
public interface ByteCodeFetcher {
    Object fetch(Object sourceObject, String propertyName);
}
