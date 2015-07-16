package graphql.execution;


import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {

    private Object result;

    private List<ValidationError> validationErrors = new ArrayList<>();

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public ExecutionResult(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public ExecutionResult(Object result) {
        this.result = result;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
