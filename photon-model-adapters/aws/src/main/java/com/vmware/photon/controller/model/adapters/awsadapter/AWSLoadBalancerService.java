/*
 * Copyright (c) 2015-2017 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;

import java.net.URI;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingAsyncClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.Instance;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerResult;

import com.vmware.photon.controller.model.adapterapi.LoadBalancerInstanceRequest;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerStateExpanded;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Adapter for provisioning a load balancer on AWS.
 */
public class AWSLoadBalancerService extends StatelessService {
    public static final String SELF_LINK = AWSUriPaths.AWS_LOAD_BALANCER_ADAPTER;

    /**
     * Load balancer request context.
     */
    private static class AWSLoadBalancerContext {

        final LoadBalancerInstanceRequest request;

        LoadBalancerStateExpanded loadBalancerStateExpanded;
        AuthCredentialsServiceState credentials;

        AmazonElasticLoadBalancingAsyncClient client;

        TaskManager taskManager;

        AWSLoadBalancerContext(StatelessService service, LoadBalancerInstanceRequest request) {
            this.request = request;
            this.taskManager = new TaskManager(service, request.taskReference,
                    request.resourceLink());
        }
    }

    private AWSClientManager clientManager;

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {
        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.LOAD_BALANCING);

        super.handleStart(op);
    }

    /**
     * Extend default 'stop' logic with releasing AWS client.
     */
    @Override
    public void handleStop(Operation op) {
        returnClientManager(this.clientManager, AWSConstants.AwsClientType.LOAD_BALANCING);

        super.handleStop(op);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        // Immediately complete the Operation from calling task.
        op.complete();

        // initialize context object
        AWSLoadBalancerContext context = new AWSLoadBalancerContext(this,
                op.getBody(LoadBalancerInstanceRequest.class));

        DeferredResult.completed(context)
                .thenCompose(this::populateContext)
                .thenCompose(this::handleInstanceRequest)
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        context.taskManager.finishTask();
                    } else {
                        context.taskManager.patchTaskToFailure(e);
                    }
                });
    }

    private DeferredResult<AWSLoadBalancerContext> populateContext(AWSLoadBalancerContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getLoadBalancerState)
                .thenCompose(this::getCredentials)
                .thenCompose(this::getAWSClient);
    }

    private DeferredResult<AWSLoadBalancerContext> getLoadBalancerState(AWSLoadBalancerContext context) {
        return this
                .sendWithDeferredResult(
                        Operation.createGet(LoadBalancerStateExpanded.buildUri(
                                context.request.resourceReference)),
                        LoadBalancerStateExpanded.class)
                .thenApply(state -> {
                    context.loadBalancerStateExpanded = state;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> getCredentials(AWSLoadBalancerContext context) {
        URI uri = context.request
                .buildUri(context.loadBalancerStateExpanded.endpointState.authCredentialsLink);
        return this.sendWithDeferredResult(
                Operation.createGet(uri), AuthCredentialsServiceState.class)
                .thenApply(authCredentialsServiceState -> {
                    context.credentials = authCredentialsServiceState;
                    return context;
                });
    }

    private DeferredResult<AWSLoadBalancerContext> getAWSClient(AWSLoadBalancerContext context) {
        DeferredResult<AWSLoadBalancerContext> r = new DeferredResult<>();
        context.client = this.clientManager.getOrCreateLoadBalancingClient(context.credentials,
                context.loadBalancerStateExpanded.regionId, this, context.request.isMockRequest,
                (t) -> r.fail(t));
        if (context.client != null) {
            r.complete(context);
        }
        return r;
    }

    private DeferredResult<AWSLoadBalancerContext> handleInstanceRequest(
            AWSLoadBalancerContext context) {
        DeferredResult<AWSLoadBalancerContext> execution = DeferredResult.completed(context);

        switch (context.request.requestType) {
        case CREATE:
            if (context.request.isMockRequest) {
                // no need to go the end-point; just generate AWS Load Balancer Id.
                // TODO
            } else {
                execution = execution
                        .thenCompose(this::createLoadBalancer)
                        .thenCompose(this::assignInstances);
            }

            return execution;

        case DELETE:
            if (context.request.isMockRequest) {
                // no need to go to the end-point (TODO: add ID to the log message)
                this.logFine("Mock request to delete an AWS load balancer processed.");
            } else {
                execution = execution.thenCompose(this::deleteLoadBalancer);
            }

            return execution.thenCompose(this::deleteLoadBalancerState);
        default:
            IllegalStateException ex = new IllegalStateException("Unsupported request type");
            return DeferredResult.failed(ex);
        }
    }

    private DeferredResult<AWSLoadBalancerContext> createLoadBalancer(
            AWSLoadBalancerContext context) {
        CreateLoadBalancerRequest request = buildCreationRequest(context);

        String message = "Create a new AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<CreateLoadBalancerRequest, CreateLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<CreateLoadBalancerRequest,
                        CreateLoadBalancerResult>(this, message) {
                    @Override
                    protected DeferredResult<CreateLoadBalancerResult> consumeSuccess(
                            CreateLoadBalancerRequest request,
                            CreateLoadBalancerResult result) {
                        return DeferredResult.completed(result);
                    }
                };

        context.client.createLoadBalancerAsync(request, handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> assignInstances(AWSLoadBalancerContext context) {
        RegisterInstancesWithLoadBalancerRequest request = buildInstanceRegistrationRequest(context);

        String message = "Registering instances to AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<RegisterInstancesWithLoadBalancerRequest, RegisterInstancesWithLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<RegisterInstancesWithLoadBalancerRequest,
                RegisterInstancesWithLoadBalancerResult>(this, message) {
                    @Override
                    protected DeferredResult<RegisterInstancesWithLoadBalancerResult> consumeSuccess(
                            RegisterInstancesWithLoadBalancerRequest request,
                            RegisterInstancesWithLoadBalancerResult result) {
                        return DeferredResult.completed(result);
                    }
                };

        context.client.registerInstancesWithLoadBalancerAsync(request, handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    private CreateLoadBalancerRequest buildCreationRequest(AWSLoadBalancerContext context) {
        Listener listener = new Listener()
                .withProtocol(context.loadBalancerStateExpanded.protocol)
                .withLoadBalancerPort(context.loadBalancerStateExpanded.port)
                .withInstanceProtocol(context.loadBalancerStateExpanded.instanceProtocol)
                .withInstancePort(context.loadBalancerStateExpanded.instancePort);

        CreateLoadBalancerRequest request = new CreateLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withListeners(Arrays.asList(listener))
                .withSubnets(context.loadBalancerStateExpanded.subnets.stream()
                        .map(subnet -> subnet.id).collect(Collectors.toList()));

        // TODO .withSchema internal vs. internet-facing

        return request;
    }

    private RegisterInstancesWithLoadBalancerRequest buildInstanceRegistrationRequest(
            AWSLoadBalancerContext context) {
        RegisterInstancesWithLoadBalancerRequest request =
                new RegisterInstancesWithLoadBalancerRequest();

        return request.withLoadBalancerName(context.loadBalancerStateExpanded.name)
                .withInstances(context.loadBalancerStateExpanded.computes.stream()
                        .map(compute -> new Instance(compute.id))
                        .collect(Collectors.toList()));
    }

    private DeferredResult<AWSLoadBalancerContext> deleteLoadBalancer(
            AWSLoadBalancerContext context) {
        DeleteLoadBalancerRequest request = new DeleteLoadBalancerRequest()
                .withLoadBalancerName(context.loadBalancerStateExpanded.name);

        String message = "Delete AWS Load Balancer with name ["
                + context.loadBalancerStateExpanded.name + "].";
        AWSDeferredResultAsyncHandler<DeleteLoadBalancerRequest, DeleteLoadBalancerResult> handler =
                new AWSDeferredResultAsyncHandler<DeleteLoadBalancerRequest,
                        DeleteLoadBalancerResult>(this, message) {
                    @Override
                    protected DeferredResult<DeleteLoadBalancerResult> consumeSuccess(
                            DeleteLoadBalancerRequest request, DeleteLoadBalancerResult result) {
                        return DeferredResult.completed(result);
                    }
                };

        context.client.deleteLoadBalancerAsync(request, handler);
        return handler.toDeferredResult()
                .thenApply(ignore -> context);
    }

    private DeferredResult<AWSLoadBalancerContext> deleteLoadBalancerState(
            AWSLoadBalancerContext context) {
        return this
                .sendWithDeferredResult(
                        Operation.createDelete(this,
                                context.loadBalancerStateExpanded.documentSelfLink))
                .thenApply(operation -> context);
    }
}