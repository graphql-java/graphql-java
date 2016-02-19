package graphql.validation;


import graphql.language.Node;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>ErrorFactory class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class ErrorFactory {


    /**
     * <p>newError.</p>
     *
     * @param validationErrorType a {@link graphql.validation.ValidationErrorType} object.
     * @param locations a {@link java.util.List} object.
     * @param description a {@link java.lang.String} object.
     * @return a {@link graphql.validation.ValidationError} object.
     */
    public ValidationError newError(ValidationErrorType validationErrorType, List<? extends Node> locations, String description) {
        List<SourceLocation> locationList = new ArrayList<>();
        for (Node node : locations) {
            locationList.add(node.getSourceLocation());
        }
        return new ValidationError(validationErrorType, locationList, description);
    }

    /**
     * <p>newError.</p>
     *
     * @param validationErrorType a {@link graphql.validation.ValidationErrorType} object.
     * @param description a {@link java.lang.String} object.
     * @return a {@link graphql.validation.ValidationError} object.
     */
    public ValidationError newError(ValidationErrorType validationErrorType, String description) {
        return new ValidationError(validationErrorType, (List) null, description);
    }
}
