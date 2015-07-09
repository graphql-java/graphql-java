package graphql.language;


import java.util.List;

public class FragmentDefinition implements Definition {
    private String name;
    private TypeName typeCondition;
    private List<Directive> directives;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FragmentDefinition that = (FragmentDefinition) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (typeCondition != null ? !typeCondition.equals(that.typeCondition) : that.typeCondition != null)
            return false;
        if (directives != null ? !directives.equals(that.directives) : that.directives != null) return false;
        return !(selectionSet != null ? !selectionSet.equals(that.selectionSet) : that.selectionSet != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (typeCondition != null ? typeCondition.hashCode() : 0);
        result = 31 * result + (directives != null ? directives.hashCode() : 0);
        result = 31 * result + (selectionSet != null ? selectionSet.hashCode() : 0);
        return result;
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
