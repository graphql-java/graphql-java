package graphql.language;


import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OperationDefinition extends AbstractNode<OperationDefinition> implements Definition<OperationDefinition>, SelectionSetContainer<OperationDefinition> {

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

    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
    }

    @Override
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
    public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
        return visitor.visitOperationDefinition(this, context);
    }

    public static Builder newOperationDefinition() {
        return new Builder();
    }

    public static final class Builder implements NodeBuilder {
        private SourceLocation sourceLocation;
        private List<Comment> comments = Collections.emptyList();
        private String name;
        private Operation operation;
        private List<VariableDefinition> variableDefinitions = new ArrayList<>();
        private List<Directive> directives = new ArrayList<>();
        private SelectionSet selectionSet;

        private Builder() {
        }


        public Builder sourceLocation(SourceLocation sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Builder comments(List<Comment> comments) {
            this.comments = comments;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder operation(Operation operation) {
            this.operation = operation;
            return this;
        }

        public Builder variableDefinitions(List<VariableDefinition> variableDefinitions) {
            this.variableDefinitions = variableDefinitions;
            return this;
        }

        public Builder directives(List<Directive> directives) {
            this.directives = directives;
            return this;
        }

        public Builder selectionSet(SelectionSet selectionSet) {
            this.selectionSet = selectionSet;
            return this;
        }

        public OperationDefinition build() {
            OperationDefinition operationDefinition = new OperationDefinition();
            operationDefinition.setSourceLocation(sourceLocation);
            operationDefinition.setComments(comments);
            operationDefinition.setName(name);
            operationDefinition.setOperation(operation);
            operationDefinition.setVariableDefinitions(variableDefinitions);
            operationDefinition.setDirectives(directives);
            operationDefinition.setSelectionSet(selectionSet);
            return operationDefinition;
        }
    }
}
