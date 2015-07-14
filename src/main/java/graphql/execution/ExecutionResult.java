package graphql.execution;


import graphql.validation.ValidationError;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {

    private Object result;

    private List<ValidationError> validationErrors = new ArrayList<>();

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
