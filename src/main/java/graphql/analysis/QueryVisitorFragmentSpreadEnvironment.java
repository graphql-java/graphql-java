package graphql.analysis;

import graphql.PublicApi;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;

@PublicApi
public interface QueryVisitorFragmentSpreadEnvironment {
    FragmentSpread getFragmentSpread();

    FragmentDefinition getFragmentDefinition();
}
