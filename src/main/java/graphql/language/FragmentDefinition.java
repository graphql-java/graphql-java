package graphql.language;


import graphql.PublicApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Provided to the DataFetcher, therefore public API
 */
@PublicApi
public class FragmentDefinition extends AbstractNode implements Definition {

    private String name;
    private TypeName typeCondition;
    private List<Directive> directives = new ArrayList<>();
    private SelectionSet selectionSet;

    public FragmentDefinition() {

    }

    public FragmentDefinition(String name, TypeName typeCondition) {
        this.name = name;
        this.typeCondition = typeCondition;
    }

    public FragmentDefinition(String name, TypeName typeCondition, SelectionSet selectionSet) {
        this.name = name;
        this.typeCondition = typeCondition;
        this.selectionSet = selectionSet;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        result.add(typeCondition);
        result.addAll(directives);
        result.add(selectionSet);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FragmentDefinition that = (FragmentDefinition) o;

        return !(name != null ? !name.equals(that.name) : that.name != null);

    }


    @Override
    public String toString() {
        return "FragmentDefinition{" +
                "name='" + name + '\'' +
                ", typeCondition='" + typeCondition + '\'' +
                ", directives=" + directives +
                ", selectionSet=" + selectionSet +
                '}';
    }
}
