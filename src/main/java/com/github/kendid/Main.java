/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.kendid;

import java.net.URI;
import java.util.List;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetIteratorRequest;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.ClientEntity;
import org.apache.olingo.client.api.domain.ClientEntitySet;
import org.apache.olingo.client.api.domain.ClientEntitySetIterator;
import org.apache.olingo.client.api.http.HttpClientFactory;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmComplexType;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmSchema;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

public class Main {
    /**
     * <p>The base url for the D365 system.</p>
     * Example: {@code https://abcdef01234567890abcdefdevaos.cloudax.dynamics.com}
     */
    public static final String D365_URL = "https:// .cloudax.dynamics.com";
    /**
     * <p>The base URL to retrieve the OAuth2 tokens from. It's put together from the login-domain and the
     * <b>tenant id</b>/<b>directory id</b>.</p>
     *
     * Example: {@code https://login.microsoftonline.com/abcdef01-2345-6789-abcd-ef0123456789}
     */
    public static final String AUTHORITY = "https://login.microsoftonline.com/";
    /**
     * <p>The <b>client id</b> of the Azure AD app registration.</p>
     */
    public static final String CLIENT_ID = "";
    /**
     * <p>The <b>client secret</b> for the Azure AD app registration.</p>
     */
    public static final String CLIENT_SECRET = "";

    private final ODataClient client;

    public Main() {
        client = ODataClientFactory.getClient();
    }

    public static void main(String[] args) {
        final Main app = new Main();
        app.perform(D365_URL + "/data");
    }

    void perform(final String serviceUrl) {
        System.out.println("---------- Read entity data model (EDM) ----------");
        Edm edm = readEdm(serviceUrl);
        final List<FullQualifiedName> complexTypesFqns =
                edm.getSchemas().stream()
                .map(EdmSchema::getComplexTypes)
                .flatMap(complexTypes -> complexTypes.stream().map(EdmComplexType::getFullQualifiedName))
                .toList();
        final List<FullQualifiedName> entityTypesFqns =
                edm.getSchemas().stream()
                .map(EdmSchema::getEntityTypes)
                .flatMap(complexTypes -> complexTypes.stream().map(EdmEntityType::getFullQualifiedName))
                .toList();

        System.out.println("Found # Complex Types: " + complexTypesFqns.size());
        System.out.println("Found # Entity Types: " + entityTypesFqns.size());

        final List<String> entityTypesNamespaces = entityTypesFqns.stream()
                .map(FullQualifiedName::getNamespace)
                .distinct()
                .toList();
        System.out.println("Found namespaces: " + entityTypesNamespaces);

        // From here on we need authentication:
        final HttpClientFactory httpClientFactory =
                new AzureADFactory(AUTHORITY, CLIENT_ID, CLIENT_SECRET, D365_URL);
        client.getConfiguration().setHttpClientFactory(httpClientFactory);

        System.out.println("\n----- Read Entities ------------------------------");
        ClientEntitySetIterator<ClientEntitySet, ClientEntity> iterator =
                readEntities(serviceUrl, "ReleasedProductsV2");

        if (iterator.hasNext()) {
            ClientEntity ce = iterator.next();
            System.out.println("Only first entry:\n" + ce.getProperties());
        }
    }

    Edm readEdm(final String serviceUrl) {
        EdmMetadataRequest request = client.getRetrieveRequestFactory().getMetadataRequest(serviceUrl);
        final ODataRetrieveResponse<Edm> response = request.execute();
        return response.getBody();
    }

    public ClientEntitySetIterator<ClientEntitySet, ClientEntity> readEntities(String serviceUri, String entitySetName) {
        URI absoluteUri = client
                .newURIBuilder(serviceUri)
                .appendEntitySetSegment(entitySetName)
                .addCustomQueryOption("cross-company", "true")
                .build();

        return readEntities(absoluteUri);
    }

    private ClientEntitySetIterator<ClientEntitySet, ClientEntity> readEntities(URI absoluteUri) {
        System.out.println("URI = " + absoluteUri);
        ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> request =
                client.getRetrieveRequestFactory().getEntitySetIteratorRequest(absoluteUri);
        request.setAccept("application/json");
        ODataRetrieveResponse<ClientEntitySetIterator<ClientEntitySet, ClientEntity>> response = request.execute();

        return response.getBody();
    }
}
