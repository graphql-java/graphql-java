package graphql;


import graphql.language.SourceLocation;

import java.util.List;

/**
 * The graphql spec says that mutations are optional but it does not specify how to respond
 * when it is not supported.  This error is returned in this case.
 *
 * http://facebook.github.io/graphql/#sec-Initial-types
 */
public class MutationNotSupportedError implements GraphQLError {

    @Override
    public String getMessage() {
        return "Mutations are not supported onm this schema";
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.MutationNotSupported;
    }

    @Override
    public String toString() {
        return "MutationNotSupportedError";
    }

    @Override
    public boolean equals(Object o) {
        return Helper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Helper.hashCode(this);
    }
}
