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

package com.vmware.photon.controller.model.adapters.azure.instance;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.microsoft.azure.SubResource;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.compute.implementation.ComputeManagementClientImpl;
import com.microsoft.azure.management.compute.implementation.ImageReferenceInner;
import com.microsoft.azure.management.network.implementation.NetworkInterfaceInner;
import com.microsoft.azure.management.network.implementation.NetworkManagementClientImpl;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupInner;
import com.microsoft.azure.management.network.implementation.NetworkSecurityGroupsInner;
import com.microsoft.azure.management.network.implementation.PublicIPAddressInner;
import com.microsoft.azure.management.network.implementation.SubnetInner;
import com.microsoft.azure.management.network.implementation.SubnetsInner;
import com.microsoft.azure.management.resources.implementation.ResourceGroupInner;
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl;
import com.microsoft.azure.management.storage.implementation.StorageAccountInner;
import com.microsoft.azure.management.storage.implementation.StorageManagementClientImpl;
import com.microsoft.rest.RestClient;

import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.ResourceGroupStateType;
import com.vmware.photon.controller.model.adapters.azure.utils.AzureDeferredResultServiceCallback;
import com.vmware.photon.controller.model.adapters.util.instance.BaseComputeInstanceContext;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Context object to store relevant information during different stages.
 */
