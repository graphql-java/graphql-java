package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class InputObjectTypeDefinition extends AbstractNode implements TypeDefinition {
    private String name;
    private List<Directive> directives = new ArrayList<>();
    private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();

    public InputObjectTypeDefinition(String name) {
        this.name = name;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public List<InputValueDefinition> getInputValueDefinitions() {
        return inputValueDefinitions;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(directives);
        result.addAll(inputValueDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InputObjectTypeDefinition that = (InputObjectTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;

    }


    @Override
    public String toString() {
        return "InputObjectTypeDefinition{" +
                "name='" + name + '\'' +
                ", directives=" + directives +
                ", inputValueDefinitions=" + inputValueDefinitions +
                '}';
    }
}
