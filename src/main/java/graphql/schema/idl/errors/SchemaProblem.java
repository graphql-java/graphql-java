package graphql.schema.idl.errors;

import graphql.GraphQLError;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A number of problems can occur when using the schema tools like {@link graphql.schema.idl.SchemaCompiler}
 * or {@link graphql.schema.idl.SchemaGenerator} classes and they are reported via this
 * exception as a list of {@link GraphQLError}s
 */
public class SchemaProblem extends RuntimeException {

    private final Collection<GraphQLError> errors;

    public SchemaProblem(Collection<GraphQLError> errors) {
        this.errors = new ArrayList<>(errors);
    }

    public Collection<GraphQLError> getErrors() {
        return errors;
    }
}
