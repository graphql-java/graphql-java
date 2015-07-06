package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Document {

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Document document = (Document) o;

        return !(definitions != null ? !definitions.equals(document.definitions) : document.definitions != null);

    }

    @Override
    public int hashCode() {
        return definitions != null ? definitions.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Document{" +
                "definitions=" + definitions +
                '}';
    }
}
