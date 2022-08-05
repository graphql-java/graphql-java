package graphql.schema;

import graphql.language.Value;

import java.util.Map;

public interface CoercingLiteralEnvironment extends CoercingEnvironment<Value<?>> {
    Map<String, Object> getVariables();
}
