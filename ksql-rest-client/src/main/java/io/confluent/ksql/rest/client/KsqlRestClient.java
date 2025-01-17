/*
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.rest.client;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.confluent.ksql.properties.LocalProperties;
import io.confluent.ksql.rest.entity.CommandStatus;
import io.confluent.ksql.rest.entity.CommandStatuses;
import io.confluent.ksql.rest.entity.KsqlEntityList;
import io.confluent.ksql.rest.entity.ServerInfo;
import java.io.Closeable;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class KsqlRestClient implements Closeable {

  private final KsqlClient client;
  private final LocalProperties localProperties;

  private List<URI> serverAddresses;

  /**
   * @param serverAddress the address of the KSQL server to connect to.
   * @param localProps initial set of local properties.
   * @param clientProps properties used to build the client.
   * @param creds optional credentials
   */
  public static KsqlRestClient create(
      final String serverAddress,
      final Map<String, ?> localProps,
      final Map<String, String> clientProps,
      final Optional<BasicCredentials> creds
  ) {
    final LocalProperties localProperties = new LocalProperties(localProps);
    final KsqlClient client = new KsqlClient(clientProps, creds, localProperties);
    return new KsqlRestClient(client, serverAddress, localProperties);
  }

  @VisibleForTesting
  KsqlRestClient(
      final KsqlClient client,
      final String serverAddress,
      final LocalProperties localProps
  ) {
    this.client = requireNonNull(client, "client");
    this.serverAddresses = parseServerAddresses(serverAddress);
    this.localProperties = requireNonNull(localProps, "localProps");
  }

  public URI getServerAddress() {
    return serverAddresses.get(0);
  }

  public void setServerAddress(final String serverAddress) {
    this.serverAddresses = parseServerAddresses(serverAddress);
  }

  public RestResponse<ServerInfo> getServerInfo() {
    return target().getServerInfo();
  }

  public RestResponse<KsqlEntityList> makeKsqlRequest(final String ksql) {
    return target().postKsqlRequest(ksql, Optional.empty());
  }

  public RestResponse<KsqlEntityList> makeKsqlRequest(final String ksql, final Long commandSeqNum) {
    return target().postKsqlRequest(ksql, Optional.ofNullable(commandSeqNum));
  }

  public RestResponse<CommandStatuses> makeStatusRequest() {
    return target().getStatuses();
  }

  public RestResponse<CommandStatus> makeStatusRequest(final String commandId) {
    return target().getStatus(commandId);
  }

  public RestResponse<QueryStream> makeQueryRequest(final String ksql, final Long commandSeqNum) {
    return target().postQueryRequest(ksql, Optional.ofNullable(commandSeqNum));
  }

  public RestResponse<InputStream> makePrintTopicRequest(
      final String ksql,
      final Long commandSeqNum
  ) {
    return target().postPrintTopicRequest(ksql, Optional.ofNullable(commandSeqNum));
  }

  @Override
  public void close() {
    client.close();
  }

  public Object setProperty(final String property, final Object value) {
    return localProperties.set(property, value);
  }

  public Object unsetProperty(final String property) {
    return localProperties.unset(property);
  }

  private KsqlTarget target() {
    return client.target(getServerAddress());
  }

  private static List<URI> parseServerAddresses(final String serverAddresses) {
    requireNonNull(serverAddresses, "serverAddress");
    return ImmutableList.copyOf(
      Arrays.stream(serverAddresses.split(","))
         .map(String::trim)
          .map(KsqlRestClient::parseUri)
         .collect(Collectors.toList()));
  }

  private static URI parseUri(final String serverAddress) {
    try {
      return new URL(serverAddress).toURI();
    } catch (final Exception e) {
      throw new KsqlRestClientException(
          "The supplied serverAddress is invalid: " + serverAddress, e);
    }
  }
}
