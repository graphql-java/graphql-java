package graphql.normalized;

import graphql.Internal;
import graphql.Mutable;

import java.util.LinkedHashSet;
import java.util.Objects;

@Mutable
@Internal
public class SingleFieldCondition {
    private final LinkedHashSet<Object> varNames;

    private static class Not {
        String varName;

        public Not(String varName) {
            this.varName = varName;
        }

        @Override
        public String toString() {
            return "!" + varName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Not not = (Not) o;
            return Objects.equals(varName, not.varName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(varName);
        }
    }

    public SingleFieldCondition(LinkedHashSet<Object> varNames) {
        this.varNames = new LinkedHashSet<>(varNames);
    }

    public SingleFieldCondition copy() {
        return new SingleFieldCondition(new LinkedHashSet<>(this.varNames));
    }

    public SingleFieldCondition() {
        this.varNames = new LinkedHashSet<>();
    }

    public LinkedHashSet<Object> getVarNames() {
        return varNames;
    }

    public void addSkipVar(String varName) {
        this.varNames.add(new Not(varName));
    }

    public void addIncludeVar(String varName) {
        this.varNames.add(varName);
    }

    public boolean isAlwaysTrue() {
        return varNames.size() == 0;
    }

    @Override
    public String toString() {
        return varNames.toString();
    }
}