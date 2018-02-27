package graphql.language;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static graphql.language.NodeUtil.directivesByName;

public class OperationDefinition extends AbstractNode<OperationDefinition> implements Definition<OperationDefinition> {

    public enum Operation {
        QUERY, MUTATION, SUBSCRIPTION
    }

    private String name;

    private Operation operation;
    private List<VariableDefinition> variableDefinitions = new ArrayList<>();
    private List<Directive> directives = new ArrayList<>();
    private SelectionSet selectionSet;

    public OperationDefinition() {

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
        List<Node> result = new ArrayList<>();
        result.addAll(variableDefinitions);
        result.addAll(directives);
        result.add(selectionSet);
        return result;
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

    public Map<String, Directive> getDirectivesByName() {
        return directivesByName(directives);
    }

    public Directive getDirective(String directiveName) {
        return getDirectivesByName().get(directiveName);
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
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationDefinition that = (OperationDefinition) o;

        return NodeUtil.isEqualTo(this.name, that.name) && operation == that.operation;

    }

    @Override
    public OperationDefinition deepCopy() {
        return new OperationDefinition(name,
                operation,
                deepCopy(variableDefinitions),
                deepCopy(directives),
                deepCopy(selectionSet)
        );
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

    @Override
    public <U> Object accept(U data, NodeVisitor<U> visitor) {
        return visitor.visitOperationDefinition(this, data);
    }
}
