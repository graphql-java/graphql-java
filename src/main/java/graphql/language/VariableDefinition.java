package graphql.language;


public class VariableDefinition {

    private String name;
    private Type type;

    public VariableDefinition() {

    }


    public VariableDefinition(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VariableDefinition that = (VariableDefinition) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return !(type != null ? !type.equals(that.type) : that.type != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VariableDefinition{" +
                "name='" + name + '\'' +
                ", type=" + type +
                '}';
    }
}
