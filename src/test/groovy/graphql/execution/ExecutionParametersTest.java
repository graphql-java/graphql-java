package graphql.execution;

import graphql.language.Field;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static graphql.Scalars.*;
import static graphql.execution.ExecutionParameters.newParameters;
import static graphql.execution.TypeInfo.newTypeInfo;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class ExecutionParametersTest {

    @Test
    public void testTransform() {
        TypeInfo typeInfo = newTypeInfo().type(GraphQLString).build();
        Object source = new Object();
        Map<String, List<Field>> fields = singletonMap("a", emptyList());

        ExecutionParameters parameters = newParameters()
                .typeInfo(typeInfo)
                .source(source)
                .fields(fields)
                .build();

        ExecutionParameters newParameters = parameters.transform(it -> it.source(123));

        assertSame(typeInfo, newParameters.typeInfo());
        assertSame(fields, newParameters.fields());

        assertNotSame(source, newParameters.source());
        assertEquals(123, newParameters.source());
    }

}