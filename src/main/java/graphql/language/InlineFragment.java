package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class InlineFragment extends AbstractNode implements Selection {
    private TypeName typeCondition;
    private List<Directive> directives = new ArrayList<>();
    private SelectionSet selectionSet;

    public InlineFragment() {

    }

    public InlineFragment(TypeName typeCondition) {
        this.typeCondition = typeCondition;
    }

    public InlineFragment(TypeName typeCondition, List<Directive> directives, SelectionSet selectionSet) {
        this.typeCondition = typeCondition;
        this.directives = directives;
        this.selectionSet = selectionSet;
    }

    public InlineFragment(TypeName typeCondition, SelectionSet selectionSet) {
        this.typeCondition = typeCondition;
        this.selectionSet = selectionSet;
    }


    public TypeName getTypeCondition() {
        return typeCondition;
    }

    public void setTypeCondition(TypeName typeCondition) {
        this.typeCondition = typeCondition;
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
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        if (typeCondition != null) {
            result.add(typeCondition);
        }
        result.addAll(directives);
        result.add(selectionSet);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return true;

    }


    @Override
    public String toString() {
        return "InlineFragment{" +
                "typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
