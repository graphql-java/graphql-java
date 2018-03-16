package graphql.util;

import graphql.Internal;

@Internal
public class TraverserResult {

    private final Object result;


    public TraverserResult(Object result) {
        this.result = result;
    }


    public Object getResult() {
        return result;
    }

}
