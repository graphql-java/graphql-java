package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Document extends AbstractNode {

    private List<Definition> definitions = new ArrayList<>();

    public Document() {

    }

    public Document(List<Definition> definitions) {
        this.definitions = definitions;
    }

    public List<Definition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<Definition> definitions) {
        this.definitions = definitions;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(definitions);
    }


    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;


        return true;

    }


    @Override
    public String toString() {
        return "Document{" +
                "definitions=" + definitions +
                '}';
    }
}
