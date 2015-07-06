package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class SelectionSet {

    private final List<Selection> selections = new ArrayList<>();

    public List<Selection> getSelections() {
        return selections;
    }
}
