package graphql.schema.diffing;

import graphql.GraphQLException;
import graphql.Internal;

@Internal
public class SchemaDiffingCancelledException extends GraphQLException {
    SchemaDiffingCancelledException(boolean byInterrupt) {
        super("Schema diffing job was cancelled by " + (byInterrupt ? "thread interrupt" : "stop call"));
    }
}
