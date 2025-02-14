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

import java.util.Arrays;
import java.util.List;

import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenType;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.meta.GeneratorMetadata;
import org.openapitools.codegen.meta.Stability;

/**
 * The generator for creating Micronaut clients.
 */
@SuppressWarnings("checkstyle:DesignForExtension")
public class KotlinMicronautClientCodegen extends AbstractMicronautKotlinCodegen<KotlinMicronautClientOptionsBuilder> {

    public static final String OPT_CONFIGURE_AUTH = "configureAuth";
    public static final String OPT_CONFIGURE_AUTH_FILTER_PATTERN = "configureAuthFilterPattern";
    public static final String OPT_CONFIGURE_CLIENT_ID = "configureClientId";
    public static final String ADDITIONAL_CLIENT_TYPE_ANNOTATIONS = "additionalClientTypeAnnotations";
    public static final String AUTHORIZATION_FILTER_PATTERN = "authorizationFilterPattern";
    public static final String BASE_PATH_SEPARATOR = "basePathSeparator";
    public static final String CLIENT_ID = "clientId";

    public static final String NAME = "kotlin-micronaut-client";

    protected boolean configureAuthorization;
    protected List<String> additionalClientTypeAnnotations;
    protected String authorizationFilterPattern;
    protected String basePathSeparator = "-";
    protected String clientId;

    KotlinMicronautClientCodegen() {

        title = "OpenAPI Micronaut Client";

        generatorMetadata = GeneratorMetadata.newBuilder(generatorMetadata)
            .stability(Stability.BETA)
            .build();
        additionalProperties.put("client", "true");

        cliOptions.add(CliOption.newBoolean(OPT_CONFIGURE_AUTH, "Configure all the authorization methods as specified in the file", configureAuthorization));
        cliOptions.add(CliOption.newString(ADDITIONAL_CLIENT_TYPE_ANNOTATIONS, "Additional annotations for client type(class level annotations). List separated by semicolon(;) or new line (Linux or Windows)"));
        cliOptions.add(CliOption.newString(AUTHORIZATION_FILTER_PATTERN, "Configure the authorization filter pattern for the client. Generally defined when generating clients from multiple specification files"));
        cliOptions.add(CliOption.newString(BASE_PATH_SEPARATOR, "Configure the separator to use between the application name and base path when referencing the property").defaultValue(basePathSeparator));
        cliOptions.add(CliOption.newString(CLIENT_ID, "Configure the service ID for the Client"));

        typeMapping.put("file", "ByteArray");
        typeMapping.put("responseFile", "InputStream");
        importMapping.put("InputStream", "java.io.InputStream");
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getHelp() {
        return "Generates a Java Micronaut Client.";
    }

    public boolean isConfigureAuthorization() {
        return configureAuthorization;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(OPT_CONFIGURE_AUTH)) {
            configureAuthorization = convertPropertyToBoolean(OPT_CONFIGURE_AUTH);
        }
        writePropertyBack(OPT_CONFIGURE_AUTH, configureAuthorization);

        // Write property that is present in server
        writePropertyBack(OPT_USE_AUTH, true);

        writePropertyBack(OPT_CONFIGURE_AUTH_FILTER_PATTERN, false);
        writePropertyBack(OPT_CONFIGURE_CLIENT_ID, false);

        final String invokerFolder = (sourceFolder + '/' + packageName).replace(".", "/");

        // Authorization files
        if (configureAuthorization) {
            final String authFolder = invokerFolder + "/auth";
            supportingFiles.add(new SupportingFile("client/auth/Authorization.mustache", authFolder, "Authorization.kt"));
            supportingFiles.add(new SupportingFile("client/auth/AuthorizationBinder.mustache", authFolder, "AuthorizationBinder.kt"));
            supportingFiles.add(new SupportingFile("client/auth/Authorizations.mustache", authFolder, "Authorizations.kt"));
            supportingFiles.add(new SupportingFile("client/auth/AuthorizationFilter.mustache", authFolder, "AuthorizationFilter.kt"));
            final String authConfigurationFolder = authFolder + "/configuration";
            supportingFiles.add(new SupportingFile("client/auth/configuration/ApiKeyAuthConfiguration.mustache", authConfigurationFolder, "ApiKeyAuthConfiguration.kt"));
            supportingFiles.add(new SupportingFile("client/auth/configuration/ConfigurableAuthorization.mustache", authConfigurationFolder, "ConfigurableAuthorization.kt"));
            supportingFiles.add(new SupportingFile("client/auth/configuration/HttpBasicAuthConfiguration.mustache", authConfigurationFolder, "HttpBasicAuthConfiguration.kt"));

            var authorizationFilterPattern = additionalProperties.get(AUTHORIZATION_FILTER_PATTERN);
            if (authorizationFilterPattern != null) {
                this.authorizationFilterPattern = authorizationFilterPattern.toString();
            }
            if (this.authorizationFilterPattern != null) {
                writePropertyBack(OPT_CONFIGURE_AUTH_FILTER_PATTERN, true);
            }
            writePropertyBack(AUTHORIZATION_FILTER_PATTERN, this.authorizationFilterPattern);
        }

        Object additionalClientAnnotations = additionalProperties.get(ADDITIONAL_CLIENT_TYPE_ANNOTATIONS);
        if (additionalClientAnnotations != null) {
            if (additionalClientAnnotations instanceof @SuppressWarnings("rawtypes") List additionalClientAnnotationsAsList) {
                //noinspection unchecked
                additionalClientTypeAnnotations = additionalClientAnnotationsAsList;
            } else {
                additionalClientTypeAnnotations = Arrays.asList(additionalClientAnnotations.toString().trim().split("\\s*(;|\\r?\\n)\\s*"));
            }
        }
        writePropertyBack(ADDITIONAL_CLIENT_TYPE_ANNOTATIONS, additionalClientTypeAnnotations);

        var clientId = additionalProperties.get(CLIENT_ID);
        if (clientId != null) {
            this.clientId = clientId.toString();
        }
        if (this.clientId != null) {
            writePropertyBack(OPT_CONFIGURE_CLIENT_ID, true);
            writePropertyBack(CLIENT_ID, this.clientId);
        }

        var basePathSeparator = additionalProperties.get(BASE_PATH_SEPARATOR);
        if (basePathSeparator != null) {
            this.basePathSeparator = basePathSeparator.toString();
        }
        writePropertyBack(BASE_PATH_SEPARATOR, this.basePathSeparator);

        // Api file
        apiTemplateFiles.clear();
        apiTemplateFiles.put("client/api.mustache", ".kt");

        // Add test files
        apiTestTemplateFiles.clear();
        if (testTool.equals(OPT_TEST_JUNIT)) {
            apiTestTemplateFiles.put("client/test/api_test.mustache", ".kt");
        }

        // Add documentation files
        supportingFiles.add(new SupportingFile("client/doc/README.mustache", "", "README.md").doNotOverwrite());
        supportingFiles.add(new SupportingFile("client/doc/auth.mustache", apiDocPath, "auth.md"));
        apiDocTemplateFiles.clear();
        apiDocTemplateFiles.put("client/doc/api_doc.mustache", ".md");
    }

