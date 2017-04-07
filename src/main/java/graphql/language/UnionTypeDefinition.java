package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class UnionTypeDefinition extends AbstractNode implements TypeDefinition {
    private String name;
    private List<Directive> directives = new ArrayList<>();
    private List<Type> memberTypes = new ArrayList<>();

    public UnionTypeDefinition(String name) {
        this.name = name;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public List<Type> getMemberTypes() {
        return memberTypes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        result.addAll(memberTypes);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnionTypeDefinition that = (UnionTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "UnionTypeDefinition{" +
                "name='" + name + '\'' +
                "directives=" + directives +
                ", memberTypes=" + memberTypes +
                '}';
    }
}