public class AzureInstanceContext extends
        BaseComputeInstanceContext<AzureInstanceContext, AzureInstanceContext.AzureNicContext> {

    /**
     * The class encapsulates NIC related data (both Photon Model and Azure model) used during
     * provisioning.
     */
    public static class AzureNicContext extends BaseComputeInstanceContext.BaseNicContext {

        /**
         * The Azure subnet this NIC is associated to. It is either looked up from Azure or created
         * by this service.
         */
        public SubnetInner subnet;

        /**
         * The actual NIC object in Azure. It is created by this service.
         */
        public NetworkInterfaceInner nic;

        /**
         * The public IP assigned to the NIC. It is created by this service.
         */
        public PublicIPAddressInner publicIP;

        /**
         * The public IP address sub resource ID. Maps an ID string with IP address, required by
         * Azure.
         */
        SubResource publicIPSubResource;

        /**
         * The security group this NIC is assigned to. It is created by this service.
         */
        public NetworkSecurityGroupInner securityGroup;

        /**
         * The security group sub resource ID. Maps an ID string with security group, required by
         * Azure.
         */
        SubResource securityGroupSubResource;

        /**
         * The resource group state the security group is member of. Optional.
         */
        public ResourceGroupState securityGroupRGState;

        /**
         * A shortcut method to {@code this.securityGroupStates.get(0)}.
         *
         * @return {@code null} is returned if security group is not specified.
         */
        public SecurityGroupState securityGroupState() {
            return this.securityGroupStates != null && !this.securityGroupStates.isEmpty()
                    ? this.securityGroupStates.get(0) : null;
        }
    }

    public AzureInstanceStage stage;

    public AuthCredentialsServiceState childAuth;

    public StorageDescription storageDescription;
    public DiskService.DiskStateExpanded bootDisk;
    public List<DiskService.DiskStateExpanded> childDisks;
    public String vmName;
    public String vmId;

    // Azure specific context
    public ApplicationTokenCredentials credentials;
    public ResourceGroupInner resourceGroup;
    public StorageAccountInner storage;

    public String storageAccountName;
    public String storageAccountRGName;

    public ImageSource imageSource;
    public ImageReferenceInner imageReference;

    public ResourceManagementClientImpl resourceManagementClient;
    public NetworkManagementClientImpl networkManagementClient;
    public StorageManagementClientImpl storageManagementClient;
    public ComputeManagementClientImpl computeManagementClient;
    public RestClient restClient;

    public AzureInstanceContext(AzureInstanceService service,
            ComputeInstanceRequest computeRequest) {
        super(service, computeRequest, AzureNicContext::new);
    }

    /**
     * Hook into parent populate behavior.
     */
    @Override
    protected DeferredResult<AzureInstanceContext> getVMDescription(AzureInstanceContext context) {
        return super.getVMDescription(context)
                // Populate vm name
                .thenApply(ctx -> {
                    ctx.vmName = ctx.child.name != null ? ctx.child.name : ctx.child.id;
                    return ctx;
                });
    }

    @Override
    protected DeferredResult<AzureInstanceContext> customizeContext(AzureInstanceContext context) {
        return DeferredResult.completed(context)
                .thenCompose(this::getNicSecurityGroupResourceGroupStates)
                .thenApply(log("getNicSecurityGroupResourceGroupStates"))
                .thenCompose(this::getNetworks).thenApply(log("getNetworks"))
                .thenCompose(this::getSecurityGroups).thenApply(log("getSecurityGroups"));
    }

    /**
     * @return type safe reference to the service using this context.
     */
    private AzureInstanceService service() {
        return (AzureInstanceService) this.service;
    }

    /**
     * For every NIC lookup associated Azure Subnets as specified by
     * {@code AzureNicContext.networkState.name} and {@code AzureNicContext.subnetState.name}. If
     * any of the subnets is not found leave the {@link AzureNicContext#subnet} as null and proceed
     * without an exception.
     */
    private DeferredResult<AzureInstanceContext> getNetworks(AzureInstanceContext context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        SubnetsInner azureClient = service()
                .getNetworkManagementClientImpl(context)
                .subnets();

        List<DeferredResult<SubnetInner>> getSubnetDRs = context.nics
                .stream()
                // Filter only vNet-Subnet with existing RG state
                .filter(nicCtx -> nicCtx.networkRGState != null)
                .map(nicCtx -> {
                    String msg = "Getting Azure Subnet ["
                            + nicCtx.networkRGState.name + "/"
                            + nicCtx.networkState.name + "/"
                            + nicCtx.subnetState.name
                            + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                            + context.vmName
                            + "] VM";

                    AzureDeferredResultServiceCallback<SubnetInner> handler = new AzureDeferredResultServiceCallback<SubnetInner>(
                            service(), msg) {
                        @Override
                        protected DeferredResult<SubnetInner> consumeSuccess(SubnetInner subnet) {
                            nicCtx.subnet = subnet;
                            return DeferredResult.completed(subnet);
                        }
                    };
                    azureClient.getAsync(
                            nicCtx.networkRGState.name,
                            nicCtx.networkState.name,
                            nicCtx.subnetState.name,
                            null /* expand */,
                            handler);

                    return handler.toDeferredResult();
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getSubnetDRs)
                .handle((all, exc) -> {
                    if (exc != null) {
                        String msg = String.format(
                                "Error getting Subnets from Azure for [%s] VM.",
                                context.child.name);
                        throw new IllegalStateException(msg, exc);
                    }
                    return context;
                });
    }

    /**
     * For every NIC lookup associated Azure Security Groups as specified by
     * {@code AzureNicContext.securityGroupState.name}. If any of the security groups is not found
     * leave the {@code AzureNicContext.securityGroup} as null and proceed without an exception.
     */
    private DeferredResult<AzureInstanceContext> getSecurityGroups(AzureInstanceContext context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        NetworkSecurityGroupsInner azureClient = service()
                .getNetworkManagementClientImpl(context)
                .networkSecurityGroups();

        List<DeferredResult<NetworkSecurityGroupInner>> getSecurityGroupDRs = context.nics
                .stream()
                // Filter only SGs with existing RG state
                .filter(nicCtx -> nicCtx.securityGroupState() != null
                        && nicCtx.securityGroupRGState != null)
                .map(nicCtx -> {
                    String sgName = nicCtx.securityGroupState().name;

                    String msg = "Getting Azure Security Group["
                            + nicCtx.securityGroupRGState.name + "/" + sgName
                            + "] for [" + nicCtx.nicStateWithDesc.name + "] NIC for ["
                            + context.vmName
                            + "] VM";

                    AzureDeferredResultServiceCallback<NetworkSecurityGroupInner> handler = new AzureDeferredResultServiceCallback<NetworkSecurityGroupInner>(
                            service(), msg) {
                        @Override
                        protected DeferredResult<NetworkSecurityGroupInner> consumeSuccess(
                                NetworkSecurityGroupInner securityGroup) {
                            nicCtx.securityGroup = securityGroup;
                            return DeferredResult.completed(securityGroup);
                        }
                    };
                    azureClient.getByResourceGroupAsync(
                            nicCtx.securityGroupRGState.name,
                            sgName,
                            null /* expand */,
                            handler);

                    return handler.toDeferredResult();
                })
                .collect(Collectors.toList());

        return DeferredResult.allOf(getSecurityGroupDRs)
                .handle((all, exc) -> {
                    if (exc != null) {
                        String msg = String.format(
                                "Error getting Security Group from Azure for [%s] VM.",
                                context.child.name);
                        throw new IllegalStateException(msg, exc);
                    }
                    return context;
                });
    }

    /**
     * Get {@link ResourceGroupState}s of the {@link SecurityGroupState}s the NICs are assigned to.
     * If any of the RGs is not specified or not found leave the
     * {@link AzureNicContext#securityGroupRGState} as null and proceed without an exception.
     */
    private DeferredResult<AzureInstanceContext> getNicSecurityGroupResourceGroupStates(
            AzureInstanceContext context) {

        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics
                .stream()
                .filter(nicCtx -> nicCtx.securityGroupState() != null
                        && nicCtx.securityGroupState().groupLinks != null
                        && !nicCtx.securityGroupState().groupLinks.isEmpty())
                .map(nicCtx -> {
                    DeferredResult<ResourceGroupState> filteredRGStates = queryFirstRGFilterByType(
                            context, nicCtx.securityGroupState().groupLinks);

                    return filteredRGStates
                            .thenApply(resourceGroupState -> {

                                nicCtx.securityGroupRGState = resourceGroupState;

                                return (Void) null;
                            });

                }).collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting ResourceGroup states of NIC Security Group states for "
                                + "[%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });
    }

    /**
     * Get {@link ResourceGroupState}s of the {@code NetworkState}s the NICs are assigned to.
     */
    @Override
    protected DeferredResult<AzureInstanceContext> getNicNetworkResourceGroupStates(
            AzureInstanceContext context) {
        if (context.nics.isEmpty()) {
            return DeferredResult.completed(context);
        }

        List<DeferredResult<Void>> getStatesDR = context.nics
                .stream()
                .filter(nicCtx -> nicCtx.networkState != null
                        && nicCtx.networkState.groupLinks != null
                        && !nicCtx.networkState.groupLinks.isEmpty())
                .map(nicCtx -> {

                    DeferredResult<ResourceGroupState> filteredRGStates = queryFirstRGFilterByType(
                            context, nicCtx.networkState.groupLinks);

                    return filteredRGStates
                            .thenApply(resourceGroupState -> {

                                nicCtx.networkRGState = resourceGroupState;

                                return (Void) null;
                            });

                }).collect(Collectors.toList());

        return DeferredResult.allOf(getStatesDR).handle((all, exc) -> {
            if (exc != null) {
                String msg = String.format(
                        "Error getting ResourceGroup states of NIC Network states for "
                                + "[%s] VM.",
                        context.child.name);
                throw new IllegalStateException(msg, exc);
            }
            return context;
        });

    }

    /**
     * Utility method for filtering resource group list by type, and returning the first one, which
     * is of ResourceGroupStateType.AzureResourceGroup type.
     */
    private DeferredResult<ResourceGroupState> queryFirstRGFilterByType(
            AzureInstanceContext context, Set<String> groupLinks) {

        Query.Builder qBuilder = Query.Builder.create()
                .addKindFieldClause(ResourceGroupState.class)
                .addInClause(ResourceState.FIELD_NAME_SELF_LINK, groupLinks)
                .addCompositeFieldClause(
                        ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeProperties.RESOURCE_TYPE_KEY,
                        ResourceGroupStateType.AzureResourceGroup.name());

        QueryStrategy<ResourceGroupState> queryByPages = new QueryTop<>(
                service().getHost(),
                qBuilder.build(),
                ResourceGroupState.class,
                context.childAuth.tenantLinks,
                context.child.endpointLink)
                        // only one group is required
                        .setMaxResultsLimit(1);

        return queryByPages.collectDocuments(Collectors.toList())
                .thenApply(rgStates -> rgStates.stream().findFirst().orElse(null));
    }

    /**
     * Shortcut method to image OS disk configuration:
     * {@code this.imageSource.asImage().diskConfigs.get(0)}.
     *
     * @return might be null
     */
    DiskConfiguration imageOsDisk() {

        if (this.imageSource == null || this.imageSource.asImageState() == null) {
            return null;
        }

        ImageState image = this.imageSource.asImageState();

        if (image.diskConfigs == null || image.diskConfigs.isEmpty()) {
            return null;
        }

        return image.diskConfigs.get(0);
    }
}
