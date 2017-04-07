package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class EnumValue extends AbstractNode implements Value {

    private String name;

    public EnumValue(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
    public List<Node> getChildren() {
        return new ArrayList<>();
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnumValue enumValue = (EnumValue) o;

        return !(name != null ? !name.equals(enumValue.name) : enumValue.name != null);

    }


    @Override
    public String toString() {
        return "EnumValue{" +
                "name='" + name + '\'' +
                '}';
    }
}
