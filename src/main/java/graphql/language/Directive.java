package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class Directive implements Node {
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
        return new ArrayList<Node>(arguments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Directive directive = (Directive) o;

        if (name != null ? !name.equals(directive.name) : directive.name != null) return false;
        return !(arguments != null ? !arguments.equals(directive.arguments) : directive.arguments != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Directive{" +
                "name='" + name + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
