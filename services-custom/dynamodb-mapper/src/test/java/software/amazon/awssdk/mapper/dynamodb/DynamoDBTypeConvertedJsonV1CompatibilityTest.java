/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.mapper.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.mapper.dynamodb.jsonfixtures.JsonModelFixtures;
import software.amazon.awssdk.mapper.dynamodb.jsonfixtures.JsonModelFixtures.ReadCase;

/**
 * Checks {@link DynamoDBTypeConvertedJson.Converter} against the v1 mapper's own converter,
 * {@code com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson.Converter} from
 * {@code aws-java-sdk-dynamodb}, so that the JSON already stored by v1 applications stays compatible.
 */
class DynamoDBTypeConvertedJsonV1CompatibilityTest {

    /** Used only to re-serialize two object graphs read from the same JSON into comparable form. */
    private static final ObjectMapper COMPARATOR = new ObjectMapper();

    static Stream<Object> writeCases() {
        return JsonModelFixtures.writeCorpus().stream();
    }

    static Stream<ReadCase> readCases() {
        return JsonModelFixtures.readCorpus().stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("writeCases")
    void convert_matchesV1Converter(Object value) {
        assertThat(v2Convert(value)).isEqualTo(v1Convert(value));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("readCases")
    void unconvert_matchesV1Converter(ReadCase readCase) throws Exception {
        Object v2 = v2Unconvert(readCase.json(), readCase.targetType());
        Object v1 = v1Unconvert(readCase.json(), readCase.targetType());

        assertThat(COMPARATOR.writeValueAsString(v2)).isEqualTo(COMPARATOR.writeValueAsString(v1));
    }

    @Test
    void v1Converter_isLoadedFromV1Sdk() {
        assertThat(com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson.Converter.class
                       .getProtectionDomain().getCodeSource().getLocation().toString())
            .contains("aws-java-sdk-dynamodb");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String v2Convert(Object value) {
        return new DynamoDBTypeConvertedJson.Converter(typeOf(value), Holder.V2).convert(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object v2Unconvert(String json, Class<?> targetType) {
        return new DynamoDBTypeConvertedJson.Converter(targetType, Holder.V2).unconvert(json);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String v1Convert(Object value) {
        return (String) new com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson.Converter(
            typeOf(value), Holder.V1).convert(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object v1Unconvert(String json, Class<?> targetType) {
        return new com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson.Converter(
            targetType, Holder.V1).unconvert(json);
    }

    private static Class<?> typeOf(Object value) {
        return value == null ? Object.class : value.getClass();
    }

    /**
     * Source of real annotation instances. Both converters read {@code targetType()} from the annotation, and the
     * default {@code void.class} makes them use the class passed to the constructor, as the mapper does.
     */
    private static final class Holder {
        static final DynamoDBTypeConvertedJson V2 = annotation("v2", DynamoDBTypeConvertedJson.class);
        static final com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson V1 =
            annotation("v1", com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson.class);

        @DynamoDBTypeConvertedJson
        private Object v2;

        @com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedJson
        private Object v1;

        private static <A extends java.lang.annotation.Annotation> A annotation(String field, Class<A> type) {
            try {
                return Holder.class.getDeclaredField(field).getAnnotation(type);
            } catch (NoSuchFieldException e) {
                throw new AssertionError(e);
            }
        }
    }
}
