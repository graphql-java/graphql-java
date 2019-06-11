package graphql.schema.somepackage;

public class ClassWithInterfaces implements InterfaceHolder.Trait1, InterfaceHolder.Trait2 {
    @Override
    public String getMethodYouMustImplement() {
        return "methodYouMustImplement";
    }

    @Override
    public String getMethodYouMustAlsoImplement() {
        return "methodYouMustAlsoImplement";
    }
}
