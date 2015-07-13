package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class TypeName implements Type {

    private String name;

    public TypeName(String name) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeName namedType = (TypeName) o;

        if (name != null ? !name.equals(namedType.name) : namedType.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "TypeName{" +
                "name='" + name + '\'' +
                '}';
    }
}
