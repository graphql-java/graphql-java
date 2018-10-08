package graphql.schema.somepackage;

public class ClassWithInteritanceAndInterfaces {

    public static class StartingClass implements InterfaceHolder.Trait1 {
        @Override
        public String getMethodYouMustImplement() {
            return "methodYouMustImplement";
        }
    }

    public static class InheritedClass extends StartingClass implements InterfaceHolder.Trait2 {

        @Override
        public String getMethodYouMustAlsoImplement() {
            return "methodYouMustAlsoImplement";
        }
    }

}
