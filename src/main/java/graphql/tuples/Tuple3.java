/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package graphql.tuples;

import graphql.Internal;

import java.util.Objects;
import java.util.function.Function;

/**
 * A tuple that holds three non-null values.
 *
 * @param <T1> The type of the first non-null value held by this tuple
 * @param <T2> The type of the second non-null value held by this tuple
 * @param <T3> The type of the third non-null value held by this tuple
 *
 * @author Jon Brisbin
 * @author Stephane Maldini
 */
@Internal
public class Tuple3<T1, T2, T3> extends Tuple2<T1, T2> {

    private static final long serialVersionUID = -4430274211524723033L;

    final T3 t3;

    Tuple3(T1 t1, T2 t2, T3 t3) {
        super(t1, t2);
        this.t3 = Objects.requireNonNull(t3, "t3");
    }

    /**
     * Type-safe way to get the third object of this {@link Tuples}.
     *
     * @return The third object
     */
    public T3 getT3() {
        return t3;
    }

    /**
     * Map the 1st part (T1) of this {@link Tuple3} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T1 part
     * @param <R>    the new type for the T1 part
     *
     * @return a new {@link Tuple3} with a different T1 value
     */
    public <R> Tuple3<R, T2, T3> mapT1(Function<T1, R> mapper) {
        return new Tuple3<>(mapper.apply(t1), t2, t3);
    }

    /**
     * Map the 2nd part (T2) of this {@link Tuple3} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T2 part
     * @param <R>    the new type for the T2 part
     *
     * @return a new {@link Tuple3} with a different T2 value
     */
    public <R> Tuple3<T1, R, T3> mapT2(Function<T2, R> mapper) {
        return new Tuple3<>(t1, mapper.apply(t2), t3);
    }

    /**
     * Map the 3rd part (T3) of this {@link Tuple3} into a different value and type,
     * keeping the other parts.
     *
     * @param mapper the mapping {@link Function} for the T3 part
     * @param <R>    the new type for the T3 part
     *
     * @return a new {@link Tuple3} with a different T3 value
     */
    public <R> Tuple3<T1, T2, R> mapT3(Function<T3, R> mapper) {
        return new Tuple3<>(t1, t2, mapper.apply(t3));
    }

    @Override
    public Object get(int index) {
        switch (index) {
            case 0:
                return t1;
            case 1:
                return t2;
            case 2:
                return t3;
            default:
                return null;
        }
    }

    @Override
    public Object[] toArray() {
        return new Object[]{t1, t2, t3};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tuple3)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        @SuppressWarnings("rawtypes")
        Tuple3 tuple3 = (Tuple3) o;

        return t3.equals(tuple3.t3);
    }

    @Override
    public int size() {
        return 3;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + t3.hashCode();
        return result;
    }
}
