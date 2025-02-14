/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.openapi.generator;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.micronaut.openapi.generator.MicronautCodeGeneratorOptionsBuilder.GeneratorLanguage;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;

import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.DefaultCodegen;
import org.openapitools.codegen.DefaultGenerator;

/**
 * Main entry point for Micronaut OpenAPI code generation.
 */
public final class MicronautCodeGeneratorEntryPoint {

    private final URI definitionFile;
    private final File outputDirectory;
    private final DefaultCodegen codeGenerator;
    private final EnumSet<OutputKind> outputs;
    private final Options options;
    private final JavaMicronautServerCodegen.ServerOptions javaServerOptions;
    private final JavaMicronautClientCodegen.ClientOptions javaClientOptions;
    private final KotlinMicronautServerCodegen.ServerOptions kotlinServerOptions;
    private final KotlinMicronautClientCodegen.ClientOptions kotlinClientOptions;

    private MicronautCodeGeneratorEntryPoint(URI definitionFile,
                                             File outputDirectory,
                                             DefaultCodegen codeGenerator,
                                             EnumSet<OutputKind> outputs,
                                             Options options,
                                             JavaMicronautServerCodegen.ServerOptions javaServerOptions,
                                             JavaMicronautClientCodegen.ClientOptions javaClientOptions,
                                             KotlinMicronautServerCodegen.ServerOptions kotlinServerOptions,
                                             KotlinMicronautClientCodegen.ClientOptions kotlinClientOptions
                                             ) {
        this.definitionFile = definitionFile;
        this.outputDirectory = outputDirectory;
        this.codeGenerator = codeGenerator;
        this.outputs = outputs;
        this.options = options;
        this.javaServerOptions = javaServerOptions;
        this.javaClientOptions = javaClientOptions;
        this.kotlinServerOptions = kotlinServerOptions;
        this.kotlinClientOptions = kotlinClientOptions;
    }

