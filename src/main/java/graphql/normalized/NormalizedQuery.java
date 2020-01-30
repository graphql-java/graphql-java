package graphql.normalized;

import graphql.Internal;

import java.util.List;

@Internal
public class NormalizedQuery {

    private final List<NormalizedQueryField> rootFields;

    public NormalizedQuery(List<NormalizedQueryField> rootFields) {
        this.rootFields = rootFields;
    }

    public List<NormalizedQueryField> getRootFields() {
        return rootFields;
    }
}
