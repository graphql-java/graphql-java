/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * This class was taken from Spring https://github.com/spring-projects/spring-framework/blob/main/spring-core/src/main/java/org/springframework/util/AlternativeJdkIdGenerator.java
 * as a way to get a more performant UUID generator for use as request ids.  SecureRandom can be expensive
 * to run on each request as per https://github.com/graphql-java/graphql-java/issues/3435, so this uses SecureRandom
 * at application start and then the cheaper Random class each call after that.
 */
package graphql.util;

import graphql.Internal;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * An id generator that uses {@link SecureRandom} for the initial seed and
 * {@link Random} thereafter. This provides a better balance between securely random ids and performance.
 *
 * @author Rossen Stoyanchev
 * @author Rob Winch
 */
@Internal
public class IdGenerator {

    private static final IdGenerator idGenerator = new IdGenerator();

    public static UUID uuid() {
        return idGenerator.generateId();
    }

    private final Random random;


    public IdGenerator() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] seed = new byte[8];
        secureRandom.nextBytes(seed);
        this.random = new Random(new BigInteger(seed).longValue());
    }


    public UUID generateId() {
        byte[] randomBytes = new byte[16];
        this.random.nextBytes(randomBytes);

        long mostSigBits = 0;
        for (int i = 0; i < 8; i++) {
            mostSigBits = (mostSigBits << 8) | (randomBytes[i] & 0xff);
        }

        long leastSigBits = 0;
        for (int i = 8; i < 16; i++) {
            leastSigBits = (leastSigBits << 8) | (randomBytes[i] & 0xff);
        }

        return new UUID(mostSigBits, leastSigBits);
    }

}