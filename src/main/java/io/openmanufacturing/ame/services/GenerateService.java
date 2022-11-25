/*
 * Copyright (c) 2022 Robert Bosch Manufacturing Solutions GmbH
 *
 * See the AUTHORS file(s) distributed with this work for
 * additional information regarding authorship.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package io.openmanufacturing.ame.services;

import static io.openmanufacturing.ame.services.utils.ModelUtils.inMemoryStrategy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import io.openmanufacturing.ame.config.ApplicationSettings;
import io.openmanufacturing.ame.exceptions.InvalidAspectModelException;
import io.openmanufacturing.ame.resolver.inmemory.InMemoryStrategy;
import io.openmanufacturing.ame.services.utils.ModelUtils;
import io.openmanufacturing.sds.aspectmodel.generator.docu.AspectModelDocumentationGenerator;
import io.openmanufacturing.sds.aspectmodel.generator.json.AspectModelJsonPayloadGenerator;
import io.openmanufacturing.sds.aspectmodel.generator.jsonschema.AspectModelJsonSchemaGenerator;
import io.openmanufacturing.sds.aspectmodel.generator.openapi.AspectModelOpenApiGenerator;
import io.openmanufacturing.sds.aspectmodel.generator.openapi.PagingOption;
import io.openmanufacturing.sds.aspectmodel.resolver.services.DataType;
import io.openmanufacturing.sds.aspectmodel.resolver.services.VersionedModel;
import io.openmanufacturing.sds.metamodel.Aspect;
import io.openmanufacturing.sds.metamodel.loader.AspectLoadingException;
import io.openmanufacturing.sds.metamodel.loader.AspectModelLoader;
import io.vavr.control.Try;

@Service
public class GenerateService {
   private static final Logger LOG = LoggerFactory.getLogger( GenerateService.class );
   private static final String COULD_NOT_LOAD_ASPECT = "Could not load Aspect";
   private static final String COULD_NOT_LOAD_ASPECT_MODEL = "Could not load Aspect model, please make sure the model is valid.";

   public GenerateService() {
      DataType.setupTypeMapping();
   }

   public byte[] generateHtmlDocument( final String aspectModel ) throws IOException {
      final Optional<String> storagePath = Optional.of( ApplicationSettings.getMetaModelStoragePath() );
      final AspectModelDocumentationGenerator generator = new AspectModelDocumentationGenerator(
            getVersionModel( aspectModel, storagePath ).get() );
      final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

      generator.generate( artifactName -> byteArrayOutputStream,
            ImmutableMap.<AspectModelDocumentationGenerator.HtmlGenerationOption, String> builder().build() );

      return byteArrayOutputStream.toByteArray();
   }

   public String jsonSchema( final String aspectModel ) {
      try {
         final Optional<String> storagePath = Optional.of( ApplicationSettings.getMetaModelStoragePath() );
         final Aspect aspect = AspectModelLoader.fromVersionedModel( getVersionModel( aspectModel, storagePath ).get() )
                                                .getOrElseThrow( e -> {
                                                   LOG.error( COULD_NOT_LOAD_ASPECT_MODEL );
                                                   return new InvalidAspectModelException( COULD_NOT_LOAD_ASPECT_MODEL,
                                                         e );
                                                } );

         final AspectModelJsonSchemaGenerator generator = new AspectModelJsonSchemaGenerator();
         final JsonNode jsonSchema = generator.apply( aspect );

         final ByteArrayOutputStream out = new ByteArrayOutputStream();
         final ObjectMapper objectMapper = new ObjectMapper();

         objectMapper.writerWithDefaultPrettyPrinter().writeValue( out, jsonSchema );

         return out.toString();
      } catch ( final IOException e ) {
         LOG.error( "Aspect Model could not be loaded correctly." );
         throw new InvalidAspectModelException( COULD_NOT_LOAD_ASPECT, e );
      }
   }

   public String sampleJSONPayload( final String aspectModel ) {
      try {
         final Optional<String> storagePath = Optional.of( ApplicationSettings.getMetaModelStoragePath() );
         final AspectModelJsonPayloadGenerator generator = new AspectModelJsonPayloadGenerator(
               getVersionModel( aspectModel, storagePath ).get() );

         return generator.generateJson();
      } catch ( final AspectLoadingException e ) {
         LOG.error( COULD_NOT_LOAD_ASPECT_MODEL );
         throw new InvalidAspectModelException( COULD_NOT_LOAD_ASPECT_MODEL, e );
      } catch ( final IOException e ) {
         LOG.error( "Aspect Model could not be loaded correctly." );
         throw new InvalidAspectModelException( COULD_NOT_LOAD_ASPECT, e );
      }
   }

   private Try<VersionedModel> getVersionModel( final String aspectModel, final Optional<String> storagePath ) {
      final InMemoryStrategy inMemoryStrategy = inMemoryStrategy( aspectModel,
            storagePath.orElse( ApplicationSettings.getMetaModelStoragePath() ) );
      return ModelUtils.fetchVersionModel( inMemoryStrategy );
   }

   public String generateYamlOpenApiSpec( final String aspectModel, final String baseUrl, final boolean includeQueryApi,
         final boolean useSemanticVersion, final Optional<PagingOption> pagingOption ) {
      try {
         final AspectModelOpenApiGenerator generator = new AspectModelOpenApiGenerator();

         return generator.applyForYaml( ModelUtils.resolveAspectFromModel( aspectModel ), useSemanticVersion, baseUrl,
               Optional.empty(), Optional.empty(), includeQueryApi, pagingOption );
      } catch ( final IOException e ) {
         LOG.error( "YAML OpenAPI specification could not be generated." );
         throw new InvalidAspectModelException( "Error generating YAML OpenAPI specification", e );
      }
   }

   public String generateJsonOpenApiSpec( final String aspectModel, final String baseUrl,
         final boolean includeQueryApi, final boolean useSemanticVersion, final Optional<PagingOption> pagingOption ) {
      try {
         final AspectModelOpenApiGenerator generator = new AspectModelOpenApiGenerator();

         final JsonNode json = generator.applyForJson( ModelUtils.resolveAspectFromModel( aspectModel ),
               useSemanticVersion, baseUrl, Optional.empty(), Optional.empty(), includeQueryApi, pagingOption );

         final ByteArrayOutputStream out = new ByteArrayOutputStream();
         final ObjectMapper objectMapper = new ObjectMapper();

         objectMapper.writerWithDefaultPrettyPrinter().writeValue( out, json );

         return out.toString();
      } catch ( final IOException e ) {
         LOG.error( "JSON OpenAPI specification could not be generated." );
         throw new InvalidAspectModelException( "Error generating JSON OpenAPI specification", e );
      }
   }
}
