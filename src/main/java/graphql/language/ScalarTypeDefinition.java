package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ScalarTypeDefinition extends AbstractNode implements TypeDefinition {
    private String name;
    private List<Directive> directives = new ArrayList<>();

    public ScalarTypeDefinition(String name) {
        this.name = name;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScalarTypeDefinition that = (ScalarTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "ScalarTypeDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                '}';
    }
}
