package graphql.schema.diffing;

public class SchemaDiffingCancelledException extends RuntimeException {
    SchemaDiffingCancelledException(boolean byInterrupt) {
        super("Schema diffing job was cancelled by " + (byInterrupt ? "thread interrupt" : "stop call"));
    }
}
