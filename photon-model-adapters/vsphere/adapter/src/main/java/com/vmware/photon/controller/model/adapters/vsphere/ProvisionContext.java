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

package com.vmware.photon.controller.model.adapters.vsphere;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputePowerRequest;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationJoin.JoinedCompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 */
public class ProvisionContext {
    public final URI computeReference;
    public final URI provisioningTaskReference;

    public ComputeStateWithDescription parent;
    public ComputeStateWithDescription child;

    public List<DiskState> disks;

    public ResourcePoolState resourcePool;
    public VSphereIOThreadPool pool;
    public AuthCredentialsServiceState vSphereCredentials;
    public Consumer<Throwable> errorHandler;

    public ProvisionContext(ComputeInstanceRequest req) {
        this.computeReference = req.computeReference;
        this.provisioningTaskReference = req.provisioningTaskReference;
    }

    public ProvisionContext(ComputePowerRequest req) {
        this.computeReference = req.computeReference;
        this.provisioningTaskReference = req.provisioningTaskReference;
    }

    /**
     * Populates the given initial context and invoke the onSuccess handler when built. At every step,
     * if failure occurs the ProvisionContext's errorHandler is invoked to cleanup.
     * @param ctx
     * @param onSuccess
     */
    public static void populateContextThen(Service service, ProvisionContext ctx,
            Consumer<ProvisionContext> onSuccess) {
        // TODO fetch all required state in parallel using OperationJoin.
        if (ctx.child == null) {
            URI computeUri = UriUtils
                    .extendUriWithQuery(ctx.computeReference,
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());
            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.child = op.getBody(ComputeStateWithDescription.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.resourcePool == null) {
            if (ctx.child.resourcePoolLink == null) {
                ctx.fail(new IllegalStateException(
                        "resourcePoolLink is not defined for resource "
                                + ctx.child.documentSelfLink));
                return;
            }

            URI rpUri = UriUtils.buildUri(service.getHost(), ctx.child.resourcePoolLink);
            AdapterUtils.getServiceState(service, rpUri, op -> {
                ctx.resourcePool = op.getBody(ResourcePoolState.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.parent == null) {
            if (ctx.child.parentLink == null) {
                ctx.fail(new IllegalStateException(
                        "parentLink is not defined for resource "
                                + ctx.child.documentSelfLink));
                return;
            }

            URI computeUri = UriUtils
                    .extendUriWithQuery(
                            UriUtils.buildUri(service.getHost(), ctx.child.parentLink),
                            UriUtils.URI_PARAM_ODATA_EXPAND,
                            Boolean.TRUE.toString());

            AdapterUtils.getServiceState(service, computeUri, op -> {
                ctx.parent = op.getBody(ComputeStateWithDescription.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
            return;
        }

        if (ctx.vSphereCredentials == null) {
            URI credUri = UriUtils
                    .buildUri(service.getHost(), ctx.parent.description.authCredentialsLink);
            AdapterUtils.getServiceState(service, credUri, op -> {
                ctx.vSphereCredentials = op.getBody(AuthCredentialsServiceState.class);
                populateContextThen(service, ctx, onSuccess);
            }, ctx.errorHandler);
        }

        if (ctx.disks == null) {
            // no disks attached
            if (ctx.child.diskLinks == null || ctx.child.diskLinks
                    .isEmpty()) {
                ctx.disks = Collections.emptyList();
                populateContextThen(service, ctx, onSuccess);
                return;
            }

            ctx.disks = new ArrayList<>(ctx.child.diskLinks.size());

            // collect disks in parallel
            Stream<Operation> opsGetDisk = ctx.child.diskLinks.stream()
                    .map(link -> Operation.createGet(service, link));

            OperationJoin join = OperationJoin.create(opsGetDisk)
                    .setCompletion((os, errors) -> {
                        if (errors != null && !errors.isEmpty()) {
                            // fail on first error
                            ctx.errorHandler
                                    .accept(new IllegalStateException("Cannot get disk state",
                                            errors.values().iterator().next()));
                            return;
                        }

                        os.values().forEach(op -> ctx.disks.add(op.getBody(DiskState.class)));

                        onSuccess.accept(ctx);
                    });

            join.sendWith(service);
        }
    }

    /**
     * The returned JoinedCompletionHandler fails this context by invoking the error handler if any
     * error is found in {@link JoinedCompletionHandler#handle(java.util.Map, java.util.Map) error map}.
     */
    public JoinedCompletionHandler failOnError() {
        return (ops, failures) -> {
            if (failures != null && !failures.isEmpty()) {
                this.fail(failures.values().iterator().next());
            }
        };
    }

    /**
     * Fails the provisioning by invoking the errorHandler.
     * @return tre if t is defined, false otherwise
     * @param t
     */
    public boolean fail(Throwable t) {
        if (t != null) {
            this.errorHandler.accept(t);
            return true;
        } else {
            return false;
        }
    }

    public void failWithMessage(String msg, Throwable t) {
        this.fail(new Exception(msg, t));
    }

    public URI getAdapterManagementReference() {
        if (child.adapterManagementReference != null) {
            return child.adapterManagementReference;
        } else {
            return parent.adapterManagementReference;
        }
    }

    /**
     * zoneID is interpreted as a resource pool. Specific zoneId is preferred, else the parents zone
     * is used.
     * @return
     */
    public String getResourcePoolId() {
        if (child.description.zoneId != null) {
            return child.description.zoneId;
        } else {
            return parent.description.zoneId;
        }
    }

    public void failWithMessage(String msg) {
        fail(new IllegalStateException(msg));
    }
}