    @Override
    public boolean isServer() {
        return false;
    }

    public void setAdditionalClientTypeAnnotations(final List<String> additionalClientTypeAnnotations) {
        this.additionalClientTypeAnnotations = additionalClientTypeAnnotations;
    }

    public void setAuthorizationFilterPattern(final String authorizationFilterPattern) {
        this.authorizationFilterPattern = authorizationFilterPattern;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    public void setBasePathSeparator(final String basePathSeparator) {
        this.basePathSeparator = basePathSeparator;
    }

    public void setConfigureAuthorization(boolean configureAuthorization) {
        this.configureAuthorization = configureAuthorization;
    }

    @Override
    public KotlinMicronautClientOptionsBuilder optionsBuilder() {
        return new DefaultClientOptionsBuilder();
    }

    static class DefaultClientOptionsBuilder implements KotlinMicronautClientOptionsBuilder {

        private List<String> additionalClientTypeAnnotations;
        private String authorizationFilterPattern;
        private String basePathSeparator;
        private String clientId;
        private boolean plural;
        private boolean useAuth;
        private boolean fluxForArrays;
        private boolean generatedAnnotation = true;
        private boolean ksp;

        @Override
        public KotlinMicronautClientOptionsBuilder withAuthorization(boolean useAuth) {
            this.useAuth = useAuth;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withAuthorizationFilterPattern(String authorizationFilterPattern) {
            this.authorizationFilterPattern = authorizationFilterPattern;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withAdditionalClientTypeAnnotations(List<String> additionalClientTypeAnnotations) {
            this.additionalClientTypeAnnotations = additionalClientTypeAnnotations;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withBasePathSeparator(String basePathSeparator) {
            this.basePathSeparator = basePathSeparator;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withFluxForArrays(boolean fluxForArrays) {
            this.fluxForArrays = fluxForArrays;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withPlural(boolean plural) {
            this.plural = plural;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withGeneratedAnnotation(boolean generatedAnnotation) {
            this.generatedAnnotation = generatedAnnotation;
            return this;
        }

        @Override
        public KotlinMicronautClientOptionsBuilder withKsp(boolean ksp) {
            this.ksp = ksp;
            return this;
        }

        ClientOptions build() {
            return new ClientOptions(
                additionalClientTypeAnnotations,
                authorizationFilterPattern,
                basePathSeparator,
                clientId,
                useAuth,
                plural,
                fluxForArrays,
                generatedAnnotation,
                ksp
            );
        }
    }

    record ClientOptions(
        List<String> additionalClientTypeAnnotations,
        String authorizationFilterPattern,
        String basePathSeparator,
        String clientId,
        boolean useAuth,
        boolean plural,
        boolean fluxForArrays,
        boolean generatedAnnotation,
        boolean ksp
    ) {
    }
}
