package graphql.language;


import java.util.ArrayList;
import java.util.List;

public class ObjectTypeDefinition extends AbstractNode implements TypeDefinition {
    private String name;
    private List<Type> implementz = new ArrayList<>();
    private List<Directive> directives = new ArrayList<>();
    private List<FieldDefinition> fieldDefinitions = new ArrayList<>();

    public ObjectTypeDefinition(String name) {
        this.name = name;
    }

    public List<Type> getImplements() {
        return implementz;
    }

    public List<Directive> getDirectives() {
        return directives;
    }

    public List<FieldDefinition> getFieldDefinitions() {
        return fieldDefinitions;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public List<Node> getChildren() {
        List<Node> result = new ArrayList<>();
        result.addAll(implementz);
        result.addAll(directives);
        result.addAll(fieldDefinitions);
        return result;
    }

    @Override
    public boolean isEqualTo(Node o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectTypeDefinition that = (ObjectTypeDefinition) o;

        if ( null == name ) {
            if ( null != that.name ) return false;
        } else if ( !name.equals(that.name) ) {
            return false;
        }
        return true;
    }


    @Override
    public String toString() {
        return "ObjectTypeDefinition{" +
                "name='" + name + '\'' +
                ", implements=" + implementz +
                ", directives=" + directives +
                ", fieldDefinitions=" + fieldDefinitions +
                '}';
    }
}
