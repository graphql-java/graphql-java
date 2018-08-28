package graphql.schema.somepackage;

import graphql.schema.DataFetchingEnvironment;

@SuppressWarnings("unused")
public class ClassWithDFEMethods {

    public String getMethodWithDFE(DataFetchingEnvironment dataFetchingEnvironment) {
        return "methodWithDFE";
    }

    public String getMethodWithDFE() {
        return "methodWithDFEWontBeFoundAsItsSecondarilyPreferred";
    }

    public String getMethodWithoutDFE() {
        return "methodWithoutDFE";
    }

    String getDefaultMethodWithDFE(DataFetchingEnvironment dataFetchingEnvironment) {
        return "defaultMethodWithDFE";
    }

    String getDefaultMethodWithDFE() {
        return "defaultMethodWithDFEWontBeFoundAsItsSecondarilyPreferred";
    }

    String getDefaultMethodWithoutDFE() {
        return "defaultMethodWithoutDFE";
    }

    public String getMethodWithTooManyArgs(DataFetchingEnvironment dataFetchingEnvironment, String moreArgs) {
        return "methodWithTooManyArgs";
    }
    String getDefaultMethodWithTooManyArgs(DataFetchingEnvironment dataFetchingEnvironment, String moreArgs) {
        return "defaultMethodWithTooManyArgs";
    }

    public String getMethodWithOneArgButNotDataFetchingEnvironment(String moreArgs) {
        return "methodWithOneArgButNotDataFetchingEnvironment";
    }
    String getDefaultMethodWithOneArgButNotDataFetchingEnvironment(String moreArgs) {
        return "defaultMethodWithOneArgButNotDataFetchingEnvironment";
    }

    public String getMethodUsesDataFetchingEnvironment(DataFetchingEnvironment dataFetchingEnvironment) {
        return dataFetchingEnvironment.getArgument("argument1");
    }

    String getDefaultMethodUsesDataFetchingEnvironment(DataFetchingEnvironment dataFetchingEnvironment) {
        return dataFetchingEnvironment.getArgument("argument2");
    }
}