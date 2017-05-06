package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class DirectiveDefinition extends AbstractNode implements Definition {
    private String name;
    private List<InputValueDefinition> inputValueDefinitions = new ArrayList<>();
    private List<DirectiveLocation> directiveLocations = new ArrayList<>();

    public DirectiveDefinition(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<InputValueDefinition> getInputValueDefinitions() {
        return inputValueDefinitions;
    }

    public List<DirectiveLocation> getDirectiveLocations() {
        return directiveLocations;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(inputValueDefinitions);
        result.addAll(directiveLocations);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DirectiveDefinition that = (DirectiveDefinition) o;

        if (null == name) {
            if (null != that.name) return false;
        } else if (!name.equals(that.name)) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "DirectiveDefinition{" +
                "name='" + name + "'" +
                ", inputValueDefinitions=" + inputValueDefinitions +
                ", directiveLocations=" + directiveLocations +
                "}";
    }
}
