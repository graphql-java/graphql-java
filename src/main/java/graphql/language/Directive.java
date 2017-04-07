package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Directive extends AbstractNode {
    private String name;
    private final List<Argument> arguments = new ArrayList<>();

    public Directive() {

    }

    public Directive(String name) {
        this.name = name;
    }

    public Directive(String name, List<Argument> arguments) {
        this.name = name;
        this.arguments.addAll(arguments);
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>(arguments);
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Directive directive = (Directive) o;

        return !(name != null ? !name.equals(directive.name) : directive.name != null);

    }

    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
