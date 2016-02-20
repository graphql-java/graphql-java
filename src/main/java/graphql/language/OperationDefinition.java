package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>OperationDefinition class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class OperationDefinition extends AbstractNode implements Definition {

    public enum Operation {
        QUERY, MUTATION
    }

    private String name;

    private Operation operation;
    private List<VariableDefinition> variableDefinitions = new ArrayList<VariableDefinition>();
    private List<Directive> directives = new ArrayList<Directive>();
    private SelectionSet selectionSet;

    /**
     * <p>Constructor for OperationDefinition.</p>
     */
    public OperationDefinition() {

    }

    /**
     * <p>Constructor for OperationDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param operation a {@link graphql.language.OperationDefinition.Operation} object.
     * @param variableDefinitions a {@link java.util.List} object.
     * @param directives a {@link java.util.List} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public OperationDefinition(String name, Operation operation, List<VariableDefinition> variableDefinitions, List<Directive> directives, SelectionSet selectionSet) {
        this.name = name;
        this.operation = operation;
        this.variableDefinitions = variableDefinitions;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }

    /**
     * <p>Constructor for OperationDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param operation a {@link graphql.language.OperationDefinition.Operation} object.
     * @param variableDefinitions a {@link java.util.List} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public OperationDefinition(String name, Operation operation, List<VariableDefinition> variableDefinitions, SelectionSet selectionSet) {
        this.name = name;
        this.operation = operation;
        this.variableDefinitions = variableDefinitions;
        this.selectionSet = selectionSet;
    }

    /**
     * <p>Constructor for OperationDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param operation a {@link graphql.language.OperationDefinition.Operation} object.
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public OperationDefinition(String name, Operation operation, SelectionSet selectionSet) {
        this.name = name;
        this.operation = operation;
        this.selectionSet = selectionSet;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<Node>();
        result.addAll(variableDefinitions);
        result.addAll(directives);
        result.add(selectionSet);
        return result;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Setter for the field <code>name</code>.</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>operation</code>.</p>
     *
     * @return a {@link graphql.language.OperationDefinition.Operation} object.
     */
    public Operation getOperation() {
        return operation;
    }

    /**
     * <p>Setter for the field <code>operation</code>.</p>
     *
     * @param operation a {@link graphql.language.OperationDefinition.Operation} object.
     */
    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    /**
     * <p>Getter for the field <code>variableDefinitions</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<VariableDefinition> getVariableDefinitions() {
        return variableDefinitions;
    }

    /**
     * <p>Setter for the field <code>variableDefinitions</code>.</p>
     *
     * @param variableDefinitions a {@link java.util.List} object.
     */
    public void setVariableDefinitions(List<VariableDefinition> variableDefinitions) {
        this.variableDefinitions = variableDefinitions;
    }

    /**
     * <p>Getter for the field <code>directives</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Directive> getDirectives() {
        return directives;
    }

    /**
     * <p>Setter for the field <code>directives</code>.</p>
     *
     * @param directives a {@link java.util.List} object.
     */
    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
    }

    /**
     * <p>Getter for the field <code>selectionSet</code>.</p>
     *
     * @return a {@link graphql.language.SelectionSet} object.
     */
    public SelectionSet getSelectionSet() {
        return selectionSet;
    }

    /**
     * <p>Setter for the field <code>selectionSet</code>.</p>
     *
     * @param selectionSet a {@link graphql.language.SelectionSet} object.
     */
    public void setSelectionSet(SelectionSet selectionSet) {
        this.selectionSet = selectionSet;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OperationDefinition that = (OperationDefinition) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return operation == that.operation;

    }

    /** {@inheritDoc} */
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
