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

package com.vmware.photon.controller.model.adapters.azure.stats;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultAuthCredentials;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultComputeHost;
import static com.vmware.photon.controller.model.adapters.azure.instance.AzureTestUtil.createDefaultResourcePool;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.createServiceURI;
import static com.vmware.photon.controller.model.tasks.ProvisioningUtils.queryDocumentsAndAssertExpectedCount;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.AzureAdapters;
import com.vmware.photon.controller.model.adapters.azure.enumeration.AzureEnumerationAdapterService;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ResourceEnumerationTaskService.ResourceEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.TestUtils;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.BasicReusableHostTestCase;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.VerificationHost;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * PRE-REQUISITE: An Azure Resource Manager VM named <b>EnumTestVM-DoNotDelete</b>, with diagnostics
 * enabled, is required for the stats collection on compute host to be successful.
 *
 * The test runs stats collection multiple times (periodically) on Azure VM with detailed monitoring enabled.
 * The test verifies if all the stats are being collected in every run and if data points obtained in the
 * current run are newer than the previous run.
 * Test also periodically collects stats on node.
 */
public class TestAzureStatsCollection extends BasicReusableHostTestCase {
    private static final String CUSTOM_DIAGNOSTIC_ENABLED_VM = "EnumTestVM-DoNotDelete";
    private static final String LAST_COLLECTION_TIME_KEY_FOR_VM = "compute-stats-gatherer_" +
            PhotonModelConstants.LAST_SUCCESSFUL_STATS_COLLECTION_TIME;
    private static final String LAST_COLLECTION_TIME_KEY_FOR_HOST = "stats-adapter_" +
            PhotonModelConstants.LAST_SUCCESSFUL_STATS_COLLECTION_TIME;
    private static final String SEPARATOR = ": ";
    private static final String STAT_NAME_MEMORY_AVAILABLE_IN_PERCENT = "MemoryAvailablePercent";
    private static final double BYTES_TO_MB = 1024 * 1024;
    private static final int MEMORY_THRESHOLD_SEVERE = 60;
    private static final int MEMORY_THRESHOLD_WARNING = 40;

    public String clientID = "clientID";
    public String clientKey = "clientKey";
    public String subscriptionId = "subscriptionId";
    public String tenantId = "tenantId";
    public boolean isMock = true;

    // object counts
    public int vmCount = 0;

    private ResourceMetrics resourceMetric;
    private ComputeState computeHost;
    private Level loggingLevelForMemory;
    private URI nodeStatsUri;
    private String resourcePoolLink;
    // This is for GUEST_VM link
    private String enumeratedComputeLink;
    private String authLink;
    private double previousRunLastSuccessfulCollectionTimeInMicrosForVM = 0;
    private double currentRunLastSuccessfulCollectionTimeInMicrosForVM = 0;
    private double previousRunLastSuccessfulCollectionTimeInMicrosForHost = 0;
    private double currentRunLastSuccessfulCollectionTimeInMicrosForHost = 0;
    private double availableMemoryPercentage;
    public double maxMemoryInMb;
    // Azure provides new stats data points every 60 seconds on VMs with detailed monitoring enabled.
    // Frequency slightly greater than 60 seconds ensures a new data point is always available for collection.
    public int enumerationFrequencyInSeconds = 65;
    public int testRunDurationInMinutes = 4;

    @Before
    public void setUp() throws Exception {
        try {
            if (this.computeHost == null) {
                PhotonModelServices.startServices(this.host);
                PhotonModelMetricServices.startServices(this.host);
                PhotonModelTaskServices.startServices(this.host);
                AzureAdapters.startServices(this.host);

                this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
                this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
                this.host.waitForServiceAvailable(AzureAdapters.LINKS);
                this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);

                this.host.setTimeoutSeconds(600);

                // Create a resource pool where the VMs will be housed
                ResourcePoolState outPool = createDefaultResourcePool(this.host);
                this.resourcePoolLink = outPool.documentSelfLink;

                AuthCredentialsServiceState authCredentials = createDefaultAuthCredentials(
                        this.host,
                        this.clientID,
                        this.clientKey,
                        this.subscriptionId,
                        this.tenantId);
                this.authLink = authCredentials.documentSelfLink;

                // create a compute host for the Azure
                this.computeHost = createDefaultComputeHost(this.host, this.resourcePoolLink, this.authLink);
            }

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AzureAdapters.LINKS);
            this.host.waitForServiceAvailable(PhotonModelMetricServices.LINKS);

