package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class SchemaDefinition extends AbstractNode implements Definition {
    private List<Directive> directives = new ArrayList<>();
    private List<OperationTypeDefinition> operationTypeDefinitions = new ArrayList<>();

    public SchemaDefinition() {
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public List<OperationTypeDefinition> getOperationTypeDefinitions() {
        return operationTypeDefinitions;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        result.addAll(operationTypeDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SchemaDefinition that = (SchemaDefinition) o;

        return true;
    }


    @Override
    public String toString() {
        return "SchemaDefinition{" +
                "directives=" + directives +
                ", operationTypeDefinitions=" + operationTypeDefinitions +
                "}";
    }
}
