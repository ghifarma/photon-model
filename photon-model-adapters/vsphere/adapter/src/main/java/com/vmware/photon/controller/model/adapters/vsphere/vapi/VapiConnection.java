/*
 * Copyright (c) 2015-2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.model.adapters.vsphere.vapi;

import java.io.IOException;
import java.net.URI;

import javax.net.ssl.TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;

import com.vmware.photon.controller.model.adapters.vsphere.util.connection.BasicConnection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.IgnoreSslErrors;

/**
 * Holds enough context to do json rpc calls to a vapi endpoint. The interface is kept similar
 * to {@link com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection}.
 */
public class VapiConnection {
    private final URI uri;
    private String username;
    private String password;
    private HttpClient client;
    private String sessionId;

    public VapiConnection(URI uri) {
        this.uri = uri;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setClient(HttpClient client) {
        this.client = client;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public void close() {
        new SessionClient(this.uri, this.client).logout(this.sessionId);
    }

    public void login() throws IOException, RpcException {
        this.sessionId = new SessionClient(this.uri, this.client)
                .login(this.username, this.password);
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public URI getURI() {
        return this.uri;
    }

    public static HttpClient newUnsecureHttpClient() {
        return HttpClientBuilder.create()
                .setSslcontext(IgnoreSslErrors.newInsecureSslContext("TLS"))
                .setHostnameVerifier(new AllowAllHostnameVerifier())
                .build();
    }

    public static HttpClient newDefaultHttpClient() {
        return HttpClientBuilder
                .create()
                .build();
    }

    private static HttpClient newDefaultHttpClient(TrustManager trustManager) {
        return HttpClientBuilder.create()
                .setSslcontext(IgnoreSslErrors.newTrustedSslContext("TLS", trustManager))
                .setHostnameVerifier(new AllowAllHostnameVerifier())
                .build();
    }

    public HttpClient getClient() {
        return this.client;
    }

    public TaggingClient newTaggingClient() {
        return new TaggingClient(getURI(), getClient(), getSessionId());
    }

    public LibraryClient newLibraryClient() {
        return new LibraryClient(getURI(), getClient(), getSessionId());
    }

    private static URI getVapiUri(URI uri) {
        return URI.create(uri.toString().replace("/sdk", "/api"));
    }

    public static VapiConnection createFromVimConnection(Connection connection) {
        VapiConnection vapiConnection = new VapiConnection(getVapiUri(connection.getURI()));
        vapiConnection.setUsername(connection.getUsername());
        vapiConnection.setPassword(connection.getPassword());

        if (connection instanceof BasicConnection) {
            BasicConnection basicConn = (BasicConnection) connection;
            if (basicConn.isIgnoreSslErrors()) {
                vapiConnection.setClient(VapiConnection.newUnsecureHttpClient());
            } else if (basicConn.getTrustManager() != null) {
                vapiConnection.setClient(VapiConnection.newDefaultHttpClient(basicConn.getTrustManager()));
            } else {
                vapiConnection.setClient(VapiConnection.newDefaultHttpClient());
            }
        }

        return vapiConnection;
    }
}
