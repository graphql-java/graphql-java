package graphql.normalized;

import graphql.Internal;
import graphql.Mutable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

import static graphql.Assert.assertNotNull;
import static java.lang.String.format;

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

    public boolean evaluate(Map<String, Object> coercedVariables) {
        for (Object var : varNames) {
            if (var instanceof Not) {
                boolean b = (boolean) assertNotNull(coercedVariables.get(((Not) var).varName), () -> format("Expect variable with name %s", ((Not) var).varName));
                if (b) {
                    return false;
                }
            } else {
                boolean b = (boolean) assertNotNull(coercedVariables.get((String) var), () -> format("Expect variable with name %s", var));
                if (!b) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return varNames.toString();
    }
}