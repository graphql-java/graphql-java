package graphql.validation;


import graphql.language.Node;
import graphql.language.SourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ErrorFactory {


    public ValidationError newError(ValidationErrorType validationErrorType, List<? extends Node> locations, String description) {
        List<SourceLocation> locationList = new ArrayList<>();
        for (Node node : locations) {
            locationList.add(node.getSourceLocation());
        }
        return new ValidationError(validationErrorType, locationList, description);
    }

}
