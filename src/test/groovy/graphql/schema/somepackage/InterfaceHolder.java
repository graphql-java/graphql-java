package graphql.schema.somepackage;

public class InterfaceHolder {

    public interface Trait1 {
        String getMethodYouMustImplement();

        default String getMethodThatIsADefault() {
            return "methodThatIsADefault";
        }
    }

    public interface Trait2 {
        String getMethodYouMustAlsoImplement();

        default String getMethodThatIsAlsoADefault() {
            return "methodThatIsAlsoADefault";
        }
    }
}