            this.nodeStatsUri = UriUtils.buildUri(this.host.getUri(), ServiceUriPaths.CORE_MANAGEMENT);

            this.maxMemoryInMb = this.host.getState().systemInfo.maxMemoryByteCount / BYTES_TO_MB;
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    /**
     * Periodically runs and verifies stats collection and prints logs for node stats.
     * @throws Throwable
     */
    @Test
    public void testStatsCollection() throws Throwable {
        runEnumeration();

        ServiceDocumentQueryResult result = queryDocumentsAndAssertExpectedCount(
                this.host, this.vmCount, ComputeService.FACTORY_LINK, false);

        /*
         * Azure provides new stats data points every one minute for VMs with detailed monitoring enabled.
         * This interval is significantly longer for VMs without detailed monitoring.
         * Currently, we cannot enable detailed monitoring through code.
         * We currently use a VM with custom name "EnumTestVM-DoNotDelete" for Azure tests, thus a prerequisite
         * for this test is having a VM with the above name on test account.
         * Here we query ComputeState and check if VM with the required custom name is enumerated.
         * The test only runs if the VM is present and enumerated.
         */
        for (Entry<String, Object> key : result.documents.entrySet()) {
            ComputeState document = Utils.fromJson(key.getValue(), ComputeState.class);
            if (!document.documentSelfLink.equals(this.computeHost.documentSelfLink)
                    && document.id.toLowerCase()
                    .contains(CUSTOM_DIAGNOSTIC_ENABLED_VM.toLowerCase())) {
                this.enumeratedComputeLink = document.documentSelfLink;
                break;
            }
        }

        if (this.enumeratedComputeLink == null) {
            this.host.log(Level.SEVERE, "VM named EnumTestVM-DoNotDelete is either not present on the "
                    + "azure account specified or was not enumerated.");
            return;
        }

        if (!this.isMock) {
            runStatsCollectionPeriodicallyAndCollectNodeStats();

            this.host.waitFor("Timeout while waiting for test run duration", () -> {
                TimeUnit.MINUTES.sleep(this.testRunDurationInMinutes);
                return true;
            });
        }
    }

