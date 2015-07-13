package graphql.execution;


public class ExecutionResult {

    private Object result;

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
