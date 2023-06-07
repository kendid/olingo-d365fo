/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.kendid;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

import org.apache.http.HttpHeaders;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.olingo.client.core.http.AbstractOAuth2HttpClientFactory;
import org.apache.olingo.client.core.http.OAuth2Exception;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IClientCredential;

public class AzureADFactory extends AbstractOAuth2HttpClientFactory {
    private String token;

    private final String clientId;
    private final String clientSecret;
    private final String authority;
    private final Set<String> scope;

    public AzureADFactory(final String authority, final String clientId, final String clientSecret,
                          final String theD365url) {
        super(URI.create(authority + "/oauth2/authorize"), URI.create(authority + "/oauth2/token"));
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authority = authority + "/";
        this.scope = Collections.singleton(theD365url + "/.default");
    }

    @Override
    protected boolean isInited() throws OAuth2Exception {
        return token != null;
    }

    @Override
    protected void init() throws OAuth2Exception {
        final IAuthenticationResult result;
        try {
            result = acquireToken();
        } catch (MalformedURLException ex) {
            throw new OAuth2Exception(ex);
        }
        token = result.accessToken();
    }

    @Override
    protected void accessToken(final DefaultHttpClient client) throws OAuth2Exception {
        client.addRequestInterceptor((request, context) -> {
            request.removeHeaders(HttpHeaders.AUTHORIZATION);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        });
    }

    @Override
    protected void refreshToken(final DefaultHttpClient client) throws OAuth2Exception {
        final IAuthenticationResult result;
        try {
            result = acquireToken();
        } catch (MalformedURLException ex) {
            throw new OAuth2Exception(ex);
        }
        token = result.accessToken();
    }

    private IAuthenticationResult acquireToken() throws MalformedURLException {
        IClientCredential credential = ClientCredentialFactory.createFromSecret(clientSecret);
        ConfidentialClientApplication cca =
                ConfidentialClientApplication
                        .builder(clientId, credential)
                        .authority(authority)
                        .build();

        ClientCredentialParameters parameters =
                ClientCredentialParameters
                        .builder(scope)
                        .build();

        return cca.acquireToken(parameters).join();
    }
}
