package graphql.execution;


public class ExecutionResult {

    private Object resut;

    public ExecutionResult(Object resut) {
        this.resut = resut;
    }

    public Object getResut() {
        return resut;
    }

    public void setResut(Object resut) {
        this.resut = resut;
    }
}
