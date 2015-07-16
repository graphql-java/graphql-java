package graphql.validation.rules;


import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLType;
import graphql.schema.SchemaUtil;
import graphql.validation.*;

public class FragmentsOnCompositeType extends AbstractRule {

    public FragmentsOnCompositeType(ValidationContext validationContext, ErrorCollector errorCollector) {
        super(validationContext, errorCollector);
    }

    @Override
    public void checkInlineFragment(InlineFragment inlineFragment) {
        GraphQLType type = SchemaUtil.findType(getValidationContext().getSchema(), inlineFragment.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            addError(new ValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid));
        }
    }

    @Override
    public void checkFragmentDefinition(FragmentDefinition fragmentDefinition) {
        GraphQLType type = SchemaUtil.findType(getValidationContext().getSchema(), fragmentDefinition.getTypeCondition().getName());
        if (type == null) return;
        if (!(type instanceof GraphQLCompositeType)) {
            addError(new ValidationError(ValidationErrorType.InlineFragmentTypeConditionInvalid));
        }
    }
}
