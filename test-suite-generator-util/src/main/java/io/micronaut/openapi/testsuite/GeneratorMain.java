/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.openapi.testsuite;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.openapi.generator.MicronautCodeGeneratorEntryPoint;
import io.micronaut.openapi.generator.MicronautCodeGeneratorOptionsBuilder.GeneratorLanguage;
import io.micronaut.openapi.generator.ParameterMapping;
import io.micronaut.openapi.generator.ResponseBodyMapping;

import static io.micronaut.openapi.generator.MicronautCodeGeneratorOptionsBuilder.GeneratorLanguage.JAVA;

/**
 * An entry point to be used in tests, to simulate
 * what the Micronaut OpenAPI Gradle plugin would do
 */
public class GeneratorMain {

    /**
     * The main executable.
     *
     * @param args The argument array, consisting of:
     *     <ol>
     *         <li>Server or client boolean.</li>
     *         <li>The definition file path.</li>
     *         <li>The output directory.</li>
     *         <li>A comma-separated list of output kinds.</li>
     *     </ol>
     *
     * @throws URISyntaxException In case definition file path is incorrect.
     */
    public static void main(String[] args) throws URISyntaxException {
        boolean server = "server".equals(args[0]);
        var lang = GeneratorLanguage.valueOf(args[6].toUpperCase());
        var generatedAnnotation = Boolean.parseBoolean(args[7]);
        var ksp = Boolean.parseBoolean(args[8]);
        List<ParameterMapping> parameterMappings =
            parseParameterMappings(args[4]);
        List<ResponseBodyMapping> responseBodyMappings =
            parseResponseBodyMappings(args[5]);

        MicronautCodeGeneratorEntryPoint.OutputKind[] outputKinds
            = Arrays.stream(args[3].split(","))
            .map(MicronautCodeGeneratorEntryPoint.OutputKind::of)
            .toArray(MicronautCodeGeneratorEntryPoint.OutputKind[]::new);

        var builder = MicronautCodeGeneratorEntryPoint.builder()
            .withDefinitionFile(new URI(args[1]))
            .withOutputDirectory(new File(args[2]))
            .withOutputs(outputKinds)
            .withOptions(options -> {
                options.withLang(lang)
                    .withInvokerPackage("io.micronaut.openapi.test")
                    .withApiPackage("io.micronaut.openapi.test.api")
                    .withModelPackage("io.micronaut.openapi.test.model")
                    .withBeanValidation(true)
                    .withOptional(true)
                    .withReactive(true)
                    .withTestFramework(lang == JAVA ? MicronautCodeGeneratorEntryPoint.TestFramework.SPOCK : MicronautCodeGeneratorEntryPoint.TestFramework.JUNIT5)
                    .withParameterMappings(parameterMappings)
                    .withResponseBodyMappings(responseBodyMappings);
            });
        if (server) {
            if (lang == GeneratorLanguage.KOTLIN) {
                builder.forKotlinServer(serverOptions -> {
                    serverOptions.withControllerPackage("io.micronaut.openapi.test.controller");
                    // commented out because currently this would prevent the test project from compiling
                    // because we generate both abstract classes _and_ dummy implementations
                    serverOptions.withGenerateImplementationFiles(false)
                        .withAuthentication(false)
                        .withKsp(ksp)
                        .withGeneratedAnnotation(generatedAnnotation);
                });
            } else {
                builder.forJavaServer(serverOptions -> {
                    serverOptions.withControllerPackage("io.micronaut.openapi.test.controller");
                    // commented out because currently this would prevent the test project from compiling
                    // because we generate both abstract classes _and_ dummy implementations
                    serverOptions.withGenerateImplementationFiles(false)
                        .withAuthentication(false)
                        .withGeneratedAnnotation(generatedAnnotation);
                });
            }
        } else {
            if (lang == GeneratorLanguage.KOTLIN) {
                builder.forKotlinClient(client -> {
                    client.withGeneratedAnnotation(generatedAnnotation)
                        .withKsp(ksp);
                });
            } else {
                builder.forJavaClient(client -> {
                    client.withGeneratedAnnotation(generatedAnnotation);
                });
            }
        }
        builder.build().generate();
    }

    private static List<ParameterMapping> parseParameterMappings(String string) {
        return parseListOfMaps(string).stream().map(map -> new ParameterMapping(
            map.get("name"),
            ParameterMapping.ParameterLocation.valueOf(map.get("location")),
            map.get("mappedType"),
            map.get("mappedName"),
            "true".equals(map.get("isValidated"))
        )).toList();
    }

    private static List<ResponseBodyMapping> parseResponseBodyMappings(String string) {
        return parseListOfMaps(string).stream().map(map -> new ResponseBodyMapping(
            map.get("headerName"),
            map.get("mappedBodyType"),
            "true".equals(map.get("isListWrapper")),
            "true".equals(map.get("isValidated"))
        )).toList();
    }

    private static List<Map<String, String>> parseListOfMaps(String string) {
        List<Map<String, String>> result = new ArrayList<>();
        if (string.isBlank()) {
            return result;
        }

        assert string.charAt(0) == '[';
        int i = 1;

        while (string.charAt(i) != ']') {
            if (string.charAt(i) == ' ') {
                ++i;
            }

            assert string.charAt(i) == '{';
            ++i;

            Map<String, String> map = new HashMap<>();
            result.add(map);
            int endIndex = string.indexOf('}', i);

            while (i < endIndex) {
                if (string.charAt(i) == ' ') {
                    ++i;
                }
                int nameIndex = string.indexOf('=', i);
                String name = string.substring(i, nameIndex);
                i = nameIndex + 1;
                int valueIndex = string.indexOf(',', i);
                if (endIndex < valueIndex || valueIndex == -1) {
                    valueIndex = endIndex;
                }
                String value = string.substring(i, valueIndex);
                i = valueIndex + 1;

                map.put(name, value);
            }

            if (i != string.length() - 1) {
                assert string.charAt(i) == ',';
                ++i;
            }
        }
        assert i == string.length() - 1;

        return result;
    }
}
