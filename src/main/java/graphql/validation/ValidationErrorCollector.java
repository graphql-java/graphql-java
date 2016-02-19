package graphql.validation;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>ValidationErrorCollector class.</p>
 *
 * @author Andreas Marek
 * @version v1.2
 */
public class ValidationErrorCollector {

    private final List<ValidationError> errors = new ArrayList<>();

    /**
     * <p>addError.</p>
     *
     * @param validationError a {@link graphql.validation.ValidationError} object.
     */
    public void addError(ValidationError validationError){
        this.errors.add(validationError);
    }

    /**
     * <p>Getter for the field <code>errors</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<ValidationError> getErrors(){
        return errors;
    }

    /**
     * <p>containsValidationError.</p>
     *
     * @param validationErrorType a {@link graphql.validation.ValidationErrorType} object.
     * @return a boolean.
     */
    public boolean containsValidationError(ValidationErrorType validationErrorType){
        for(ValidationError validationError : errors){
            if(validationError.getValidationErrorType() == validationErrorType) return true;
        }
        return false;
    }
}
