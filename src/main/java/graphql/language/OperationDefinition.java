package graphql.language;


public class OperationDefinition implements Definition {

    public enum Operation {
        QUERY, MUTATION
    }

    private Operation operation;
    private String name;

}
