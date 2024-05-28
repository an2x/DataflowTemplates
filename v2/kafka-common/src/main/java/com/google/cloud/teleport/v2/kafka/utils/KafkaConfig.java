/*
 * Copyright (C) 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.kafka.utils;

import com.google.cloud.teleport.v2.kafka.options.KafkaReadOptions;
import com.google.cloud.teleport.v2.kafka.options.KafkaWriteOptions;
import com.google.cloud.teleport.v2.kafka.values.KafkaAuthenticationMethod;
import com.google.cloud.teleport.v2.utils.SecretManagerUtils;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;

/**
 * The {@link KafkaConfig} is a utility class for constructing properties for Kafka
 * consumers and producers.
 */
public class KafkaConfig {
  public static Map<String, Object> fromReadOptions(KafkaReadOptions options) throws IOException {
    Map<String, Object> properties = from(
        options.getKafkaReadAuthenticationMode(),
        null,
        null,
        null,
        null,
        null,
        options.getKafkaReadUsernameSecretId(),
        options.getKafkaReadPasswordSecretId());

    properties.putAll(KafkaCommonUtils.configureKafkaOffsetCommit(options));

    return properties;
  }

  public static Map<String, Object> fromWriteOptions(KafkaWriteOptions options) throws IOException {
    return from(
        options.getDestinationAuthenticationMethod(),
        options.getDestinationKeystoreLocation(),
        options.getDestinationTruststoreLocation(),
        options.getDestinationTruststorePasswordSecretId(),
        options.getDestinationKeystorePasswordSecretId(),
        options.getDestinationKeyPasswordSecretId(),
        options.getDestinationUsernameSecretId(),
        options.getDestinationPasswordSecretId());
  }

  public static Map<String, Object> fromReadOptionsWithSslAuth(KafkaReadOptions options, String keystoreLocation, String truststoreLocation,
      String truststorePasswordSecretId, String keystorePasswordSecretId, String keyPasswordSecretId) throws IOException {

    // This is a temporary workaround for the fact that KafkaReadOptions doesn't support SSL authentication mode yet.
    // This method can be removed once SSL parameter are added there, in favor of from(KafkaReadOptions) method.

    Map<String, Object> properties = from(
        options.getKafkaReadAuthenticationMode(),
        keystoreLocation,
        truststoreLocation,
        truststorePasswordSecretId,
        keystorePasswordSecretId,
        keyPasswordSecretId,
        options.getKafkaReadUsernameSecretId(),
        options.getKafkaReadPasswordSecretId());

    properties.putAll(KafkaCommonUtils.configureKafkaOffsetCommit(options));

    return properties;
  }

  private static Map<String, Object> from(String authMode, String keystoreLocation, String truststoreLocation,
      String truststorePasswordSecretId, String keystorePasswordSecretId, String keyPasswordSecretId, String usernameSecretId, String passwordSecretId) throws IOException {
    Map<String, Object> properties = new HashMap<>();
    if (authMode == null) {
      return properties;
    }

    if (authMode.equals(KafkaAuthenticationMethod.SSL)) {
      properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, KafkaAuthenticationMethod.SSL);
      properties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystoreLocation);
      properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststoreLocation);
      properties.put(
          SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
          FileAwareFactoryFn.SECRET_MANAGER_VALUE_PREFIX + truststorePasswordSecretId);
      properties.put(
          SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
          FileAwareFactoryFn.SECRET_MANAGER_VALUE_PREFIX + keystorePasswordSecretId);
      properties.put(
          SslConfigs.SSL_KEY_PASSWORD_CONFIG,
          FileAwareFactoryFn.SECRET_MANAGER_VALUE_PREFIX + keyPasswordSecretId);
      properties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "");
    } else if (authMode.equals(KafkaAuthenticationMethod.SASL_PLAIN)) {
      properties.put(SaslConfigs.SASL_MECHANISM, KafkaAuthenticationMethod.SASL_MECHANISM);
      //         Note: in other languages, set sasl.username and sasl.password instead.
      properties.put(
          CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, KafkaAuthenticationMethod.SASL_PLAIN);
      properties.put(
          SaslConfigs.SASL_JAAS_CONFIG,
          "org.apache.kafka.common.security.plain.PlainLoginModule required"
              + " username=\'"
              + SecretManagerUtils.getSecret(usernameSecretId)
              + "\'"
              + " password=\'"
              + SecretManagerUtils.getSecret(passwordSecretId)
              + "\';");
    } else {
      throw new UnsupportedEncodingException("Authentication method not supported: " + authMode);
    }
    return properties;
  }
}
