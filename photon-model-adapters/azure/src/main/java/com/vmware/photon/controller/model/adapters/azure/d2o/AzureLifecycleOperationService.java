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

package com.vmware.photon.controller.model.adapters.azure.d2o;

import static com.vmware.photon.controller.model.resources.ComputeService.PowerState.SUSPEND;

import java.util.concurrent.ExecutorService;

import com.microsoft.azure.management.compute.implementation.OperationStatusResponseInner;

import com.vmware.photon.controller.model.adapters.azure.AzureAsyncCallback;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureBaseAdapterContext;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureUtils;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperation;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils.TargetCriteria;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.AdapterUtils;
import com.vmware.photon.controller.model.adapters.util.BaseAdapterContext.BaseAdapterStage;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;

public class AzureLifecycleOperationService extends StatelessService {

    public static final String SELF_LINK = ResourceOperationSpecService.buildDefaultAdapterLink(
            EndpointType.azure.name(), ResourceOperationSpecService.ResourceType.COMPUTE,
            "d2opower");

    private static class AzureLifecycleOperationContext
            extends AzureBaseAdapterContext<AzureLifecycleOperationContext> {

        final ResourceOperationRequest request;

        String vmName;
        String rgName;

        private AzureLifecycleOperationContext(
                AzureLifecycleOperationService service,
                ExecutorService executorService,
                ResourceOperationRequest request) {

            super(service, executorService, request);

            this.request = request;
        }
    }

    private ExecutorService executorService;

    @Override
    public void handleStart(Operation startPost) {

        this.executorService = getHost().allocateExecutor(this);

        CompletionHandler completionHandler = (op, exc) -> {
            if (exc != null) {
                startPost.fail(exc);
            } else {
                startPost.complete();
            }
        };
        ResourceOperationUtils.registerResourceOperation(this,
                completionHandler, getResourceOperationSpecs());
    }

    private ResourceOperationSpec[] getResourceOperationSpecs() {
        ResourceOperationSpec operationSpec1 = getResourceOperationSpec(ResourceOperation.RESTART,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        ResourceOperationSpec operationSpec2 = getResourceOperationSpec(ResourceOperation.SUSPEND,
                TargetCriteria.RESOURCE_POWER_STATE_ON.getCriteria());
        return new ResourceOperationSpec[] { operationSpec1, operationSpec2 };
    }

    private ResourceOperationSpec getResourceOperationSpec(ResourceOperation operationType,
            String targetCriteria) {
        ResourceOperationSpec spec = new ResourceOperationSpec();
        spec.adapterReference = AdapterUriUtil.buildAdapterUri(getHost(), SELF_LINK);
        spec.endpointType = EndpointType.azure.name();
        spec.resourceType = ResourceType.COMPUTE;
        spec.operation = operationType.operation;
        spec.name = operationType.displayName;
        spec.description = operationType.description;
        spec.targetCriteria = targetCriteria;
        return spec;
    }

    @Override
    public void handleStop(Operation delete) {
        this.executorService.shutdown();
        AdapterUtils.awaitTermination(this.executorService);
        super.handleStop(delete);
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }
        ResourceOperationRequest request = op.getBody(ResourceOperationRequest.class);

        op.complete();

        AzureLifecycleOperationContext ctx = new AzureLifecycleOperationContext(
                this, this.executorService, request);

        logInfo("Handle operation %s for compute %s.",
                request.operation, request.resourceLink());

        if (request.isMockRequest) {
            updateComputeState(ctx);
            return;
        }

        ctx.populateBaseContext(BaseAdapterStage.VMDESC)
                .whenComplete((ignoreCtx, e) -> {
                    if (e != null) {
                        logSevere(
                                "Error populating base context during Azure resource operation %s for resource %s failed with error %s",
                                request.operation, request.resourceReference,
                                Utils.toString(e));

                        ctx.finishExceptionally(e);
                        return;
                    }

                    ctx.vmName = ctx.child.name != null ? ctx.child.name : ctx.child.id;
                    ctx.rgName = AzureUtils.getResourceGroupName(ctx);

                    applyResourceOperation(ctx);
                });
    }

    private void applyResourceOperation(AzureLifecycleOperationContext ctx) {
        if (ResourceOperation.RESTART.operation.equals(ctx.request.operation)) {
            restart(ctx);
        } else if (ResourceOperation.SUSPEND.operation.equals(ctx.request.operation)) {
            suspend(ctx);
        } else {
            String errorMsg = String.format(
                    "Unsupported resource operation %s requested for resource %s under group %s.",
                    ctx.request.operation, ctx.vmName, ctx.rgName);
            ctx.finishExceptionally(new IllegalArgumentException(errorMsg));
        }
    }

    private void restart(AzureLifecycleOperationContext ctx) {

        ctx.azureSdkClients.getAzureClient().virtualMachines().inner().restartAsync(ctx.rgName,
                ctx.vmName,
                new AzureAsyncCallback<OperationStatusResponseInner>() {
                    @Override
                    public void onError(Throwable paramThrowable) {
                        logSevere(
                                "Error: Azure restart operation failed for resource %s in resourceGroup %s with error %s",
                                ctx.vmName, ctx.rgName, Utils.toString(paramThrowable));
                        ctx.finishExceptionally(paramThrowable);
                    }

                    @Override
                    public void onSuccess(OperationStatusResponseInner paramServiceResponse) {
                        logFine(
                                "Success: Azure restart operation for resource %s in resourceGroup %s completed successfully.",
                                ctx.vmName, ctx.rgName);
                        updateComputeState(ctx);
                    }
                });
    }

    private void suspend(AzureLifecycleOperationContext ctx) {

        Runnable suspend = () -> ctx.azureSdkClients.getAzureClient().virtualMachines().inner()
                .deallocateAsync(ctx.rgName, ctx.vmName,
                        new AzureAsyncCallback<OperationStatusResponseInner>() {
                            @Override
                            public void onError(Throwable paramThrowable) {
                                logSevere(
                                        "Error: Azure deallocate operation failed for resource %s in resourceGroup %s with error %s",
                                        ctx.vmName, ctx.rgName, Utils.toString(paramThrowable));
                                ctx.finishExceptionally(paramThrowable);
                            }

                            @Override
                            public void onSuccess(OperationStatusResponseInner response) {
                                logFine(
                                        "Success: Azure deallocate operation for resource %s in resourceGroup %s completed successfully.",
                                        ctx.vmName, ctx.rgName);
                                updateComputeState(ctx);
                            }
                        });

        PhotonModelUtils.runInExecutor(this.executorService, suspend, ctx::finishExceptionally);
    }

    private void updateComputeState(AzureLifecycleOperationContext ctx) {
        ComputeState state = new ComputeState();
        state.powerState = getPowerState(ctx.request);
        if (SUSPEND.equals(state.powerState)) {
            state.address = ""; // clear IP address in case of power-off
        }
        Operation.createPatch(ctx.request.resourceReference)
                .setBody(state)
                .setCompletion((o, e) -> ctx.finish(e))
                .sendWith(this);
    }

    private PowerState getPowerState(ResourceOperationRequest request) {
        PowerState state = PowerState.UNKNOWN;
        if (ResourceOperation.RESTART.operation.equals(request.operation)) {
            state = PowerState.ON;
        } else if (ResourceOperation.SUSPEND.operation.equals(request.operation)) {
            state = PowerState.SUSPEND;
        }
        return state;
    }
}
