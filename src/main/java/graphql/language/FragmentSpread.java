package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class FragmentSpread extends AbstractNode implements Selection {

    private String name;
    private List<Directive> directives = new ArrayList<>();

    public FragmentSpread() {
    }

    public FragmentSpread(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public void setDirectives(List<Directive> directives) {
        this.directives = directives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FragmentSpread that = (FragmentSpread) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return !(directives != null ? !directives.equals(that.directives) : that.directives != null);

    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        return result;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (directives != null ? directives.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FragmentSpread{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }
}
