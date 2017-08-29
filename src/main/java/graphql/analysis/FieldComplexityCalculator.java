package graphql.analysis;

import graphql.PublicApi;

@PublicApi
@FunctionalInterface
public interface FieldComplexityCalculator {

    int calculate(FieldComplexityEnvironment environment, int childComplexity);

}
