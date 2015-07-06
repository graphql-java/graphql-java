package graphql.language;


public class NamedType implements Type{

    private String name;

    public NamedType() {
    }
    public NamedType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NamedType namedType = (NamedType) o;

        return !(name != null ? !name.equals(namedType.name) : namedType.name != null);

    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "NamedType{" +
                "name='" + name + '\'' +
                '}';
    }
}
