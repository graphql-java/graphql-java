package graphql;


import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>InvalidSyntaxError class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class InvalidSyntaxError implements GraphQLError {

    private final List<SourceLocation> sourceLocations = new ArrayList<SourceLocation>();

    /**
     * <p>Constructor for InvalidSyntaxError.</p>
     *
     * @param sourceLocation a {@link graphql.language.SourceLocation} object.
     */
    public InvalidSyntaxError(SourceLocation sourceLocation) {
        if (sourceLocation != null)
            this.sourceLocations.add(sourceLocation);
    }

    /**
     * <p>Constructor for InvalidSyntaxError.</p>
     *
     * @param sourceLocations a {@link java.util.List} object.
     */
    public InvalidSyntaxError(List<SourceLocation> sourceLocations) {
        if (sourceLocations != null) {
            this.sourceLocations.addAll(sourceLocations);
        }
    }


    /** {@inheritDoc} */
    @Override
    public String getMessage() {
        return "Invalid Syntax";
    }

    /** {@inheritDoc} */
    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    /** {@inheritDoc} */
    @Override
    public ErrorType getErrorType() {
        return ErrorType.InvalidSyntax;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "InvalidSyntaxError{" +
                "sourceLocations=" + sourceLocations +
                '}';
    }

}
