package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class InterfaceTypeDefinition extends AbstractNode implements TypeDefinition {
    private String name;
    private List<FieldDefinition> definitions = new ArrayList<>();
    private List<Directive> directives = new ArrayList<>();

    public InterfaceTypeDefinition(String name) {
        this.name = name;
    }

    public List<FieldDefinition> getFieldDefinitions() {
        return definitions;
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
        result.addAll(definitions);
        result.addAll(directives);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InterfaceTypeDefinition that = (InterfaceTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "InterfaceTypeDefinition{" +
                "name='" + name + '\'' +
                ", fieldDefinitions=" + definitions +
                ", directives=" + directives +
                '}';
    }
}
