package graphql;

import java.util.Map;

public interface CustomGraphQLErrorSupport {
    Map<String,?> getErrors();
}
