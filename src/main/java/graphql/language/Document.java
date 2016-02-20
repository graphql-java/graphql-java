package graphql.language;


import java.util.ArrayList;
import java.util.List;

/**
 * <p>Document class.</p>
 *
 * @author Andreas Marek
 * @version v1.0
 */
public class Document extends AbstractNode {

    private List<Definition> definitions = new ArrayList<Definition>();

    /**
     * <p>Constructor for Document.</p>
     */
    public Document() {

    }

    /**
     * <p>Constructor for Document.</p>
     *
     * @param definitions a {@link java.util.List} object.
     */
    public Document(List<Definition> definitions) {
        this.definitions = definitions;
    }

    /**
     * <p>Getter for the field <code>definitions</code>.</p>
     *
     * @return a {@link java.util.List} object.
     */
    public List<Definition> getDefinitions() {
        return definitions;
    }

    /**
     * <p>Setter for the field <code>definitions</code>.</p>
     *
     * @param definitions a {@link java.util.List} object.
     */
    public void setDefinitions(List<Definition> definitions) {
        this.definitions = definitions;
    }


    /** {@inheritDoc} */
    @Override
    public List<Node> getChildren() {
        return new ArrayList<Node>(definitions);
    }


    /** {@inheritDoc} */
    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;


        return true;

    }


    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Document{" +
                "definitions=" + definitions +
                '}';
    }
}
