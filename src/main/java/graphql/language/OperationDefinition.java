package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class OperationDefinition implements Definition {

    public enum Operation {
        QUERY, MUTATION
    }

    private String name;

    private Operation operation;
    private List<VariableDefinition> variableDefinitions = new ArrayList<>();
    private List<Directive> directives;
    private SelectionSet selectionSet;

    public OperationDefinition(){

    }

    public OperationDefinition(String name, Operation operation, List<VariableDefinition> variableDefinitions, List<Directive> directives, SelectionSet selectionSet) {
        this.name = name;
        this.operation = operation;
        this.variableDefinitions = variableDefinitions;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }

    public OperationDefinition(String name, Operation operation, List<VariableDefinition> variableDefinitions, SelectionSet selectionSet) {
        this.name = name;
        this.operation = operation;
        this.variableDefinitions = variableDefinitions;
        this.selectionSet = selectionSet;
    }

    public OperationDefinition(String name, Operation operation, SelectionSet selectionSet) {
        this.name = name;
        this.operation = operation;
        this.selectionSet = selectionSet;
    }

    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public List<VariableDefinition> getVariableDefinitions() {
        return variableDefinitions;
    }

    public void setVariableDefinitions(List<VariableDefinition> variableDefinitions) {
        this.variableDefinitions = variableDefinitions;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
    }

    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    public void setSelectionSet(SelectionSet selectionSet) {
        this.selectionSet = selectionSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationDefinition that = (OperationDefinition) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (operation != that.operation) return false;
        if (variableDefinitions != null ? !variableDefinitions.equals(that.variableDefinitions) : that.variableDefinitions != null)
            return false;
        if (directives != null ? !directives.equals(that.directives) : that.directives != null) return false;
        return !(selectionSet != null ? !selectionSet.equals(that.selectionSet) : that.selectionSet != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (operation != null ? operation.hashCode() : 0);
        result = 31 * result + (variableDefinitions != null ? variableDefinitions.hashCode() : 0);
        result = 31 * result + (directives != null ? directives.hashCode() : 0);
        result = 31 * result + (selectionSet != null ? selectionSet.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OperationDefinition{" +
                "name='" + name + '\'' +
                ", operation=" + operation +
                ", variableDefinitions=" + variableDefinitions +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
