/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.security.ssl;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceSubscriptionState.ServiceSubscriber;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Helper class allowing sharing continuous queries between multiple components for performance
 * reasons.
 */
class SslTrustCertificateServiceUtils {
    /**
     * Unique prefix for continuous query tasks. Changes upon restart which is OK because
     * continuous queries are not persisted.
     */
    private static final String QUERY_TASK_SELF_LINK_PREFIX = UUID.randomUUID().toString();

    /**
     * Default query expiration is 10 minutes and cannot be set to infinite. Here we choose a very
     * long expiration period which should be fine for all practical reasons.
     * <p>
     * Note that since local query tasks are not persistent, this expiration interval restarts at
     * every host start.
     */
    private static final long QUERY_TASK_EXPIRATION_DAYS = 5 * 365; // 5 years

    /** Used for the pagination. */
    private static final String PROPERTY_QUERY_SIZE = "SslTrustCertificateServiceUtils.QUERY_SIZE";
    private static final int DEFAULT_QUERY_SIZE = 100;
    private static final int QUERY_RESULT_LIMIT = Integer.getInteger(PROPERTY_QUERY_SIZE, DEFAULT_QUERY_SIZE);


    /**
     * Subscribes a consumer to the given continuous query.
     */
    static void subscribe(ServiceHost host, Consumer<Operation> consumer) {
        QueryTask task = getQueryTask();
        Operation.createPost(host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBody(task)
                .setReferer(host.getUri())
                .setConnectionSharing(true)
                .setCompletion((o, e) -> {
                    if (e != null && o.getStatusCode() != Operation.STATUS_CODE_CONFLICT) {
                        host.log(Level.SEVERE, Utils.toString(e));
                        return;
                    }

                    String taskUriPath = UriUtils.buildUriPath(
                            ServiceUriPaths.CORE_LOCAL_QUERY_TASKS, task.documentSelfLink);
                    Operation subscribePost = Operation.createPost(host, taskUriPath)
                            .setReferer(host.getUri())
                            .setConnectionSharing(true)
                            .setCompletion((op, ex) -> {
                                if (ex != null) {
                                    host.log(Level.SEVERE, Utils.toString(ex));
                                }
                            });

                    host.log(Level.INFO, "Subscribing to a continuous task: %s", taskUriPath);
                    host.startSubscriptionService(subscribePost, consumer,
                            ServiceSubscriber.create(false));
                }).sendWith(host);
    }

    private static QueryTask getQueryTask() {

        Query computeQuery = Query.Builder.create()
                .addKindFieldClause(SslTrustCertificateState.class)
                .build();
        QueryTask task = QueryTask.Builder
                .create()
                .addOption(QueryOption.CONTINUOUS)
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(computeQuery).build();

        task.documentSelfLink = QUERY_TASK_SELF_LINK_PREFIX;
        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(
                TimeUnit.DAYS.toMicros(QUERY_TASK_EXPIRATION_DAYS));

        return task;
    }

    static void loadCertificates(ServiceHost host, Consumer<SslTrustCertificateState> consumer) {
        Query.Builder query = Query.Builder.create()
                .addKindFieldClause(SslTrustCertificateState.class);

        QueryStrategy<SslTrustCertificateState> queryLocalStates = new QueryUtils.QueryByPages<>(
                host,
                query.build(),
                SslTrustCertificateState.class,
                null,
                null)
                .setMaxPageSize(QUERY_RESULT_LIMIT);

        queryLocalStates.queryDocuments(c -> {
            try {
                host.log(Level.FINE, "Processing '%s'.", c);
                SslTrustCertificateState sslTrustCert =
                        Utils.fromJson(c, SslTrustCertificateState.class);
                host.log(Level.FINE,
                        "Certificate with '%s', issuer '%s' and alias '%s' loaded.",
                        sslTrustCert.commonName, sslTrustCert.issuerName,
                        sslTrustCert.getAlias());
                consumer.accept(sslTrustCert);
            } catch (Exception e) {
                host.log(Level.WARNING, "cannot deserialize " + c);
            }
        });
    }
}
