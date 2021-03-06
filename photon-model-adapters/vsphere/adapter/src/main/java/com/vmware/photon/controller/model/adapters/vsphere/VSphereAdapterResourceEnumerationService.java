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

import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_AVAILABLE_BYTES;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_USED_BYTES;
import static com.vmware.xenon.common.UriUtils.buildUriPath;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections.CollectionUtils;
import org.codehaus.jackson.node.ObjectNode;

import com.vmware.pbm.PbmProfile;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.util.AdapterUriUtil;
import com.vmware.photon.controller.model.adapters.util.TagsUtil;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.vsphere.VsphereResourceCleanerService.ResourceCleanRequest;
import com.vmware.photon.controller.model.adapters.vsphere.network.DvsProperties;
import com.vmware.photon.controller.model.adapters.vsphere.network.NsxProperties;
import com.vmware.photon.controller.model.adapters.vsphere.tagging.TagCache;
import com.vmware.photon.controller.model.adapters.vsphere.util.MoRefKeyedMap;
import com.vmware.photon.controller.model.adapters.vsphere.util.VimNames;
import com.vmware.photon.controller.model.adapters.vsphere.util.connection.Connection;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.RpcException;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.TaggingClient;
import com.vmware.photon.controller.model.adapters.vsphere.vapi.VapiConnection;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SnapshotService;
import com.vmware.photon.controller.model.resources.SnapshotService.SnapshotState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.monitoring.SingleResourceStatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsUtil;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.photon.controller.model.util.PhotonModelUriUtils;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.ObjectContent;
import com.vmware.vim25.OpaqueNetworkSummary;
import com.vmware.vim25.PropertyFilterSpec;
import com.vmware.vim25.UpdateSet;
import com.vmware.vim25.VirtualDeviceBackingInfo;
import com.vmware.vim25.VirtualEthernetCard;
import com.vmware.vim25.VirtualEthernetCardNetworkBackingInfo;
import com.vmware.vim25.VirtualMachineSnapshotTree;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * Handles enumeration for vsphere endpoints. It supports up to
 * {@link #MAX_CONCURRENT_ENUM_PROCESSES} concurrent long-running enumeration processes. Attempts to
 * start more processes than that will result in error.
 */
public class VSphereAdapterResourceEnumerationService extends StatelessService {
    public static final String SELF_LINK = VSphereUriPaths.ENUMERATION_SERVICE;

    private static final int MAX_CONCURRENT_ENUM_PROCESSES = 10;

    private static final long QUERY_TASK_EXPIRY_MICROS = TimeUnit.MINUTES.toMicros(1);

    /*
     * A VM must "ferment" for a few minutes before being eligible for enumeration. This is the time
     * between a VM is created and its UUID is recorded back in the ComputeState resource. This way
     * a VM being provisioned by photon-model will not be enumerated mid-flight.
     */
    private static final long VM_FERMENTATION_PERIOD_MILLIS = 3 * 60 * 1000;
    public static final String PREFIX_NETWORK = "network";
    public static final String PREFIX_DATASTORE = "datastore";

    /**
     * Stores currently running enumeration processes.
     */
    private final ConcurrentMap<String, ComputeEnumerateResourceRequest> startedEnumProcessesByHost = new ConcurrentHashMap<>();

    /**
     * Bounded theadpool executing the currently running enumeration processes.
     */
    private final ExecutorService enumerationThreadPool;

    private final TagCache tagCache;

    public VSphereAdapterResourceEnumerationService() {
        this.enumerationThreadPool = new ThreadPoolExecutor(MAX_CONCURRENT_ENUM_PROCESSES,
                MAX_CONCURRENT_ENUM_PROCESSES,
                0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new AbortPolicy());

        this.tagCache = new TagCache();
    }

    @Override
    public void handlePatch(Operation op) {
        if (!op.hasBody()) {
            op.fail(new IllegalArgumentException("body is required"));
            return;
        }

        ComputeEnumerateResourceRequest request = op.getBody(ComputeEnumerateResourceRequest.class);

        validate(request);

        op.setStatusCode(Operation.STATUS_CODE_CREATED);
        op.complete();

        TaskManager mgr = new TaskManager(this, request.taskReference, request.resourceLink());

        if (request.isMockRequest) {
            // just finish the mock request
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        URI parentUri = ComputeStateWithDescription.buildUri(PhotonModelUriUtils.createInventoryUri(getHost(),
                request.resourceReference));

        Operation.createGet(parentUri)
                .setCompletion(o -> {
                    thenWithParentState(request, o.getBody(ComputeStateWithDescription.class), mgr);
                }, mgr)
                .sendWith(this);
    }

    private void thenWithParentState(ComputeEnumerateResourceRequest request,
            ComputeStateWithDescription parent, TaskManager mgr) {

        if (request.enumerationAction == EnumerationAction.STOP) {
            endEnumerationProcess(parent, mgr);
            return;
        }

        collectAllEndpointResources(request, parent.documentSelfLink).thenAccept(resourceLinks -> {
            VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);

            pool.submit(this, parent.adapterManagementReference,
                    parent.description.authCredentialsLink,
                    (connection, e) -> {
                        if (e != null) {
                            String msg = String.format("Cannot establish connection to %s",
                                    parent.adapterManagementReference);
                            logWarning(msg);
                            mgr.patchTaskToFailure(msg, e);
                        } else {
                            if (request.enumerationAction == EnumerationAction.REFRESH) {
                                refreshResourcesOnce(resourceLinks, request, connection, parent, mgr);
                            } else if (request.enumerationAction == EnumerationAction.START) {
                                startEnumerationProcess(
                                        connection.createUnmanagedCopy(),
                                        parent,
                                        request,
                                        mgr);
                            }
                        }
                    });
        });
    }

    private void endEnumerationProcess(ComputeStateWithDescription parent, TaskManager mgr) {
        // just remove from map, enumeration process checks if it should continue at every step
        ComputeEnumerateResourceRequest old = this.startedEnumProcessesByHost
                .remove(parent.documentSelfLink);

        if (old == null) {
            logFine(() -> String.format("No running enumeration process for %s was found",
                    parent.documentSelfLink));
        }

        mgr.patchTask(TaskStage.FINISHED);
    }

    private DeferredResult<Set<String>> collectAllEndpointResources(ComputeEnumerateResourceRequest req,
            String parentLink) {
        Query.Builder builder = Query.Builder.create()
                .addFieldClause(ResourceState.FIELD_NAME_ENDPOINT_LINK, req.endpointLink)
                .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, parentLink, Occurance.MUST_NOT_OCCUR)
                .addInClause(ServiceDocument.FIELD_NAME_KIND, Arrays.asList(
                        Utils.buildKind(ComputeState.class),
                        Utils.buildKind(NetworkState.class),
                        Utils.buildKind(StorageDescription.class),
                        Utils.buildKind(SubnetState.class)));

        QueryTask task = QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .setResultLimit(QueryUtils.DEFAULT_RESULT_LIMIT)
                .build();

        DeferredResult<Set<String>> res = new DeferredResult<>();
        Set<String> links = new ConcurrentSkipListSet<>();

        QueryUtils.startInventoryQueryTask(this, task)
                .whenComplete((result, e) -> {
                    if (e != null) {
                        res.complete(new HashSet<>());
                        return;
                    }

                    if (result.results.nextPageLink == null) {
                        res.complete(links);
                        return;
                    }

                    Operation.createGet(PhotonModelUriUtils
                            .createInventoryUri(getHost(), result.results.nextPageLink))
                            .setCompletion(makeCompletion(links, res))
                            .sendWith(this);
                });

        return res;
    }

    private CompletionHandler makeCompletion(Set<String> imageLinks, DeferredResult<Set<String>> res) {
        return (o, e) -> {
            if (e != null) {
                res.complete(imageLinks);
                return;
            }

            QueryTask qt = o.getBody(QueryTask.class);
            imageLinks.addAll(qt.results.documentLinks);

            if (qt.results.nextPageLink == null) {
                res.complete(imageLinks);
            } else {
                Operation.createGet(PhotonModelUriUtils.createInventoryUri(getHost(), qt.results.nextPageLink))
                        .setCompletion(makeCompletion(imageLinks, res))
                        .sendWith(this);
            }
        };
    }

    private void startEnumerationProcess(
            Connection connection,
            ComputeStateWithDescription parent,
            ComputeEnumerateResourceRequest request, TaskManager mgr) {

        ComputeEnumerateResourceRequest old = this.startedEnumProcessesByHost
                .putIfAbsent(parent.documentSelfLink, request);

        if (old != null) {
            logFine(() -> String.format("Enumeration process for %s already started, not starting a"
                    + " new one", parent.documentSelfLink));
            return;
        }

        EnumerationClient client;
        try {
            client = new EnumerationClient(connection, parent);
        } catch (Exception e) {
            String msg = String
                    .format("Error connecting to %s while starting enumeration process for %s",
                            parent.adapterManagementReference,
                            parent.documentSelfLink);
            logWarning(msg);
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        try {
            OperationContext opContext = OperationContext.getOperationContext();
            this.enumerationThreadPool.execute(() -> {
                OperationContext.restoreOperationContext(opContext);
                try {
                    startEnumerationProcess(parent, client);
                } catch (Exception e) {
                    String msg = String.format("Error during enumeration process %s, aborting",
                            parent.documentSelfLink);
                    log(Level.FINE, msg);
                    mgr.patchTaskToFailure(msg, e);
                }
            });
        } catch (RejectedExecutionException e) {
            String msg = String
                    .format("Max number of resource enumeration processes reached: will not start "
                            + "one for %s", parent.documentSelfLink);
            logWarning(msg);
            mgr.patchTaskToFailure(msg, e);
        }
    }

    /**
     * This method executes in a thread managed by {@link #enumerationThreadPool}.
     *
     * @param client
     * @throws Exception
     */
    private void startEnumerationProcess(ComputeStateWithDescription parent,
            EnumerationClient client)
            throws Exception {
        if (parent.description.regionId == null) {
            // not implemented if no datacenter is provided
            return;
        }

        PropertyFilterSpec spec = client.createResourcesFilterSpec();

        try {
            for (UpdateSet updateSet : client.pollForUpdates(spec)) {
                processUpdates(updateSet);
                if (!this.startedEnumProcessesByHost.containsKey(parent.documentSelfLink)) {
                    break;
                }
            }
        } catch (Exception e) {
            // destroy connection and let global error handler process it further
            client.close();
            throw e;
        }
    }

    /**
     * This method executes in a thread managed by {@link VSphereIOThreadPoolAllocator}
     *
     * @param resourceLinks
     * @param request
     * @param connection
     * @param parent
     * @param mgr
     */
    private void refreshResourcesOnce(
            Set<String> resourceLinks, ComputeEnumerateResourceRequest request,
            Connection connection,
            ComputeStateWithDescription parent,
            TaskManager mgr) {

        EnumerationClient client;
        try {
            client = new EnumerationClient(connection, parent);
        } catch (Exception e) {
            mgr.patchTaskToFailure(e);
            return;
        }

        VapiConnection vapiConnection = VapiConnection.createFromVimConnection(connection);

        try {
            vapiConnection.login();
        } catch (IOException | RpcException e) {
            logWarning(() -> String.format("Cannot login into vAPI endpoint: %s", Utils.toString(e)));
            // TODO: patchTaskToFailure on each failure?
            return;
        }

        EnumerationProgress enumerationProgress = new EnumerationProgress(resourceLinks, request,
                parent, vapiConnection);

        try {
            refreshResourcesOnDatacenter(client, enumerationProgress, mgr);
        } catch (Exception e) {
            logWarning(() -> String.format("Error during enumeration: %s", Utils.toString(e)));
        }

        try {
            vapiConnection.close();
        } catch (Exception e) {
            logWarning(() -> String.format("Error occurred when closing vAPI connection: %s",
                    Utils.toString(e)));
        }

        garbageCollectUntouchedComputeResources(request, enumerationProgress, mgr);
    }

    private void refreshResourcesOnDatacenter(EnumerationClient client, EnumerationProgress ctx,
            TaskManager mgr) {
        MoRefKeyedMap<NetworkOverlay> networks = new MoRefKeyedMap<>();
        List<HostSystemOverlay> hosts = new ArrayList<>();
        List<DatastoreOverlay> datastores = new ArrayList<>();
        List<ComputeResourceOverlay> clusters = new ArrayList<>();
        List<ResourcePoolOverlay> resourcePools = new ArrayList<>();
        List<StoragePolicyOverlay> storagePolicies = new ArrayList<>();

        // put results in different buckets by type
        PropertyFilterSpec spec = client.createResourcesFilterSpec();
        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                for (ObjectContent cont : page) {
                    if (VimUtils.isNetwork(cont.getObj())) {
                        NetworkOverlay net = new NetworkOverlay(cont);
                        ctx.track(net);
                        if (!net.getName().toLowerCase().contains("dvuplinks")) {
                            // skip uplinks altogether,
                            // TODO starting with 6.5 query the property config.uplink instead
                            networks.put(net.getId(), net);
                        }
                    } else if (VimUtils.isHost(cont.getObj())) {
                        // this includes all standalone and clustered hosts
                        HostSystemOverlay hs = new HostSystemOverlay(cont);
                        hosts.add(hs);
                    } else if (VimUtils.isComputeResource(cont.getObj())) {
                        ComputeResourceOverlay cr = new ComputeResourceOverlay(cont);
                        if (cr.isDrsEnabled()) {
                            // when DRS is enabled add the cluster itself and skip the hosts
                            clusters.add(cr);
                        } else {
                            // ignore non-clusters and non-drs cluster: they are handled as hosts
                            continue;
                        }
                    } else if (VimUtils.isDatastore(cont.getObj())) {
                        DatastoreOverlay ds = new DatastoreOverlay(cont);
                        datastores.add(ds);
                    } else if (VimUtils.isResourcePool(cont.getObj())) {
                        ResourcePoolOverlay rp = new ResourcePoolOverlay(cont);
                        resourcePools.add(rp);
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(() -> msg + ": " + e.toString());
            mgr.patchTaskToFailure(msg, e);
            return;
        }

        try {
            List<PbmProfile> pbmProfiles = client.retrieveStoragePolicies();
            if (!pbmProfiles.isEmpty()) {
                for (PbmProfile profile : pbmProfiles) {
                    List<String> datastoreNames = client.getDatastores(profile.getProfileId());
                    StoragePolicyOverlay spOverlay = new StoragePolicyOverlay(profile, datastoreNames);
                    storagePolicies.add(spOverlay);
                }
            }
        } catch (Exception e) {
            // vSphere throws exception even if there are no storage policies found on the server.
            // Hence we can just log the message and continue, as with the datastore selection
            // still provisioning can proceed. Not marking the task to failure here.
            String msg = "Error processing Storage policy ";
            logWarning(() -> msg + ": " + e.toString());
        }

        // process results in topological order
        ctx.expectNetworkCount(networks.size());
        for (NetworkOverlay net : networks.values()) {
            processFoundNetwork(ctx, net, networks);
        }

        ctx.expectDatastoreCount(datastores.size());
        for (DatastoreOverlay ds : datastores) {
            processFoundDatastore(ctx, ds);
        }

        // checkpoint net & storage, they are not related currently
        try {
            ctx.getDatastoreTracker().await();
            ctx.getNetworkTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        // Process found storage policy, it is related to datastore. Hence process it after
        // datastore processing is complete.
        if (storagePolicies.size() > 0) {
            ctx.expectStoragePolicyCount(storagePolicies.size());
            for (StoragePolicyOverlay sp : storagePolicies) {
                processFoundStoragePolicy(ctx, sp);
            }

            // checkpoint for storage policy
            try {
                ctx.getStoragePolicyTracker().await();
            } catch (InterruptedException e) {
                threadInterrupted(mgr, e);
                return;
            }
        }

        // exclude hosts part of a cluster
        for (ComputeResourceOverlay cluster : clusters) {
            for (ManagedObjectReference hostRef : cluster.getHosts()) {
                hosts.removeIf(ho -> Objects.equals(ho.getId().getValue(), hostRef.getValue()));
            }
        }

        ctx.expectHostSystemCount(hosts.size());
        for (HostSystemOverlay hs : hosts) {
            ctx.track(hs);
            processFoundHostSystem(ctx, hs);
        }

        ctx.expectComputeResourceCount(clusters.size());
        for (ComputeResourceOverlay cr : clusters) {
            ctx.track(cr);
            processFoundComputeResource(ctx, cr);
        }

        // exclude all root resource pools
        for (Iterator<ResourcePoolOverlay> it = resourcePools.iterator(); it.hasNext(); ) {
            ResourcePoolOverlay rp = it.next();
            if (!VimNames.TYPE_RESOURCE_POOL.equals(rp.getParent().getType())) {
                // no need to collect the root resource pool
                it.remove();
            }
        }

        MoRefKeyedMap<String> computeResourceNamesByMoref = collectComputeNames(hosts, clusters);
        ctx.expectResourcePoolCount(resourcePools.size());
        for (ResourcePoolOverlay rp : resourcePools) {
            String ownerName = computeResourceNamesByMoref.get(rp.getOwner());
            processFoundResourcePool(ctx, rp, ownerName);
        }

        // checkpoint compute
        try {
            ctx.getHostSystemTracker().await();
            ctx.getComputeResourceTracker().await();
            ctx.getResourcePoolTracker().await();
        } catch (InterruptedException e) {
            threadInterrupted(mgr, e);
            return;
        }

        long latestAcceptableModification = System.currentTimeMillis()
                - VM_FERMENTATION_PERIOD_MILLIS;
        spec = client.createVmFilterSpec(client.getDatacenter());
        try {
            for (List<ObjectContent> page : client.retrieveObjects(spec)) {
                ctx.resetVmTracker();
                for (ObjectContent cont : page) {
                    if (!VimUtils.isVirtualMachine(cont.getObj())) {
                        continue;
                    }
                    VmOverlay vm = new VmOverlay(cont);
                    if (vm.isTemplate()) {
                        // templates are skipped, enumerated as "images" instead
                        continue;
                    }
                    if (vm.getInstanceUuid() == null) {
                        logWarning(() -> String.format("Cannot process a VM without"
                                        + " instanceUuid: %s",
                                VimUtils.convertMoRefToString(vm.getId())));
                    } else if (vm.getLastReconfigureMillis() < latestAcceptableModification) {
                        ctx.getVmTracker().register();
                        processFoundVm(ctx, vm);
                    }
                }
                ctx.getVmTracker().arriveAndAwaitAdvance();
            }
        } catch (Exception e) {
            String msg = "Error processing PropertyCollector results";
            logWarning(() -> msg + ": " + e.toString());
            mgr.patchTaskToFailure(msg, e);
            return;
        }
    }

    /**
     * Collect the names of all hosts and cluster indexed by the string representation
     * of their moref.
     * Used to construct user-friendly resource pool names without fetching their owner's name again.
     * @param hosts
     * @param computeResources
     * @return
     */
    private MoRefKeyedMap<String> collectComputeNames(List<HostSystemOverlay> hosts,
            List<ComputeResourceOverlay> computeResources) {
        MoRefKeyedMap<String> computeResourceNamesByMoref = new MoRefKeyedMap<>();
        for (HostSystemOverlay host : hosts) {
            computeResourceNamesByMoref.put(host.getId(), host.getName());
        }
        for (ComputeResourceOverlay cr : computeResources) {
            computeResourceNamesByMoref.put(cr.getId(), cr.getName());
        }

        return computeResourceNamesByMoref;
    }

    private void garbageCollectUntouchedComputeResources(ComputeEnumerateResourceRequest request,
            EnumerationProgress progress, TaskManager mgr) {
        if (progress.getResourceLinks().isEmpty()) {
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        if (!request.preserveMissing) {
            // delete dependent resources without waiting for response
            for (String resourceLink : progress.getResourceLinks()) {
                Operation.createDelete(
                        PhotonModelUriUtils.createInventoryUri(getHost(), resourceLink))
                        .sendWith(this);
            }
            mgr.patchTask(TaskStage.FINISHED);
            return;
        }

        List<Operation> deleteOps = new ArrayList<>();
        for (String resourceLink : progress.getResourceLinks()) {
            if (resourceLink.startsWith(ComputeService.FACTORY_LINK)) {
                ResourceCleanRequest patch = new ResourceCleanRequest();
                patch.resourceLink = resourceLink;
                deleteOps.add(Operation.createPatch(this, VSphereUriPaths.RESOURCE_CLEANER)
                        .setBody(patch));
            } else {
                deleteOps.add(Operation.createDelete(
                        PhotonModelUriUtils.createInventoryUri(getHost(), resourceLink)));
            }
        }

        OperationJoin.create(deleteOps)
                .setCompletion((os, es) -> mgr.patchTask(TaskStage.FINISHED))
                .sendWith(this);
    }

    private void threadInterrupted(TaskManager mgr, InterruptedException e) {
        String msg = "Enumeration thread was interrupted";
        logWarning(msg);
        mgr.patchTaskToFailure(msg, e);
    }

    private void processFoundNetwork(EnumerationProgress enumerationProgress, NetworkOverlay net,
            MoRefKeyedMap<NetworkOverlay> allNetworks) {
        if (net.getParentSwitch() != null) {
            // portgroup: create subnet
            QueryTask task = queryForSubnet(enumerationProgress, net);
            withTaskResults(task, result -> {
                if (result.documentLinks.isEmpty()) {
                    createNewSubnet(enumerationProgress, net, allNetworks.get(net.getParentSwitch()));
                } else {
                    SubnetState oldDocument = convertOnlyResultToDocument(result, SubnetState.class);
                    updateSubnet(oldDocument, enumerationProgress, net);
                }
            });
        } else {
            // DVS or opaque network
            QueryTask task = queryForNetwork(enumerationProgress, net.getName());
            withTaskResults(task, result -> {
                if (result.documentLinks.isEmpty()) {
                    createNewNetwork(enumerationProgress, net);
                } else {
                    NetworkState oldDocument = convertOnlyResultToDocument(result, NetworkState.class);
                    updateNetwork(oldDocument, enumerationProgress, net);
                }
            });
        }
    }

    private void updateSubnet(SubnetState oldDocument, EnumerationProgress enumerationProgress, NetworkOverlay net) {
        SubnetState state = makeSubnetStateFromResults(enumerationProgress, net);
        state.documentSelfLink = oldDocument.documentSelfLink;
        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Subnet(Portgroup) %s", net.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(this);
    }

    private void createNewSubnet(EnumerationProgress enumerationProgress, NetworkOverlay net,
            NetworkOverlay parentSwitch) {
        SubnetState state = makeSubnetStateFromResults(enumerationProgress, net);
        state.customProperties.put(DvsProperties.DVS_UUID, parentSwitch.getDvsUuid());

        state.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), SubnetService.FACTORY_LINK))
                .setBody(state)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(this);

        logFine(() -> String.format("Found new Subnet(Portgroup) %s", net.getName()));
    }

    private SubnetState makeSubnetStateFromResults(EnumerationProgress enumerationProgress, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        SubnetState state = new SubnetState();

        state.id = state.name = net.getName();
        state.endpointLink = enumerationProgress.getRequest().endpointLink;

        ManagedObjectReference parentSwitch = net.getParentSwitch();
        state.networkLink = buildStableDvsLink(parentSwitch, request.endpointLink);

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        custProp.put(DvsProperties.PORT_GROUP_KEY, net.getPortgroupKey());

        return state;
    }

    private QueryTask queryForSubnet(EnumerationProgress ctx, NetworkOverlay portgroup) {
        String dvsLink = buildStableDvsLink(portgroup.getParentSwitch(), ctx.getRequest().endpointLink);
        String moref = VimUtils.convertMoRefToString(portgroup.getId());

        Builder builder = Query.Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addFieldClause(SubnetState.FIELD_NAME_NETWORK_LINK, dvsLink)
                .addCompositeFieldClause(ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                        CustomProperties.MOREF, moref);
        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private void updateNetwork(NetworkState oldDocument, EnumerationProgress enumerationProgress, NetworkOverlay net) {
        NetworkState networkState = makeNetworkStateFromResults(enumerationProgress, net);
        networkState.documentSelfLink = oldDocument.documentSelfLink;
        networkState.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            networkState.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Network %s", net.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), oldDocument.documentSelfLink))
                .setBody(networkState)
                .setCompletion(trackNetwork(enumerationProgress, net))
                .sendWith(this);

        if (!VimNames.TYPE_NETWORK.equals(net.getId().getType())) {
            return;
        }

        // represent a Network also as a subnet
        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                UriUtils.getLastPathSegment(networkState.documentSelfLink));
        subnet.id = subnet.name = net.getName();
        subnet.endpointLink = enumerationProgress.getRequest().endpointLink;
        subnet.networkLink = networkState.documentSelfLink;
        subnet.tenantLinks = enumerationProgress.getTenantLinks();
        enumerationProgress.touchResource(subnet.documentSelfLink);

        CustomProperties.of(subnet)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), SubnetService.FACTORY_LINK))
                .setBody(subnet)
                .sendWith(this);
    }

    private void createNewNetwork(EnumerationProgress enumerationProgress, NetworkOverlay net) {
        NetworkState networkState = makeNetworkStateFromResults(enumerationProgress, net);
        networkState.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), NetworkService.FACTORY_LINK))
                .setBody(networkState)
                .setCompletion((o, e) -> {
                    trackNetwork(enumerationProgress, net).handle(o, e);
                    Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(),
                            ResourceGroupService.FACTORY_LINK))
                            .setBody(makeNetworkGroup(net, enumerationProgress))
                            .sendWith(this);
                })
                .sendWith(this);

        logFine(() -> String.format("Found new Network %s", net.getName()));

        if (!VimNames.TYPE_NETWORK.equals(net.getId().getType())) {
            return;
        }

        SubnetState subnet = new SubnetState();
        subnet.documentSelfLink = UriUtils.buildUriPath(SubnetService.FACTORY_LINK,
                UriUtils.getLastPathSegment(networkState.documentSelfLink));
        subnet.id = subnet.name = net.getName();
        subnet.endpointLink = enumerationProgress.getRequest().endpointLink;
        subnet.networkLink = networkState.documentSelfLink;
        subnet.tenantLinks = enumerationProgress.getTenantLinks();

        CustomProperties.of(subnet)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), SubnetService.FACTORY_LINK))
                .setBody(subnet)
                .sendWith(this);
    }

    private String getSelfLinkFromOperation(Operation o) {
        return o.getBody(ServiceDocument.class).documentSelfLink;
    }

    private CompletionHandler trackDatastore(EnumerationProgress enumerationProgress,
            DatastoreOverlay ds) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getDatastoreTracker().track(ds.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getDatastoreTracker().track(ds.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackStoragePolicy(EnumerationProgress enumerationProgress,
            StoragePolicyOverlay sp) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e != null) {
                logFine(() -> String
                        .format("Error in syncing resource group for Storage Policy %s",
                                sp.getName()));
            }
            enumerationProgress.getStoragePolicyTracker().track();
        };
    }

    private CompletionHandler trackVm(EnumerationProgress enumerationProgress) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            enumerationProgress.getVmTracker().arrive();
        };
    }

    private CompletionHandler trackComputeResource(EnumerationProgress enumerationProgress,
            ComputeResourceOverlay cr) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getComputeResourceTracker().track(cr.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getComputeResourceTracker().track(cr.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackHostSystem(EnumerationProgress enumerationProgress,
            HostSystemOverlay hs) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getHostSystemTracker().track(hs.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getHostSystemTracker().track(hs.getParent(), ResourceTracker.ERROR);
            }
        };
    }

    private CompletionHandler trackNetwork(EnumerationProgress enumerationProgress,
            NetworkOverlay net) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getNetworkTracker().track(net.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getNetworkTracker().track(net.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private NetworkState makeNetworkStateFromResults(EnumerationProgress enumerationProgress, NetworkOverlay net) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        ComputeStateWithDescription parent = enumerationProgress.getParent();

        NetworkState state = new NetworkState();

        state.documentSelfLink = NetworkService.FACTORY_LINK + "/" + this.getHost().nextUUID();
        state.id = state.name = net.getName();
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        state.regionId = enumerationProgress.getRegionId();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;
        state.authCredentialsLink = parent.description.authCredentialsLink;

        URI ref = parent.description.instanceAdapterReference;
        state.instanceAdapterReference = AdapterUriUtil.buildAdapterUri(ref.getPort(),
                VSphereUriPaths.DVS_NETWORK_SERVICE);

        CustomProperties custProp = CustomProperties.of(state)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TYPE, net.getId().getType());

        if (net.getSummary() instanceof OpaqueNetworkSummary) {
            OpaqueNetworkSummary ons = (OpaqueNetworkSummary) net.getSummary();
            custProp.put(NsxProperties.OPAQUE_NET_ID, ons.getOpaqueNetworkId());
            custProp.put(NsxProperties.OPAQUE_NET_TYPE, ons.getOpaqueNetworkType());
        }

        if (net.getId().getType().equals(VimNames.TYPE_DVS)) {
            // dvs'es have a stable link
            state.documentSelfLink = buildStableDvsLink(net.getId(), request.endpointLink);

            custProp.put(DvsProperties.DVS_UUID, net.getDvsUuid());
        }

        return state;
    }

    private String buildStableDvsLink(ManagedObjectReference ref, String endpointLink) {
        return NetworkService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, endpointLink);
    }

    private QueryTask queryForNetwork(EnumerationProgress ctx, String name) {
        URI adapterManagementReference = ctx.getRequest().adapterManagementReference;
        String regionId = ctx.getRegionId();

        Builder builder = Query.Builder.create()
                .addFieldClause(NetworkState.FIELD_NAME_ADAPTER_MANAGEMENT_REFERENCE,
                        adapterManagementReference.toString())
                .addKindFieldClause(NetworkState.class)
                .addCaseInsensitiveFieldClause(NetworkState.FIELD_NAME_NAME, name, MatchType.TERM, Occurance.MUST_OCCUR)
                .addFieldClause(NetworkState.FIELD_NAME_REGION_ID, regionId);
        QueryUtils.addEndpointLink(builder, NetworkState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * Process the found storage policy by creating a new resource group if not found already. If
     * it already present, then update its properties. Process the updates on its compatible
     * datastores.
     */
    private void processFoundStoragePolicy(EnumerationProgress enumerationProgress,
            StoragePolicyOverlay sp) {
        QueryTask task = queryForStoragePolicy(enumerationProgress, sp.getProfileId(), sp.getName());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewStoragePolicy(enumerationProgress, sp);
            } else {
                ResourceGroupState oldDocument = convertOnlyResultToDocument(result,
                        ResourceGroupState.class);
                updateStoragePolicy(oldDocument, enumerationProgress, sp);
            }
        });
    }

    private QueryTask queryForStoragePolicy(EnumerationProgress ctx, String id, String name) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ResourceState.FIELD_NAME_ID, id)
                .addFieldClause(ResourceState.FIELD_NAME_REGION_ID, ctx.getRegionId())
                .addCaseInsensitiveFieldClause(ResourceState.FIELD_NAME_NAME, name,
                        MatchType.TERM, Occurance.MUST_OCCUR);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private void createNewStoragePolicy(EnumerationProgress enumerationProgress,
            StoragePolicyOverlay sp) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();
        ResourceGroupState rgState = makeStoragePolicyFromResults(request, sp, regionId);
        rgState.tenantLinks = enumerationProgress.getTenantLinks();
        logFine(() -> String.format("Found new Storage Policy %s", sp.getName()));

        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), ResourceGroupService.FACTORY_LINK))
                .setBody(rgState)
                .setCompletion((o, e) -> {
                    trackStoragePolicy(enumerationProgress, sp).handle(o, e);
                    // Update all compatible datastores group link with the self link of this
                    // storage policy
                    updateDataStoreWithStoragePolicyGroup(enumerationProgress, sp,
                            o.getBody(ResourceGroupState.class).documentSelfLink);
                })
                .sendWith(this);
    }

    private void updateStoragePolicy(ResourceGroupState oldDocument,
            EnumerationProgress enumerationProgress, StoragePolicyOverlay sp) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();

        ResourceGroupState rgState = makeStoragePolicyFromResults(request, sp, regionId);
        rgState.documentSelfLink = oldDocument.documentSelfLink;

        if (oldDocument.tenantLinks == null) {
            rgState.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Storage %s", sp.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), rgState.documentSelfLink))
                .setBody(rgState)
                .setCompletion((o, e) -> {
                    trackStoragePolicy(enumerationProgress, sp).handle(o, e);
                    if (e == null) {
                        // Update all compatible datastores group link with the self link of this
                        // storage policy
                        updateDataStoreWithStoragePolicyGroup(enumerationProgress, sp,
                                o.getBody(ResourceGroupState.class).documentSelfLink);
                    }
                }).sendWith(this);
    }

    private ResourceGroupState makeStoragePolicyFromResults(ComputeEnumerateResourceRequest request,
            StoragePolicyOverlay sp, String regionId) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = sp.getProfileId();
        res.name = sp.getName();
        res.desc = sp.getDescription();
        res.regionId = regionId;
        res.customProperties = sp.getCapabilities();
        CustomProperties.of(res)
                .put(ComputeProperties.RESOURCE_TYPE_KEY, sp.getType())
                .put(ComputeProperties.ENDPOINT_LINK_PROP_NAME, request.endpointLink);

        return res;
    }

    private void updateDataStoreWithStoragePolicyGroup(EnumerationProgress ctx,
            StoragePolicyOverlay sp, String selfLink) {
        List<Operation> getOps = new ArrayList<>();
        sp.getDatastoreNames().stream().forEach(name -> {
            String dataStoreLink = ctx.getDatastoreTracker()
                    .getSelfLink(name, VimNames.TYPE_DATASTORE);
            if (dataStoreLink != null && !ResourceTracker.ERROR.equals(dataStoreLink)) {
                getOps.add(Operation.createGet(
                        PhotonModelUriUtils.createInventoryUri(getHost(), dataStoreLink)));
            }
        });

        if (!getOps.isEmpty()) {
            OperationJoin.create(getOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            logFine(() -> String.format("Syncing Storage policy failed %s",
                                    Utils.toString(exs)));
                        } else {
                            QueryTask task = queryForStorage(ctx, null, selfLink);
                            withTaskResults(task, result -> {
                                // Call patch on all to update the group links
                                updateStorageDescription(ops.values().stream(), selfLink, result);
                            });

                        }
                    }).sendWith(this);
        }
    }

    private void updateStorageDescription(Stream<Operation> opStream, String spSelfLink,
            ServiceDocumentQueryResult result) {
        List<Operation> patchOps = new ArrayList<>();
        List<String> originalLinks = new ArrayList<>();
        if (result.documentLinks != null) {
            originalLinks.addAll(result.documentLinks);
        }

        opStream.forEach(op -> {
            StorageDescription storageDescription = op.getBody
                    (StorageDescription.class);
            if (result.documentLinks != null && result.documentLinks
                    .contains(storageDescription.documentSelfLink)) {
                originalLinks.remove(storageDescription.documentSelfLink);
            } else {
                if (storageDescription.groupLinks == null) {
                    storageDescription.groupLinks = new HashSet<>();
                }
                storageDescription.groupLinks.add(spSelfLink);
                patchOps.add(Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(),
                        storageDescription.documentSelfLink))
                        .setBody(storageDescription));
            }
        });

        // In this case, we need to update the datastore by removing the policy group link
        if (!originalLinks.isEmpty()) {
            originalLinks.stream().forEach(link -> {
                StorageDescription storageDescription = Utils
                        .fromJson(result.documents.get(link), StorageDescription.class);
                if (storageDescription.groupLinks != null) {
                    storageDescription.groupLinks.remove(spSelfLink);
                }
                patchOps.add(Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(),
                        storageDescription.documentSelfLink))
                        .setBody(storageDescription));
            });
        }

        if (!patchOps.isEmpty()) {
            OperationJoin.create(patchOps)
                    .setCompletion((ops, exs) -> {
                        if (exs != null) {
                            logFine(() -> String.format("Syncing Storage policy failed %s",
                                    Utils.toString(exs)));
                        }
                    }).sendWith(this);
        }
    }

    private void processFoundDatastore(EnumerationProgress enumerationProgress, DatastoreOverlay ds) {
        QueryTask task = queryForStorage(enumerationProgress, ds.getName(), null);

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewStorageDescription(enumerationProgress, ds);
            } else {
                StorageDescription oldDocument = convertOnlyResultToDocument(result,
                        StorageDescription.class);
                updateStorageDescription(oldDocument, enumerationProgress, ds);
            }
        });
    }

    private void updateStorageDescription(StorageDescription oldDocument,
            EnumerationProgress enumerationProgress, DatastoreOverlay ds) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();

        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.documentSelfLink = oldDocument.documentSelfLink;
        desc.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            desc.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing Storage %s", ds.getName()));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .setCompletion((o, e) -> {
                    trackDatastore(enumerationProgress, ds).handle(o, e);
                    if (e == null) {
                        submitWorkToVSpherePool(() -> {
                            updateLocalTags(enumerationProgress, ds, o.getBody(ResourceState.class));
                            updateStorageStats(ds, o.getBody(ServiceDocument.class).documentSelfLink);
                        });
                    }
                }).sendWith(this);
    }

    private void createNewStorageDescription(EnumerationProgress enumerationProgress,
            DatastoreOverlay ds) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String regionId = enumerationProgress.getRegionId();
        StorageDescription desc = makeStorageFromResults(request, ds, regionId);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        logFine(() -> String.format("Found new Datastore %s", ds.getName()));

        submitWorkToVSpherePool(() -> {
            populateTags(enumerationProgress, ds, desc);

            Operation.createPost(
                    PhotonModelUriUtils.createInventoryUri(getHost(), StorageDescriptionService.FACTORY_LINK))
                    .setBody(desc)
                    .setCompletion((o, e) -> {
                        trackDatastore(enumerationProgress, ds).handle(o, e);
                        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(),
                                ResourceGroupService.FACTORY_LINK))
                                .setBody(makeStorageGroup(ds, enumerationProgress))
                                .sendWith(this);
                        updateStorageStats(ds, o.getBody(ServiceDocument.class).documentSelfLink);
                    })
                    .sendWith(this);
        });
    }

    private void updateStorageStats(DatastoreOverlay ds, String selfLink) {
        ResourceMetrics metrics = new ResourceMetrics();
        metrics.timestampMicrosUtc = Utils.getNowMicrosUtc();
        metrics.documentSelfLink = StatsUtil.getMetricKey(selfLink, metrics.timestampMicrosUtc);
        metrics.entries = new HashMap<>();
        metrics.entries.put(STORAGE_USED_BYTES, (double) ds.getCapacityBytes() - ds.getFreeSpaceBytes());
        metrics.entries.put(STORAGE_AVAILABLE_BYTES, (double) ds.getFreeSpaceBytes());
        metrics.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.DAYS.toMicros(
                SingleResourceStatsCollectionTaskService.EXPIRATION_INTERVAL);

        metrics.customProperties = new HashMap<>();
        metrics.customProperties
                .put(ResourceMetrics.PROPERTY_RESOURCE_LINK, selfLink);

        Operation.createPost(UriUtils.buildUri(
                    ClusterUtil.getClusterUri(getHost(), ServiceTypeCluster.METRIC_SERVICE),
                    ResourceMetricsService.FACTORY_LINK))
                .setBodyNoCloning(metrics)
                .sendWith(this);
    }

    private ResourceGroupState makeNetworkGroup(NetworkOverlay net, EnumerationProgress ctx) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = net.getName();
        res.name = "Hosts connected to network '" + net.getName() + "'";
        res.tenantLinks = ctx.getTenantLinks();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, net.getId())
                .put(CustomProperties.TARGET_LINK, ctx.getNetworkTracker().getSelfLink(net.getId()));
        res.documentSelfLink = computeGroupStableLink(net.getId(), PREFIX_NETWORK, ctx.getRequest().endpointLink);

        return res;
    }

    private String computeGroupStableLink(ManagedObjectReference ref, String prefix, String endpointLink) {
        return UriUtils.buildUriPath(
                ResourceGroupService.FACTORY_LINK,
                prefix + "-" +
                        VimUtils.buildStableManagedObjectId(ref, endpointLink));
    }

    private ResourceGroupState makeStorageGroup(DatastoreOverlay ds, EnumerationProgress ctx) {
        ResourceGroupState res = new ResourceGroupState();
        res.id = ds.getName();
        res.name = "Hosts that can access datastore '" + ds.getName() + "'";
        res.tenantLinks = ctx.getTenantLinks();
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(CustomProperties.TARGET_LINK, ctx.getDatastoreTracker().getSelfLink(ds.getId()));
        res.documentSelfLink = computeGroupStableLink(ds.getId(), PREFIX_DATASTORE, ctx.getRequest().endpointLink);

        return res;
    }

    private StorageDescription makeStorageFromResults(ComputeEnumerateResourceRequest request,
            DatastoreOverlay ds, String regionId) {
        StorageDescription res = new StorageDescription();
        res.id = res.name = ds.getName();
        res.type = ds.getType();
        res.resourcePoolLink = request.resourcePoolLink;
        res.endpointLink = request.endpointLink;
        res.adapterManagementReference = request.adapterManagementReference;
        res.capacityBytes = ds.getCapacityBytes();
        res.regionId = regionId;
        CustomProperties.of(res)
                .put(CustomProperties.MOREF, ds.getId())
                .put(STORAGE_USED_BYTES, ds.getCapacityBytes() - ds.getFreeSpaceBytes())
                .put(STORAGE_AVAILABLE_BYTES, ds.getFreeSpaceBytes());

        return res;
    }

    private QueryTask queryForStorage(EnumerationProgress ctx, String name, String groupLink) {
        Builder builder = Query.Builder.create()
                .addFieldClause(StorageDescription.FIELD_NAME_ADAPTER_REFERENCE,
                        ctx.getRequest().adapterManagementReference.toString())
                .addFieldClause(StorageDescription.FIELD_NAME_REGION_ID, ctx.getRegionId());

        if (name != null) {
            builder.addCaseInsensitiveFieldClause(StorageDescription.FIELD_NAME_NAME, name,
                    MatchType.TERM, Occurance.MUST_OCCUR);
        }
        if (groupLink != null) {
            builder.addCollectionItemClause(ResourceState.FIELD_NAME_GROUP_LINKS, groupLink);
        }
        QueryUtils.addEndpointLink(builder, StorageDescription.class,
                ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * Either creates a new Compute or update an already existing one. Existence is checked by
     * querying for a compute with id equals to moref value of a cluster whose parent is the Compute
     * from the request.
     *
     * @param enumerationProgress
     * @param cr
     */
    private void processFoundComputeResource(EnumerationProgress enumerationProgress, ComputeResourceOverlay cr) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForCluster(enumerationProgress, request.resourceLink(), cr.getId().getValue());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewComputeResource(enumerationProgress, cr);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                updateCluster(oldDocument, enumerationProgress, cr);
            }
        });
    }

    private <T> T convertOnlyResultToDocument(ServiceDocumentQueryResult result, Class<T> type) {
        return Utils.fromJson(result.documents.values().iterator().next(), type);
    }

    private void updateCluster(ComputeState oldDocument,
            EnumerationProgress enumerationProgress, ComputeResourceOverlay cr) {
        ComputeState state = makeComputeResourceFromResults(enumerationProgress, cr);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing ComputeResource %s", oldDocument.documentSelfLink));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackComputeResource(enumerationProgress, cr).handle(o, e);
                    if (e == null) {
                        submitWorkToVSpherePool(()
                                -> updateLocalTags(enumerationProgress, cr, o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForCluster(enumerationProgress, cr);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private ComputeDescription makeDescriptionForResourcePool(EnumerationProgress enumerationProgress,
            ResourcePoolOverlay rp, String rpSelfLink) {
        ComputeDescription res = new ComputeDescription();
        res.name = rp.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, UriUtils.getLastPathSegment(rpSelfLink));

        res.totalMemoryBytes = rp.getMemoryReservationBytes();
        // resource pools CPU is measured in Mhz
        res.cpuCount = 0;
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        res.instanceAdapterReference = enumerationProgress
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationProgress
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationProgress
                .getParent().description.statsAdapterReference;
        res.diskAdapterReference = enumerationProgress
                .getParent().description.diskAdapterReference;
        res.regionId = enumerationProgress.getRegionId();

        return res;
    }

    private ComputeDescription makeDescriptionForCluster(EnumerationProgress enumerationProgress,
            ComputeResourceOverlay cr) {
        ComputeDescription res = new ComputeDescription();
        res.name = cr.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, getHost().nextUUID());
        res.cpuCount = cr.getTotalCpuCores();
        if (cr.getTotalCpuCores() != 0) {
            res.cpuMhzPerCore = cr.getTotalCpuMhz() / cr.getTotalCpuCores();
        }
        res.totalMemoryBytes = cr.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        res.instanceAdapterReference = enumerationProgress
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationProgress
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationProgress
                .getParent().description.statsAdapterReference;
        res.diskAdapterReference = enumerationProgress
                .getParent().description.diskAdapterReference;
        res.regionId = enumerationProgress.getRegionId();

        return res;
    }

    private void createNewComputeResource(EnumerationProgress enumerationProgress, ComputeResourceOverlay cr) {
        ComputeDescription desc = makeDescriptionForCluster(enumerationProgress, cr);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeComputeResourceFromResults(enumerationProgress, cr);
        state.tenantLinks = enumerationProgress.getTenantLinks();
        state.descriptionLink = desc.documentSelfLink;

        submitWorkToVSpherePool(() -> {
            populateTags(enumerationProgress, cr, state);

            logFine(() -> String.format("Found new ComputeResource %s", cr.getId().getValue()));
            Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), ComputeService.FACTORY_LINK))
                    .setBody(state)
                    .setCompletion(trackComputeResource(enumerationProgress, cr))
                    .sendWith(this);
        });
    }

    /**
     * After the tags for the ref are retrieved from the endpoint they are posted to the tag service
     * and the selfLinks are collected ready to be used in a {@link ComputeState#tagLinks}.
     *
     * @param endpoint
     * @param ref
     * @param tenantLinks
     * @return
     */
    private Set<String> retrieveTagLinksAndCreateTagsAsync(VapiConnection endpoint,
            ManagedObjectReference ref, List<String> tenantLinks) {
        List<TagState> tags = null;
        try {
            tags = retrieveAttachedTags(endpoint, ref, tenantLinks);
        } catch (IOException | RpcException ignore) {

        }

        return createTagsAsync(tags);
    }

    private Set<String> createTagsAsync(List<TagState> tags) {
        if (tags == null || tags.isEmpty()) {
            return new HashSet<>();
        }

        Stream<Operation> ops = tags.stream()
                .map(s -> Operation
                        .createPost(UriUtils.buildFactoryUri(getHost(), TagService.class))
                        .setBody(s));

        OperationJoin.create(ops)
                .sendWith(this);

        return tags.stream()
                .map(s -> s.documentSelfLink)
                .collect(Collectors.toSet());
    }

    /**
     * Retreives all tags for a MoRef from an endpoint.
     *
     * @param endpoint
     * @param ref
     * @param tenantLinks
     * @return empty list if no tags found, never null
     */
    private List<TagState> retrieveAttachedTags(VapiConnection endpoint,
            ManagedObjectReference ref, List<String> tenantLinks) throws IOException, RpcException {
        TaggingClient taggingClient = endpoint.newTaggingClient();
        List<String> tagIds = taggingClient.getAttachedTags(ref);

        List<TagState> res = new ArrayList<>();
        for (String id : tagIds) {
            TagState cached = this.tagCache.get(id, newTagRetriever(taggingClient));
            if (cached != null) {
                TagState tag = TagsUtil.newTagState(cached.key, cached.value, true, tenantLinks);
                res.add(tag);
            }
        }

        return res;
    }

    /**
     * Builds a function to retrieve tags given and endpoint.
     *
     * @param client
     * @return
     */
    private Function<String, TagState> newTagRetriever(TaggingClient client) {
        return (tagId) -> {
            try {
                ObjectNode tagModel = client.getTagModel(tagId);
                if (tagModel == null) {
                    return null;
                }

                TagState res = new TagState();
                res.value = tagModel.get("name").asText();
                res.key = client.getCategoryName(tagModel.get("category_id").asText());
                return res;
            } catch (IOException | RpcException e) {
                return null;
            }
        };
    }

    private ComputeState makeComputeResourceFromResults(EnumerationProgress enumerationProgress,
            ComputeResourceOverlay cr) {
        ComputeState state = new ComputeState();
        state.id = cr.getId().getValue();
        state.type = ComputeType.VM_HOST;
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        state.regionId = enumerationProgress.getRegionId();
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.adapterManagementReference = enumerationProgress
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = enumerationProgress.getRequest().resourcePoolLink;
        state.groupLinks = getConnectedDatastoresAndNetworks(enumerationProgress, cr.getDatastore(), cr.getNetwork());

        state.name = cr.getName();
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, cr.getId())
                .put(CustomProperties.TYPE, cr.getId().getType());
        return state;
    }

    private QueryTask queryForCluster(EnumerationProgress ctx, String parentComputeLink,
            String moRefId) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink)
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * @param enumerationProgress
     * @param hs
     */
    private void processFoundHostSystem(EnumerationProgress enumerationProgress, HostSystemOverlay hs) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForHostSystem(enumerationProgress, request.resourceLink(), hs.getId().getValue());

        withTaskResults(task, result -> {
            if (result.documentLinks.isEmpty()) {
                createNewHostSystem(enumerationProgress, hs);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                updateHostSystem(oldDocument, enumerationProgress, hs);
            }
        });
    }

    private void updateHostSystem(ComputeState oldDocument, EnumerationProgress enumerationProgress,
            HostSystemOverlay hs) {
        ComputeState state = makeHostSystemFromResults(enumerationProgress, hs);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing HostSystem %s", oldDocument.documentSelfLink));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), state.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackHostSystem(enumerationProgress, hs).handle(o, e);
                    if (e == null) {
                        submitWorkToVSpherePool(()
                                -> updateLocalTags(enumerationProgress, hs, o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(this);

        ComputeDescription desc = makeDescriptionForHost(enumerationProgress, hs);
        desc.documentSelfLink = oldDocument.descriptionLink;
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void submitWorkToVSpherePool(Runnable work) {
        // store context at the moment of submission
        OperationContext orig = OperationContext.getOperationContext();
        VSphereIOThreadPool pool = VSphereIOThreadPoolAllocator.getPool(this);

        pool.submit(() -> {
            OperationContext old = OperationContext.getOperationContext();

            OperationContext.setFrom(orig);
            try {
                work.run();
            } finally {
                OperationContext.restoreOperationContext(old);
            }
        });
    }

    private void createNewHostSystem(EnumerationProgress enumerationProgress, HostSystemOverlay hs) {
        ComputeDescription desc = makeDescriptionForHost(enumerationProgress, hs);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeHostSystemFromResults(enumerationProgress, hs);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationProgress.getTenantLinks();

        submitWorkToVSpherePool(() -> {
            populateTags(enumerationProgress, hs, state);

            logFine(() -> String.format("Found new HostSystem %s", hs.getName()));
            Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), ComputeService.FACTORY_LINK))
                    .setBody(state)
                    .setCompletion(trackHostSystem(enumerationProgress, hs))
                    .sendWith(this);
        });
    }

    private void populateTags(EnumerationProgress enumerationProgress, AbstractOverlay obj,
            ResourceState state) {
        state.tagLinks = retrieveTagLinksAndCreateTagsAsync(enumerationProgress.getEndpoint(),
                obj.getId(), enumerationProgress.getTenantLinks());
    }

    private ComputeDescription makeDescriptionForHost(EnumerationProgress enumerationProgress,
            HostSystemOverlay hs) {
        ComputeDescription res = new ComputeDescription();
        res.name = hs.getName();
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, getHost().nextUUID());
        res.cpuCount = hs.getCoreCount();
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        res.cpuMhzPerCore = hs.getCpuMhz();
        res.totalMemoryBytes = hs.getTotalMemoryBytes();
        res.supportedChildren = Collections.singletonList(ComputeType.VM_GUEST.name());
        res.instanceAdapterReference = enumerationProgress
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationProgress
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationProgress
                .getParent().description.statsAdapterReference;
        res.diskAdapterReference = enumerationProgress
                .getParent().description.diskAdapterReference;
        res.regionId = enumerationProgress.getRegionId();

        return res;
    }

    private ComputeState makeHostSystemFromResults(EnumerationProgress enumerationProgress,
            HostSystemOverlay hs) {
        ComputeState state = new ComputeState();
        state.type = ComputeType.VM_HOST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = enumerationProgress.getRequest().endpointLink;
        state.regionId = enumerationProgress.getRegionId();
        state.id = hs.getId().getValue();
        state.adapterManagementReference = enumerationProgress
                .getRequest().adapterManagementReference;
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = enumerationProgress.getRequest().resourcePoolLink;
        state.groupLinks = getConnectedDatastoresAndNetworks(enumerationProgress, hs.getDatastore(), hs.getNetwork());

        state.name = hs.getName();
        // TODO: retrieve host power state
        state.powerState = PowerState.ON;
        CustomProperties.of(state)
                .put(CustomProperties.MOREF, hs.getId())
                .put(CustomProperties.TYPE, hs.getId().getType());
        return state;
    }

    private Set<String> getConnectedDatastoresAndNetworks(EnumerationProgress ctx,
            List<ManagedObjectReference> datastores, List<ManagedObjectReference> networks) {
        Set<String> res = new TreeSet<>();

        for (ManagedObjectReference ref : datastores) {
            res.add(computeGroupStableLink(ref, PREFIX_DATASTORE, ctx.getRequest().endpointLink));
        }

        for (ManagedObjectReference ref : networks) {
            NetworkOverlay ov = (NetworkOverlay) ctx.getOverlay(ref);
            if (ov.getParentSwitch() != null) {
                // instead of a portgroup add the switch
                res.add(computeGroupStableLink(ov.getParentSwitch(), PREFIX_NETWORK, ctx.getRequest().endpointLink));
            } else if (!VimNames.TYPE_PORTGROUP.equals(ov.getId().getType())) {
                // skip portgroups and care only about opaque nets and standard swtiches
                res.add(computeGroupStableLink(ov.getId(), PREFIX_NETWORK, ctx.getRequest().endpointLink));
            }
        }

        return res;
    }

    /**
     * @param enumerationProgress
     * @param vm
     */
    private void processFoundVm(EnumerationProgress enumerationProgress, VmOverlay vm) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        QueryTask task = queryForVm(enumerationProgress, request.resourceLink(), vm.getInstanceUuid());
        withTaskResults(task, result -> {
            String vmSelfLink = null;
            if (result.documentLinks.isEmpty()) {
                createNewVm(enumerationProgress, vm);
            } else {
                ComputeState oldDocument = convertOnlyResultToDocument(result, ComputeState.class);
                updateVm(oldDocument, enumerationProgress, vm);
                vmSelfLink = oldDocument.documentSelfLink;
            }
            processSnapshots(enumerationProgress, vm, vmSelfLink);
        });
    }

    private void updateVm(ComputeState oldDocument, EnumerationProgress enumerationProgress,
            VmOverlay vm) {
        ComputeState state = makeVmFromResults(enumerationProgress, vm);
        state.documentSelfLink = oldDocument.documentSelfLink;
        state.resourcePoolLink = null;

        if (oldDocument.tenantLinks == null) {
            state.tenantLinks = enumerationProgress.getTenantLinks();
        }

        logFine(() -> String.format("Syncing VM %s", state.documentSelfLink));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), oldDocument.documentSelfLink))
                .setBody(state)
                .setCompletion((o, e) -> {
                    trackVm(enumerationProgress).handle(o, e);
                    if (e == null) {
                        submitWorkToVSpherePool(()
                                -> updateLocalTags(enumerationProgress, vm, o.getBody(ResourceState.class)));
                    }
                })
                .sendWith(this);
    }

    private void updateLocalTags(EnumerationProgress enumerationProgress, AbstractOverlay obj,
            ResourceState patchResponse) {
        List<TagState> tags;
        try {
            tags = retrieveAttachedTags(enumerationProgress.getEndpoint(),
                    obj.getId(),
                    enumerationProgress.getTenantLinks());
        } catch (IOException | RpcException e) {
            logWarning("Error updating local tags for %s", patchResponse.documentSelfLink);
            return;
        }

        Map<String, String> remoteTagMap = new HashMap<>();
        for (TagState ts : tags) {
            remoteTagMap.put(ts.key, ts.value);
        }

        TagsUtil.updateLocalTagStates(this, patchResponse, remoteTagMap);
    }

    private void createNewVm(EnumerationProgress enumerationProgress, VmOverlay vm) {
        ComputeDescription desc = makeDescriptionForVm(enumerationProgress, vm);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(this);

        ComputeState state = makeVmFromResults(enumerationProgress, vm);
        state.descriptionLink = desc.documentSelfLink;
        state.tenantLinks = enumerationProgress.getTenantLinks();

        submitWorkToVSpherePool(() -> {
            populateTags(enumerationProgress, vm, state);
            state.networkInterfaceLinks = new ArrayList<>();
            for (VirtualEthernetCard nic : vm.getNics()) {
                VirtualDeviceBackingInfo backing = nic.getBacking();

                if (backing instanceof VirtualEthernetCardNetworkBackingInfo) {
                    VirtualEthernetCardNetworkBackingInfo veth = (VirtualEthernetCardNetworkBackingInfo) backing;
                    NetworkInterfaceState iface = new NetworkInterfaceState();
                    iface.networkLink = enumerationProgress.getNetworkTracker()
                            .getSelfLink(veth.getNetwork());
                    iface.name = nic.getDeviceInfo().getLabel();
                    iface.documentSelfLink = buildUriPath(NetworkInterfaceService.FACTORY_LINK,
                            getHost().nextUUID());

                    Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(),
                            NetworkInterfaceService.FACTORY_LINK))
                            .setBody(iface)
                            .sendWith(this);

                    state.networkInterfaceLinks.add(iface.documentSelfLink);
                } else {
                    // TODO add support for DVS
                    logFine(() -> String.format("Will not add nic of type %s",
                            backing.getClass().getName()));
                }
            }

            logFine(() -> String.format("Found new VM %s", vm.getInstanceUuid()));
            Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), ComputeService.FACTORY_LINK))
                    .setBody(state)
                    .setCompletion(trackVm(enumerationProgress))
                    .sendWith(this);
        });
    }

    private void processSnapshots(EnumerationProgress enumerationProgress, VmOverlay vm, String
            vmSelfLink) {
        if (vmSelfLink != null) {
            List<VirtualMachineSnapshotTree> rootSnapshotList = vm.getRootSnapshotList();
            if (rootSnapshotList != null) {
                enumerationProgress.resetSnapshotTracker();
                for (VirtualMachineSnapshotTree snapshotTree : rootSnapshotList) {
                    enumerationProgress.getSnapshotTracker().register();
                    processSnapshot(snapshotTree, null, enumerationProgress, vm,
                            vmSelfLink);
                    enumerationProgress.getSnapshotTracker().arrive();
                }
            }
        }
    }

    private void processSnapshot(VirtualMachineSnapshotTree current, String parentLink,
                                 EnumerationProgress enumerationProgress,
                                 VmOverlay vm, String vmSelfLink) {
        QueryTask task = queryForSnapshot(enumerationProgress, current.getId().toString(),
                vmSelfLink);
        withTaskResults(task, (ServiceDocumentQueryResult result) -> {
            SnapshotState snapshotState = constructSnapshot(current, parentLink, vmSelfLink,
                    enumerationProgress, vm);
            if (result.documentLinks.isEmpty()) {
                createSnapshot(snapshotState)
                        .thenCompose(createdSnapshotState ->
                                trackAndProcessChildSnapshots(current, enumerationProgress, vm, vmSelfLink, createdSnapshotState));
            } else {
                SnapshotState oldState = convertOnlyResultToDocument(result, SnapshotState.class);
                updateSnapshot(enumerationProgress, vm, oldState, snapshotState, current.getId().toString())
                        .thenCompose(updatedSnapshotState ->
                                trackAndProcessChildSnapshots(current, enumerationProgress, vm, vmSelfLink, updatedSnapshotState));
            }
        });
    }

    private DeferredResult<Object> trackAndProcessChildSnapshots(VirtualMachineSnapshotTree current, EnumerationProgress enumerationProgress,
                                                                 VmOverlay vm, String vmSelfLink, SnapshotState updatedSnapshotState) {
        trackSnapshot(enumerationProgress, vm);
        List<VirtualMachineSnapshotTree> childSnapshotList = current.getChildSnapshotList();
        if (!CollectionUtils.isEmpty(childSnapshotList)) {
            for (VirtualMachineSnapshotTree childSnapshot : childSnapshotList) {
                processSnapshot(childSnapshot, updatedSnapshotState.documentSelfLink,
                        enumerationProgress, vm, vmSelfLink);
            }
        }
        return DeferredResult.completed(null);
    }

    private QueryTask queryForSnapshot(EnumerationProgress ctx, String id, String vmSelfLink) {
        Builder builder = Query.Builder.create()
                .addFieldClause(SnapshotState.FIELD_NAME_ID, id)
                .addFieldClause(SnapshotState.FIELD_NAME_COMPUTE_LINK, vmSelfLink);

        QueryUtils.addEndpointLink(builder, SnapshotState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    private SnapshotState constructSnapshot(VirtualMachineSnapshotTree current, String parentLink,
            String vmSelfLink, EnumerationProgress enumerationProgress,
            VmOverlay vm) {
        SnapshotState snapshot = new SnapshotState();
        snapshot.computeLink = vmSelfLink;
        snapshot.parentLink = parentLink;
        snapshot.description = current.getDescription();
        //TODO how to determine if the snapshot is current
        //snapshot.isCurrent = current.isQuiesced()
        snapshot.creationTimeMicros = current.getCreateTime().toGregorianCalendar().getTimeInMillis();
        //TODO How to fetch custom properties
        //snapshot.customProperties = current.get
        //TODO what are snapshot grouplinks
        //snapshot.groupLinks
        snapshot.name = current.getName();
        snapshot.regionId = enumerationProgress.getRegionId();
        snapshot.id = current.getId().toString();
        populateTags(enumerationProgress, vm, snapshot);
        snapshot.tenantLinks = enumerationProgress.getTenantLinks();
        if (snapshot.endpointLinks == null) {
            snapshot.endpointLinks = new HashSet<String>();
        }
        snapshot.endpointLinks.add(enumerationProgress.getRequest().endpointLink);
        return snapshot;
    }

    private DeferredResult<SnapshotState> createSnapshot(SnapshotState snapshot) {
        logFine(() -> String.format("Creating new snapshot %s", snapshot.name));
        Operation opCreateSnapshot = Operation.createPost(this, SnapshotService.FACTORY_LINK)
                .setBody(snapshot);
        return this.sendWithDeferredResult(opCreateSnapshot, SnapshotState.class);
    }

    private DeferredResult<SnapshotState> updateSnapshot(EnumerationProgress enumerationProgress,
                                                         VmOverlay vm, SnapshotState oldState,
                                                         SnapshotState newState, String id) {
        newState.documentSelfLink = oldState.documentSelfLink;
        newState.id = id;
        newState.regionId = enumerationProgress.getRegionId();

        DeferredResult<SnapshotState> res = new DeferredResult<>();
        submitWorkToVSpherePool(() -> {
            populateTags(enumerationProgress, vm, newState);
            newState.tenantLinks = enumerationProgress.getTenantLinks();
            logFine(() -> String.format("Syncing snapshot %s", oldState.name));
            Operation opPatchSnapshot = Operation.createPatch(UriUtils.buildUri(getHost(), oldState.documentSelfLink))
                    .setBody(newState);

            sendWithDeferredResult(opPatchSnapshot, SnapshotState.class).handle((snap, e) -> {
                if (e != null) {
                    res.fail(e);
                } else {
                    res.complete(snap);
                }
                return null;
            });
        });

        return res;
    }

    private CompletionHandler trackSnapshot(EnumerationProgress enumerationProgress,
                                            VmOverlay vm) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
        };
    }

    private ComputeDescription makeDescriptionForVm(EnumerationProgress enumerationProgress,
            VmOverlay vm) {
        ComputeDescription res = new ComputeDescription();
        res.name = vm.getName();
        res.endpointLink = enumerationProgress.getRequest().endpointLink;
        res.documentSelfLink =
                buildUriPath(ComputeDescriptionService.FACTORY_LINK, getHost().nextUUID());
        res.instanceAdapterReference = enumerationProgress
                .getParent().description.instanceAdapterReference;
        res.enumerationAdapterReference = enumerationProgress
                .getParent().description.enumerationAdapterReference;
        res.statsAdapterReference = enumerationProgress
                .getParent().description.statsAdapterReference;
        res.powerAdapterReference = enumerationProgress
                .getParent().description.powerAdapterReference;
        res.diskAdapterReference = enumerationProgress
                .getParent().description.diskAdapterReference;

        res.regionId = enumerationProgress.getRegionId();

        res.cpuCount = vm.getNumCpu();
        res.totalMemoryBytes = vm.getMemoryBytes();
        return res;
    }

    private void processFoundResourcePool(EnumerationProgress enumerationProgress, ResourcePoolOverlay rp,
            String ownerName) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();
        String selfLink = buildStableResourcePoolLink(rp.getId(), request.endpointLink);

        Operation.createGet(PhotonModelUriUtils.createInventoryUri(getHost(), selfLink))
                .setCompletion((o, e) -> {
                    if (e == null) {
                        updateResourcePool(enumerationProgress, ownerName, selfLink, rp);
                    } else if (e instanceof ServiceNotFoundException) {
                        createNewResourcePool(enumerationProgress, ownerName, selfLink, rp);
                    } else {
                        trackResourcePool(enumerationProgress, rp).handle(o, e);
                    }
                })
                .sendWith(this);
    }

    private void updateResourcePool(EnumerationProgress enumerationProgress, String ownerName, String selfLink,
            ResourcePoolOverlay rp) {
        ComputeState state = makeResourcePoolFromResults(enumerationProgress, rp, selfLink);
        state.name = rp.makeUserFriendlyName(ownerName);
        state.tenantLinks = enumerationProgress.getTenantLinks();
        state.resourcePoolLink = null;

        ComputeDescription desc = makeDescriptionForResourcePool(enumerationProgress, rp, selfLink);
        state.descriptionLink = desc.documentSelfLink;

        logFine(() -> String.format("Refreshed ResourcePool %s", state.name));
        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), selfLink))
                .setBody(state)
                .setCompletion(trackResourcePool(enumerationProgress, rp))
                .sendWith(this);

        Operation.createPatch(PhotonModelUriUtils.createInventoryUri(getHost(), desc.documentSelfLink))
                .setBody(desc)
                .sendWith(this);
    }

    private void createNewResourcePool(EnumerationProgress enumerationProgress, String ownerName, String selfLink,
            ResourcePoolOverlay rp) {
        ComputeState state = makeResourcePoolFromResults(enumerationProgress, rp, selfLink);
        state.name = rp.makeUserFriendlyName(ownerName);
        state.tenantLinks = enumerationProgress.getTenantLinks();

        ComputeDescription desc = makeDescriptionForResourcePool(enumerationProgress, rp, selfLink);
        desc.tenantLinks = enumerationProgress.getTenantLinks();
        state.descriptionLink = desc.documentSelfLink;

        logFine(() -> String.format("Found new ResourcePool %s", state.name));
        Operation.createPost(PhotonModelUriUtils.createInventoryUri(getHost(), ComputeService.FACTORY_LINK))
                .setBody(state)
                .setCompletion(trackResourcePool(enumerationProgress, rp))
                .sendWith(this);

        Operation.createPost(
                PhotonModelUriUtils.createInventoryUri(getHost(), ComputeDescriptionService.FACTORY_LINK))
                .setBody(desc)
                .sendWith(this);
    }

    private ComputeState makeResourcePoolFromResults(EnumerationProgress enumerationProgress, ResourcePoolOverlay rp,
            String selfLink) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        ComputeState state = new ComputeState();
        state.documentSelfLink = selfLink;
        state.name = rp.getName();
        state.id = rp.getId().getValue();
        state.type = ComputeType.VM_HOST;
        state.powerState = PowerState.ON;
        state.endpointLink = request.endpointLink;
        state.regionId = enumerationProgress.getRegionId();
        state.parentLink = enumerationProgress.getRequest().resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;
        state.adapterManagementReference = request.adapterManagementReference;

        ManagedObjectReference owner = rp.getOwner();
        AbstractOverlay ov = enumerationProgress.getOverlay(owner);
        if (ov instanceof ComputeResourceOverlay) {
            ComputeResourceOverlay cr = (ComputeResourceOverlay) ov;
            state.groupLinks = getConnectedDatastoresAndNetworks(enumerationProgress,
                    cr.getDatastore(), cr.getNetwork());
        } else if (ov instanceof HostSystemOverlay) {
            HostSystemOverlay cr = (HostSystemOverlay) ov;
            state.groupLinks = getConnectedDatastoresAndNetworks(enumerationProgress,
                    cr.getDatastore(), cr.getNetwork());
        }

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, rp.getId())
                .put(CustomProperties.TYPE, VimNames.TYPE_RESOURCE_POOL);
        return state;
    }

    private CompletionHandler trackResourcePool(EnumerationProgress enumerationProgress, ResourcePoolOverlay rp) {
        return (o, e) -> {
            enumerationProgress.touchResource(getSelfLinkFromOperation(o));
            if (e == null) {
                enumerationProgress.getResourcePoolTracker().track(rp.getId(), getSelfLinkFromOperation(o));
            } else {
                enumerationProgress.getResourcePoolTracker().track(rp.getId(), ResourceTracker.ERROR);
            }
        };
    }

    private String buildStableResourcePoolLink(ManagedObjectReference ref, String adapterManagementReference) {
        return ComputeService.FACTORY_LINK + "/"
                + VimUtils.buildStableManagedObjectId(ref, adapterManagementReference);
    }

    /**
     * Make a ComputeState from the request and a vm found in vsphere.
     *
     * @param enumerationProgress
     * @param vm
     * @return
     */
    private ComputeState makeVmFromResults(EnumerationProgress enumerationProgress, VmOverlay vm) {
        ComputeEnumerateResourceRequest request = enumerationProgress.getRequest();

        ComputeState state = new ComputeState();
        state.type = ComputeType.VM_GUEST;
        state.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        state.endpointLink = request.endpointLink;
        state.adapterManagementReference = request.adapterManagementReference;
        state.parentLink = request.resourceLink();
        state.resourcePoolLink = request.resourcePoolLink;

        state.instanceAdapterReference = enumerationProgress.getParent()
                .description.instanceAdapterReference;
        state.enumerationAdapterReference = enumerationProgress.getParent()
                .description.enumerationAdapterReference;
        state.powerAdapterReference = enumerationProgress.getParent()
                .description.powerAdapterReference;

        state.regionId = enumerationProgress.getRegionId();
        state.cpuCount = (long) vm.getNumCpu();
        state.totalMemoryBytes = vm.getMemoryBytes();

        state.hostName = vm.getHostName();
        state.powerState = vm.getPowerState();
        state.primaryMAC = vm.getPrimaryMac();
        if (!vm.isTemplate()) {
            state.address = vm.guessPublicIpV4Address();
        }
        state.id = vm.getInstanceUuid();
        state.name = vm.getName();

        CustomProperties.of(state)
                .put(CustomProperties.MOREF, vm.getId())
                .put(CustomProperties.TYPE, VimNames.TYPE_VM);
        return state;
    }

    /**
     * Executes a direct query and invokes the provided handler with the results.
     *
     * @param task
     * @param handler
     */
    private void withTaskResults(QueryTask task, Consumer<ServiceDocumentQueryResult> handler) {
        task.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT, QueryOption.INDEXED_METADATA);
        task.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(QUERY_TASK_EXPIRY_MICROS);

        QueryUtils.startInventoryQueryTask(this, task)
                .whenComplete((o, e) -> {
                    if (e != null) {
                        logWarning(() -> String.format("Error processing task %s",
                                task.documentSelfLink));
                        return;
                    }

                    handler.accept(o.results);
                });
    }

    /**
     * Builds a query for finding a ComputeState by instanceUuid from vsphere and parent compute
     * link.
     *
     * @param ctx
     *
     * @param parentComputeLink
     * @param instanceUuid
     * @return
     */
    private QueryTask queryForVm(EnumerationProgress ctx, String parentComputeLink,
            String instanceUuid) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, instanceUuid)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);

        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .build();
    }

    /**
     * Builds a query for finding a HostSystems by its manage object reference.
     *
     * @param ctx
     * @param parentComputeLink
     * @param moRefId
     * @return
     */
    private QueryTask queryForHostSystem(EnumerationProgress ctx, String parentComputeLink, String moRefId) {
        Builder builder = Query.Builder.create()
                .addFieldClause(ComputeState.FIELD_NAME_ID, moRefId)
                .addFieldClause(ComputeState.FIELD_NAME_PARENT_LINK, parentComputeLink);
        QueryUtils.addEndpointLink(builder, ComputeState.class, ctx.getRequest().endpointLink);
        QueryUtils.addTenantLinks(builder, ctx.getTenantLinks());

        return QueryTask.Builder.createDirectTask()
                .setQuery(builder.build())
                .addOption(QueryOption.EXPAND_CONTENT)
                .build();
    }

    private void processUpdates(UpdateSet updateSet) {
        // handle PC updates
        // https://jira-hzn.eng.vmware.com/browse/VCOM-17
    }

    private void validate(ComputeEnumerateResourceRequest request) {
        // assume all request are REFRESH requests
        if (request.enumerationAction == null) {
            request.enumerationAction = EnumerationAction.REFRESH;
        }
    }
}
