package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Document {

    private List<Definition> definitions = new ArrayList<>();

    public List<Definition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<Definition> definitions) {
        this.definitions = definitions;
    }

}
