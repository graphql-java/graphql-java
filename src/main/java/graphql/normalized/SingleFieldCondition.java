package graphql.normalized;

import graphql.Internal;
import graphql.Mutable;

import java.util.ArrayList;
import java.util.List;

@Mutable
@Internal
public class SingleFieldCondition {
    private final List<Object> varNames;

    private static class Not {
        String varName;

        public Not(String varName) {
            this.varName = varName;
        }

        @Override
        public String toString() {
            return "!" + varName;
        }
    }

    public SingleFieldCondition(List<Object> varNames) {
        this.varNames = new ArrayList<>(varNames);
    }

    public SingleFieldCondition copy() {
        return new SingleFieldCondition(new ArrayList<>(this.varNames));
    }

    public SingleFieldCondition() {
        this.varNames = new ArrayList<>();
    }

    public List<Object> getVarNames() {
        return varNames;
    }

    public void addSkipVar(String varName) {
        this.varNames.add(new Not(varName));
    }

    public void addIncludeVar(String varName) {
        this.varNames.add(varName);
    }

    @Override
    public String toString() {
        return varNames.toString();
    }
}