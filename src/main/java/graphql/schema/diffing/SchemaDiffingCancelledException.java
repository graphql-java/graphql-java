package graphql.schema.diffing;

import graphql.Internal;

@Internal
public class SchemaDiffingCancelledException extends RuntimeException {
    SchemaDiffingCancelledException(boolean byInterrupt) {
        super("Schema diffing job was cancelled by " + (byInterrupt ? "thread interrupt" : "stop call"));
    }
}
