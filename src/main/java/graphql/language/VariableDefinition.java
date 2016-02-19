package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>VariableDefinition class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class VariableDefinition extends AbstractNode {

    private String name;
    private Type type;
    private Value defaultValue;

    /**
     * <p>Constructor for VariableDefinition.</p>
     */
    public VariableDefinition() {

    }


    /**
     * <p>Constructor for VariableDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param type a {@link graphql.language.Type} object.
     */
    public VariableDefinition(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    /**
     * <p>Constructor for VariableDefinition.</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param type a {@link graphql.language.Type} object.
     * @param defaultValue a {@link graphql.language.Value} object.
     */
    public VariableDefinition(String name, Type type, Value defaultValue) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * <p>Getter for the field <code>defaultValue</code>.</p>
     *
     * @return a {@link graphql.language.Value} object.
     */
    public Value getDefaultValue() {
        return defaultValue;
    }

    /**
     * <p>Setter for the field <code>defaultValue</code>.</p>
     *
     * @param defaultValue a {@link graphql.language.Value} object.
     */
    public void setDefaultValue(Value defaultValue) {
        this.defaultValue = defaultValue;
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
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return a {@link graphql.language.Type} object.
     */
    public Type getType() {
        return type;
    }

    /**
     * <p>Setter for the field <code>type</code>.</p>
     *
     * @param type a {@link graphql.language.Type} object.
     */
    public void setType(Type type) {
        this.type = type;
    }

    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.add(type);
        if (defaultValue != null) result.add(defaultValue);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VariableDefinition that = (VariableDefinition) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "VariableDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", defaultValue=" + defaultValue +
                '}';
    }
}
