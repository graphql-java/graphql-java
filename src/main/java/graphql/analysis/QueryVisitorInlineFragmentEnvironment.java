package graphql.analysis;

import graphql.PublicApi;
import graphql.language.InlineFragment;

@PublicApi
public interface QueryVisitorInlineFragmentEnvironment {
    InlineFragment getInlineFragment();
}
