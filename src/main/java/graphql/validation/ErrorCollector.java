package graphql.validation;


import java.util.ArrayList;
import java.util.List;

public class ErrorCollector {

    private final List<ValidationError> errors = new ArrayList<>();

    public void addError(ValidationError validationError){
        this.errors.add(validationError);
    }

    public List<ValidationError> getErrors(){
        return errors;
    }
}