    /**
     * Runs enumeration.
     * @throws Throwable
     */
    private void runEnumeration() throws Throwable {
        ResourceEnumerationTaskState enumerationTaskState = new ResourceEnumerationTaskState();

        enumerationTaskState.parentComputeLink = this.computeHost.documentSelfLink;
        enumerationTaskState.enumerationAction = EnumerationAction.START;
        enumerationTaskState.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        enumerationTaskState.resourcePoolLink = this.resourcePoolLink;
        if (this.isMock) {
            enumerationTaskState.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        ResourceEnumerationTaskState enumTask = TestUtils
                .doPost(this.host, enumerationTaskState, ResourceEnumerationTaskState.class,
                        UriUtils.buildUri(this.host, ResourceEnumerationTaskService.FACTORY_LINK));

        this.host.waitFor("Error waiting for enumeration task", () -> {
            try {
                ResourceEnumerationTaskState state = this.host
                        .waitForFinishedTask(ResourceEnumerationTaskState.class,
                                enumTask.documentSelfLink);
                if (state != null) {
                    return true;
                }
            } catch (Throwable e) {
                return false;
            }
            return false;
        });
    }

    /**
     * Runs stats collection task, verifies stats collection is done correctly and logs node stats periodically.
     */
    private void runStatsCollectionPeriodicallyAndCollectNodeStats() {
        this.host.getScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                // Test stats for the VM that was just enumerated from Azure.
                this.host.log(Level.INFO, "Collecting stats for VM and Compute Host");
                if (this.enumeratedComputeLink != null) {
                    this.host.waitFor("Error waiting for stats collection...", () -> {
                        resourceStatsCollection(host, null,
                                this.isMock ? EnumSet.of(TaskOption.IS_MOCK) : null);
                        logNodeStats(this.host.getServiceStats(this.nodeStatsUri));
                        verifyStatsCollection(this.enumeratedComputeLink);
                        verifyStatsCollection(this.computeHost.documentSelfLink);
                        return true;
                    });
                }
            } catch (Throwable e) {
                this.host.log(Level.SEVERE, "Error running stats collection" + e.toString());
            }
        }, 0, this.enumerationFrequencyInSeconds, TimeUnit.SECONDS);
    }

    /**
     * Prints logs for node stats (Memory usage).
     * @param statsMap Map containing node stats.
     */
    private void logNodeStats(Map<String, ServiceStat> statsMap) {
        // In case getServiceStats method fails or returns null.
        if (statsMap == null || statsMap.isEmpty()) {
            this.host.log(Level.WARNING, "Error getting memory usage.");
            return;
        }

        this.availableMemoryPercentage = (statsMap.get(
                ServiceHostManagementService.STAT_NAME_AVAILABLE_MEMORY_BYTES_PER_HOUR)
                .latestValue / BYTES_TO_MB) / this.maxMemoryInMb * 100;

        this.loggingLevelForMemory = Level.INFO;

        // Increase logging level if available Memory is less than expected.
        if (this.availableMemoryPercentage > MEMORY_THRESHOLD_SEVERE) {
            this.loggingLevelForMemory = Level.SEVERE;
        } else if (this.availableMemoryPercentage > MEMORY_THRESHOLD_WARNING) {
            this.loggingLevelForMemory = Level.WARNING;
        }

        this.host.log(this.loggingLevelForMemory, STAT_NAME_MEMORY_AVAILABLE_IN_PERCENT
                + SEPARATOR + this.availableMemoryPercentage);
    }

    /**
     * Waits for stats collection task to be finished.
     * @param host
     * @param peerURI
     * @param options
     * @throws Throwable
     */
    public void resourceStatsCollection(VerificationHost host, URI peerURI,
                                               EnumSet<TaskOption> options) throws Throwable {
        StatsCollectionTaskState statsTask = performResourceStatsCollection(
                host, options);

        // Wait for the stats collection task to be completed.
        host.waitForFinishedTask(StatsCollectionTaskState.class,
                createServiceURI(host, peerURI, statsTask.documentSelfLink));
    }

    /**
     * Performs stats collection for given resourcePoolLink.
     * @param host
     * @param options
     */
    public StatsCollectionTaskState performResourceStatsCollection(
            VerificationHost host, EnumSet<TaskOption> options)
            throws Throwable {

        StatsCollectionTaskState statsCollectionTaskState =
                new StatsCollectionTaskState();

        statsCollectionTaskState.resourcePoolLink = this.resourcePoolLink;
        statsCollectionTaskState.options = EnumSet.noneOf(TaskOption.class);

        if (options != null) {
            statsCollectionTaskState.options = options;
        }

        URI uri = UriUtils.buildUri(host, StatsCollectionTaskService.FACTORY_LINK);
        StatsCollectionTaskState statsTask = TestUtils.doPost(
                host, statsCollectionTaskState, StatsCollectionTaskState.class, uri);

        return statsTask;
    }

    /**
     * Verifies whether all the stats are being collected and whether last successful stats collection time
     * is more recent than the time from previous stats run.
     * @param selfLink
     */
    private void verifyStatsCollection(String selfLink) {
        Operation computeStateOp = Operation.createGet(UriUtils.buildUri(this.host, selfLink))
                .setReferer(this.host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.log(Level.SEVERE, "Error getting compute state from document link");
                    }
                });

        Operation response = this.host.waitForResponse(computeStateOp);

        ComputeState computeState = response.getBody(ComputeState.class);

        this.previousRunLastSuccessfulCollectionTimeInMicrosForHost =
                this.currentRunLastSuccessfulCollectionTimeInMicrosForHost;
        this.previousRunLastSuccessfulCollectionTimeInMicrosForVM =
                this.currentRunLastSuccessfulCollectionTimeInMicrosForVM;

        // Verify stats collection based on VM type.
        if (computeState.type == ComputeType.VM_HOST) {
            this.resourceMetric = getResourceMetrics(selfLink, PhotonModelConstants.STORAGE_USED_BYTES);
            assertNotNull("No StorageUsedBytes metric present for VM", this.resourceMetric);
            this.resourceMetric = getResourceMetrics(selfLink, PhotonModelConstants.MEMORY_USED_PERCENT);
            assertNotNull("No MemoryUsedPercent metric present for VM", this.resourceMetric);
            this.resourceMetric = getResourceMetrics(selfLink, PhotonModelConstants.CPU_UTILIZATION_PERCENT);
            assertNotNull("No CpuUtilizationPercent metric present for VM", this.resourceMetric);
            this.resourceMetric = getResourceMetrics(selfLink, LAST_COLLECTION_TIME_KEY_FOR_HOST);
            this.currentRunLastSuccessfulCollectionTimeInMicrosForHost =
                    this.resourceMetric.entries.get(LAST_COLLECTION_TIME_KEY_FOR_HOST);
            assertTrue(this.currentRunLastSuccessfulCollectionTimeInMicrosForHost >
                    this.previousRunLastSuccessfulCollectionTimeInMicrosForHost);
        } else if (computeState.type == ComputeType.VM_GUEST) {
            this.resourceMetric = getResourceMetrics(selfLink, PhotonModelConstants.MEMORY_USED_PERCENT);
            assertNotNull("No MemoryUsedPercent metric present for host", this.resourceMetric);
            this.resourceMetric = getResourceMetrics(selfLink, PhotonModelConstants.CPU_UTILIZATION_PERCENT);
            assertNotNull("No CpuUtilizationPercent metric present for host", this.resourceMetric);
            this.resourceMetric = getResourceMetrics(selfLink, LAST_COLLECTION_TIME_KEY_FOR_VM);
            this.currentRunLastSuccessfulCollectionTimeInMicrosForVM =
                    this.resourceMetric.entries.get(LAST_COLLECTION_TIME_KEY_FOR_VM);
            assertTrue(this.currentRunLastSuccessfulCollectionTimeInMicrosForVM >
                    this.previousRunLastSuccessfulCollectionTimeInMicrosForVM);
        } else {
            this.host.log(Level.SEVERE, "Unable to validate stats collection for this compute type");
        }
    }

    /**
     * Query to get ResourceMetrics document for a specific resource containing a specific metric.
     * @param resourceLink Link to the resource on which stats are being collected.
     * @param metricKey Metric name.
     * @return ResourceMetrics document.
     */
    private ResourceMetrics getResourceMetrics(String resourceLink, String metricKey) {
        QueryTask qt = QueryTask.Builder
                .createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .addOption(QueryOption.SORT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ServiceDocumentDescription.TypeName.STRING)
                .setQuery(QueryTask.Query.Builder.create()
                        .addKindFieldClause(ResourceMetrics.class)
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK,
                                UriUtils.buildUriPath(ResourceMetricsService.FACTORY_LINK,
                                        UriUtils.getLastPathSegment(resourceLink)),
                                QueryTask.QueryTerm.MatchType.PREFIX)
                        .addRangeClause(QueryTask.QuerySpecification
                                        .buildCompositeFieldName(ResourceMetrics.FIELD_NAME_ENTRIES,
                                                metricKey),
                                QueryTask.NumericRange
                                        .createDoubleRange(0.0, Double.MAX_VALUE, true, true))
                        .build())
                .build();
        Operation op = Operation.createPost(UriUtils.buildUri(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS))
                .setReferer(this.host.getUri()).setBody(qt).setCompletion((o, e) -> {
                    if (e != null) {
                        this.host.log(Level.WARNING, e.toString());
                    }
                });
        Operation result = this.host.waitForResponse(op);
        QueryTask qtResult = result.getBody(QueryTask.class);
        ResourceMetrics resourceMetric = null;
        if (qtResult.results.documentLinks.size() > 0) {
            String documentLink = qtResult.results.documentLinks.get(0);
            resourceMetric = Utils.fromJson(qtResult.results.documents.get(documentLink),
                    ResourceMetrics.class);
        }
        return resourceMetric;
    }
}