    private static void withPath(File file, Consumer<? super String> action) {
        if (file == null) {
            return;
        }
        try {
            String path = file.getCanonicalPath();
            action.accept(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs code generation.
     */
    public void generate() {
        var openAPI = new OpenAPIParser()
            .readLocation(definitionFile.toString(), null, new ParseOptions()).getOpenAPI();

        // Configure codegen
        withPath(outputDirectory, codeGenerator::setOutputDir);

        // Disable timestamps are it makes builds non preproducible
        if (codeGenerator instanceof AbstractMicronautJavaCodegen<?> javaCodegen) {
            javaCodegen.setHideGenerationTimestamp(true);
        } else if (codeGenerator instanceof AbstractMicronautKotlinCodegen<?> kotlinCodegen) {
            kotlinCodegen.setHideGenerationTimestamp(true);
        }

        configureOptions();

        // Create input
        var input = new ClientOptInput();
        input.openAPI(openAPI);
        input.config(codeGenerator);

        // Generate
        DefaultGenerator generator = new DefaultGenerator();
        for (OutputKind outputKind : OutputKind.values()) {
            generator.setGeneratorPropertyDefault(outputKind.generatorProperty, "false");
        }
        for (OutputKind outputKind : outputs) {
            generator.setGeneratorPropertyDefault(outputKind.generatorProperty, "true");
        }

        generator.opts(input).generate();
    }

    private void configureOptions() {
        if (options == null) {
            return;
        }
        if (options.modelPackage != null) {
            codeGenerator.setModelPackage(options.modelPackage);
        }
        if (options.apiPackage != null) {
            codeGenerator.setApiPackage(options.apiPackage);
        }
        if ((options.lang == null || options.lang == GeneratorLanguage.JAVA) && codeGenerator instanceof AbstractMicronautJavaCodegen<?> javaCodeGen) {

            if (options.invokerPackage != null) {
                javaCodeGen.setInvokerPackage(options.invokerPackage);
            }
            if (options.artifactId != null) {
                javaCodeGen.setArtifactId(options.artifactId);
            }
            if (options.parameterMappings != null) {
                javaCodeGen.addParameterMappings(options.parameterMappings);
            }
            if (options.responseBodyMappings != null) {
                javaCodeGen.addResponseBodyMappings(options.responseBodyMappings);
            }
            javaCodeGen.setReactive(options.reactive);
            javaCodeGen.setGenerateHttpResponseAlways(options.generateHttpResponseAlways);
            javaCodeGen.setGenerateHttpResponseWhereRequired(options.generateHttpResponseWhereRequired);
            javaCodeGen.setUseOptional(options.optional);
            javaCodeGen.setUseBeanValidation(options.beanValidation);
            javaCodeGen.setTestTool(options.testFramework.value);
            javaCodeGen.setSerializationLibrary(options.serializationLibraryKind().name());
            javaCodeGen.setDateTimeLibrary(options.dateTimeFormat().name());
            configureJavaServerOptions();
            configureJavaClientOptions();
        } else if (options.lang == GeneratorLanguage.KOTLIN && codeGenerator instanceof AbstractMicronautKotlinCodegen<?> kotlinCodeGen) {

            if (options.invokerPackage != null) {
                kotlinCodeGen.setInvokerPackage(options.invokerPackage);
            }
            if (options.artifactId != null) {
                kotlinCodeGen.setArtifactId(options.artifactId);
            }
            if (options.parameterMappings != null) {
                kotlinCodeGen.addParameterMappings(options.parameterMappings);
            }
            if (options.responseBodyMappings != null) {
                kotlinCodeGen.addResponseBodyMappings(options.responseBodyMappings);
            }
            kotlinCodeGen.setReactive(options.reactive);
            kotlinCodeGen.setGenerateHttpResponseAlways(options.generateHttpResponseAlways);
            kotlinCodeGen.setGenerateHttpResponseWhereRequired(options.generateHttpResponseWhereRequired);
            kotlinCodeGen.setUseBeanValidation(options.beanValidation);
            kotlinCodeGen.setTestTool(options.testFramework.value);
            kotlinCodeGen.setSerializationLibrary(options.serializationLibraryKind().name());
            kotlinCodeGen.setDateTimeLibrary(options.dateTimeFormat().name());
            configureKotlinServerOptions();
            configureKotlinClientOptions();
        }
        codeGenerator.processOpts();
    }

    private void configureJavaServerOptions() {
        if (javaServerOptions != null && codeGenerator instanceof JavaMicronautServerCodegen javaServerCodegen) {
            if (javaServerOptions.controllerPackage() != null) {
                javaServerCodegen.setControllerPackage(javaServerOptions.controllerPackage());
            }
            javaServerCodegen.setGenerateImplementationFiles(javaServerOptions.generateImplementationFiles());
            javaServerCodegen.setGenerateOperationsToReturnNotImplemented(javaServerOptions.generateOperationsToReturnNotImplemented());
            javaServerCodegen.setGenerateControllerFromExamples(javaServerOptions.generateControllerFromExamples());
            javaServerCodegen.setUseAuth(javaServerOptions.useAuth());
            javaServerCodegen.setLombok(javaServerOptions.lombok());
            javaServerCodegen.setPlural(javaServerOptions.plural());
            javaServerCodegen.setFluxForArrays(javaServerOptions.fluxForArrays());
            javaServerCodegen.setGeneratedAnnotation(javaServerOptions.generatedAnnotation());
        }
    }

    public void configureJavaClientOptions() {
        if (javaClientOptions != null && codeGenerator instanceof JavaMicronautClientCodegen javaClientCodegen) {
            if (javaClientOptions.additionalClientTypeAnnotations() != null) {
                javaClientCodegen.setAdditionalClientTypeAnnotations(javaClientOptions.additionalClientTypeAnnotations());
            }
            if (javaClientOptions.clientId() != null) {
                javaClientCodegen.setClientId(javaClientCodegen.clientId);
            }
            if (javaClientOptions.authorizationFilterPattern() != null) {
                javaClientCodegen.setAuthorizationFilterPattern(javaClientCodegen.authorizationFilterPattern);
            }
            if (javaClientOptions.basePathSeparator() != null) {
                javaClientCodegen.setBasePathSeparator(javaClientCodegen.basePathSeparator);
            }
            javaClientCodegen.setConfigureAuthorization(javaClientOptions.useAuth());
            javaClientCodegen.setLombok(javaClientOptions.lombok());
            javaClientCodegen.setPlural(javaClientOptions.plural());
            javaClientCodegen.setFluxForArrays(javaClientOptions.fluxForArrays());
            javaClientCodegen.setGeneratedAnnotation(javaClientOptions.generatedAnnotation());
        }
    }

    private void configureKotlinServerOptions() {
        if (kotlinServerOptions != null && codeGenerator instanceof KotlinMicronautServerCodegen kotlinServerCodegen) {
            if (kotlinServerOptions.controllerPackage() != null) {
                kotlinServerCodegen.setControllerPackage(kotlinServerOptions.controllerPackage());
            }
            kotlinServerCodegen.setGenerateImplementationFiles(kotlinServerOptions.generateImplementationFiles());
            kotlinServerCodegen.setGenerateOperationsToReturnNotImplemented(kotlinServerOptions.generateOperationsToReturnNotImplemented());
            kotlinServerCodegen.setGenerateControllerFromExamples(kotlinServerOptions.generateControllerFromExamples());
            kotlinServerCodegen.setGeneratedAnnotation(kotlinServerOptions.generatedAnnotation());
            kotlinServerCodegen.setKsp(kotlinServerOptions.ksp());
            kotlinServerCodegen.setUseAuth(kotlinServerOptions.useAuth());
            kotlinServerCodegen.setPlural(kotlinServerOptions.plural());
            kotlinServerCodegen.setFluxForArrays(kotlinServerOptions.fluxForArrays());
        }
    }

    public void configureKotlinClientOptions() {
        if (kotlinClientOptions != null && codeGenerator instanceof KotlinMicronautClientCodegen kotlinClientCodegen) {
            if (kotlinClientOptions.additionalClientTypeAnnotations() != null) {
                kotlinClientCodegen.setAdditionalClientTypeAnnotations(kotlinClientOptions.additionalClientTypeAnnotations());
            }
            if (kotlinClientOptions.clientId() != null) {
                kotlinClientCodegen.setClientId(kotlinClientCodegen.clientId);
            }
            if (kotlinClientOptions.authorizationFilterPattern() != null) {
                kotlinClientCodegen.setAuthorizationFilterPattern(kotlinClientCodegen.authorizationFilterPattern);
            }
            if (kotlinClientOptions.basePathSeparator() != null) {
                kotlinClientCodegen.setBasePathSeparator(kotlinClientCodegen.basePathSeparator);
            }
            kotlinClientCodegen.setGeneratedAnnotation(kotlinClientOptions.generatedAnnotation());
            kotlinClientCodegen.setKsp(kotlinClientOptions.ksp());
            kotlinClientCodegen.setConfigureAuthorization(kotlinClientOptions.useAuth());
            kotlinClientCodegen.setPlural(kotlinClientOptions.plural());
            kotlinClientCodegen.setFluxForArrays(kotlinClientOptions.fluxForArrays());
        }
    }

    /**
     * Returns a code generator builder.
     *
     * @return the builder
     */
    public static MicronautCodeGeneratorBuilder builder() {
        return new DefaultBuilder();
    }

    /**
     * The different output kinds that the generator supports.
     */
    public enum OutputKind {
        MODELS(CodegenConstants.MODELS, DefaultBuilder.HAS_OUTPUT),
        MODEL_TESTS(CodegenConstants.MODEL_TESTS, DefaultBuilder.HAS_OUTPUT),
        MODEL_DOCS(CodegenConstants.MODEL_DOCS, DefaultBuilder.HAS_OUTPUT),
        APIS(CodegenConstants.APIS, DefaultBuilder.HAS_OUTPUT),
        API_TESTS(CodegenConstants.API_TESTS, DefaultBuilder.HAS_OUTPUT),
        API_DOCS(CodegenConstants.API_DOCS, DefaultBuilder.HAS_OUTPUT),
        SUPPORTING_FILES(CodegenConstants.SUPPORTING_FILES, DefaultBuilder.HAS_OUTPUT);

        private final String generatorProperty;
        private final Consumer<DefaultBuilder> validationAction;

        OutputKind(String generatorProperty, Consumer<DefaultBuilder> validationAction) {
            this.generatorProperty = generatorProperty;
            this.validationAction = validationAction;
        }

        public static OutputKind of(String name) {
            for (OutputKind kind : values()) {
                if (kind.name().equals(name) || kind.generatorProperty.equals(name)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown output kind '" + name + "'");
        }

        public String getGeneratorProperty() {
            return generatorProperty;
        }
    }

    private static class DefaultBuilder implements MicronautCodeGeneratorBuilder {

        private static final Consumer<DefaultBuilder> HAS_OUTPUT = b -> Objects.requireNonNull(b.outputDirectory, "Sources directory must not be null");

        private Options options;
        private DefaultCodegen codeGenerator;
        private URI definitionFile;
        private File outputDirectory;
        private final EnumSet<OutputKind> outputs = EnumSet.noneOf(OutputKind.class);
        private JavaMicronautServerCodegen.ServerOptions javaServerOptions;
        private JavaMicronautClientCodegen.ClientOptions javaClientOptions;
        private KotlinMicronautServerCodegen.ServerOptions kotlinServerOptions;
        private KotlinMicronautClientCodegen.ClientOptions kotlinClientOptions;

        @Override
        public <B extends GeneratorOptionsBuilder, G extends MicronautCodeGenerator<B>> MicronautCodeGeneratorBuilder forCodeGenerator(G generator, Consumer<? super B> configuration) {
            codeGenerator = (DefaultCodegen) generator;
            var builder = generator.optionsBuilder();
            configuration.accept(builder);
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder forJavaClient(Consumer<? super JavaMicronautClientOptionsBuilder> clientOptionsSpec) {
            codeGenerator = new JavaMicronautClientCodegen();
            var clientOptionsBuilder = new JavaMicronautClientCodegen.DefaultClientOptionsBuilder();
            clientOptionsSpec.accept(clientOptionsBuilder);
            javaClientOptions = clientOptionsBuilder.build();
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder forJavaServer(Consumer<? super JavaMicronautServerOptionsBuilder> serverOptionsSpec) {
            codeGenerator = new JavaMicronautServerCodegen();
            var serverOptionsBuilder = new JavaMicronautServerCodegen.DefaultServerOptionsBuilder();
            serverOptionsSpec.accept(serverOptionsBuilder);
            javaServerOptions = serverOptionsBuilder.build();
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder forKotlinClient(Consumer<? super KotlinMicronautClientOptionsBuilder> clientOptionsSpec) {
            codeGenerator = new KotlinMicronautClientCodegen();
            var clientOptionsBuilder = new KotlinMicronautClientCodegen.DefaultClientOptionsBuilder();
            clientOptionsSpec.accept(clientOptionsBuilder);
            kotlinClientOptions = clientOptionsBuilder.build();
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder forKotlinServer(Consumer<? super KotlinMicronautServerOptionsBuilder> serverOptionsSpec) {
            codeGenerator = new KotlinMicronautServerCodegen();
            var serverOptionsBuilder = new KotlinMicronautServerCodegen.DefaultServerOptionsBuilder();
            serverOptionsSpec.accept(serverOptionsBuilder);
            kotlinServerOptions = serverOptionsBuilder.build();
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder withDefinitionFile(URI definitionFile) {
            this.definitionFile = definitionFile;
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder withOutputDirectory(File outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder withOutputs(OutputKind... elements) {
            outputs.addAll(Arrays.asList(elements));
            return this;
        }

        @Override
        public MicronautCodeGeneratorBuilder withOptions(Consumer<? super MicronautCodeGeneratorOptionsBuilder> optionsConfigurer) {
            DefaultOptionsBuilder builder = new DefaultOptionsBuilder();
            optionsConfigurer.accept(builder);
            options = builder.build();
            return this;
        }

        private void validate() {
            Objects.requireNonNull(definitionFile, "OpenAPI definition file must not be null");
            Objects.requireNonNull(codeGenerator, "You must select either server or client generation");
            for (OutputKind output : outputs) {
                output.validationAction.accept(this);
            }
        }

        @Override
        public MicronautCodeGeneratorEntryPoint build() {
            validate();
            return new MicronautCodeGeneratorEntryPoint(definitionFile,
                outputDirectory,
                codeGenerator,
                outputs,
                options,
                javaServerOptions,
                javaClientOptions,
                kotlinServerOptions,
                kotlinClientOptions
            );
        }

        private static class DefaultOptionsBuilder implements MicronautCodeGeneratorOptionsBuilder {

            private String apiPackage;
            private String artifactId;
            private boolean beanValidation = true;
            private String invokerPackage;
            private String modelPackage;
            private List<ParameterMapping> parameterMappings;
            private List<ResponseBodyMapping> responseBodyMappings;
            private boolean optional;
            private boolean reactive = true;
            private boolean generateHttpResponseAlways;
            private boolean generateHttpResponseWhereRequired = true;
            private TestFramework testFramework = TestFramework.JUNIT5;
            private SerializationLibraryKind serializationLibraryKind = SerializationLibraryKind.MICRONAUT_SERDE_JACKSON;
            private DateTimeFormat dateTimeFormat = DateTimeFormat.ZONED_DATETIME;
            private GeneratorLanguage lang = GeneratorLanguage.JAVA;

            @Override
            public MicronautCodeGeneratorOptionsBuilder withLang(GeneratorLanguage lang) {
                this.lang = lang;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withApiPackage(String apiPackage) {
                this.apiPackage = apiPackage;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withInvokerPackage(String invokerPackage) {
                this.invokerPackage = invokerPackage;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withModelPackage(String modelPackage) {
                this.modelPackage = modelPackage;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withArtifactId(String artifactId) {
                this.artifactId = artifactId;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withParameterMappings(List<ParameterMapping> parameterMappings) {
                this.parameterMappings = parameterMappings;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withResponseBodyMappings(List<ResponseBodyMapping> responseBodyMappings) {
                this.responseBodyMappings = responseBodyMappings;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withReactive(boolean reactive) {
                this.reactive = reactive;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withGenerateHttpResponseAlways(boolean generateHttpResponseAlways) {
                this.generateHttpResponseAlways = generateHttpResponseAlways;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withGenerateHttpResponseWhereRequired(boolean generateHttpResponseWhereRequired) {
                this.generateHttpResponseWhereRequired = generateHttpResponseWhereRequired;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withBeanValidation(boolean beanValidation) {
                this.beanValidation = beanValidation;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withOptional(boolean optional) {
                this.optional = optional;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withTestFramework(TestFramework testFramework) {
                this.testFramework = testFramework;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withSerializationLibrary(SerializationLibraryKind library) {
                serializationLibraryKind = library;
                return this;
            }

            @Override
            public MicronautCodeGeneratorOptionsBuilder withDateTimeFormat(DateTimeFormat format) {
                dateTimeFormat = format;
                return this;
            }

            private Options build() {
                return new Options(lang, apiPackage, modelPackage, invokerPackage, artifactId, parameterMappings, responseBodyMappings, beanValidation, optional, reactive, generateHttpResponseAlways, generateHttpResponseWhereRequired, testFramework, serializationLibraryKind, dateTimeFormat);
            }
        }
    }

    /**
     * The different test frameworks which are supported
     * by this generator.
     */
    public enum TestFramework {

        JUNIT5(AbstractMicronautJavaCodegen.OPT_TEST_JUNIT),
        SPOCK(AbstractMicronautJavaCodegen.OPT_TEST_SPOCK);

        private final String value;

        TestFramework(String value) {
            this.value = value;
        }
    }

    private record Options(
        GeneratorLanguage lang,
        String apiPackage,
        String modelPackage,
        String invokerPackage,
        String artifactId,
        List<ParameterMapping> parameterMappings,
        List<ResponseBodyMapping> responseBodyMappings,
        boolean beanValidation,
        boolean optional,
        boolean reactive,
        boolean generateHttpResponseAlways,
        boolean generateHttpResponseWhereRequired,
        TestFramework testFramework,
        SerializationLibraryKind serializationLibraryKind,
        MicronautCodeGeneratorOptionsBuilder.DateTimeFormat dateTimeFormat
    ) {
    }

}
