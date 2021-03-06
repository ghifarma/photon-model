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

package com.vmware.photon.controller.model.adapters.awsadapter.enumeration;

import static com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants.VOLUME_TYPE;
import static com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory.returnClientManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DeviceType;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Tag;
import com.google.gson.reflect.TypeToken;

import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest.ImageEnumerateRequestType;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSConstants;
import com.vmware.photon.controller.model.adapters.awsadapter.AWSUriPaths;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManager;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSClientManagerFactory;
import com.vmware.photon.controller.model.adapters.awsadapter.util.AWSDeferredResultAsyncHandler;
import com.vmware.photon.controller.model.adapters.util.TaskManager;
import com.vmware.photon.controller.model.adapters.util.enums.EndpointEnumerationProcess;
import com.vmware.photon.controller.model.resources.ImageService;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState.DiskConfiguration;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * AWS image enumeration adapter responsible to enumerate AWS {@link ImageState}s. It handles
 * {@link ImageEnumerateRequest} as send/initiated by {@code ImageEnumerationTaskService}.
 */
public class AWSImageEnumerationAdapterService extends StatelessService {

    public static final String SELF_LINK = AWSUriPaths.AWS_IMAGE_ENUMERATION_ADAPTER;

    public static final String IMAGES_PAGE_SIZE_PROPERTY = "photon-model.adapter.aws.images.page.size";

    /**
     * Get images page size from {@value #IMAGES_PAGE_SIZE_PROPERTY} system property.
     *
     * @return by default return 1000
     */
    public static int getImagesPageSize() {

        final int DEFAULT_IMAGES_PAGE_SIZE = 1000;

        try {
            String imagesPageSizeStr = System.getProperty(
                    IMAGES_PAGE_SIZE_PROPERTY,
                    String.valueOf(DEFAULT_IMAGES_PAGE_SIZE));

            return Integer.parseInt(imagesPageSizeStr);

        } catch (NumberFormatException exc) {

            return DEFAULT_IMAGES_PAGE_SIZE;
        }
    }

    private AWSClientManager clientManager;

    /**
     * {@link EndpointEnumerationProcess} specialization that loads AWS {@link Image}s into
     * {@link ImageState} store.
     */
    private static class AWSImageEnumerationContext
            extends EndpointEnumerationProcess<AWSImageEnumerationContext, ImageState, Image> {

        /**
         * The underlying image-enum request.
         */
        final ImageEnumerateRequest request;

        /**
         * The image-enum task that triggered this request.
         */
        ImageEnumerationTaskState imageEnumTaskState;

        AmazonEC2AsyncClient awsClient;

        PartitionedIterator<Image> awsImages;

        TaskManager taskManager;

        AWSImageEnumerationContext(
                AWSImageEnumerationAdapterService service,
                ImageEnumerateRequest request) {

            super(service, request.resourceReference, ImageState.class, ImageService.FACTORY_LINK);

            this.taskManager = new TaskManager(this.service,
                    request.taskReference,
                    request.resourceLink());

            this.request = request;

            if (request.requestType == ImageEnumerateRequestType.PUBLIC) {
                // Public/Shared images should NOT consider tenantLinks and endpointLink
                setApplyInfraFields(false);
            }
        }

        /**
         * <ul>
         * <li>Extract calling image-enum task state prior end-point loading.</li>
         * <li>Extract end-point region id once end-point state is loaded.</li>
         * </ul>
         */
        @Override
        protected DeferredResult<AWSImageEnumerationContext> getEndpointState(
                AWSImageEnumerationContext context) {

            return DeferredResult.completed(context)
                    .thenCompose(this::getImageEnumTaskState)
                    .thenCompose(ctx -> super.getEndpointState(ctx));
        }

        @Override
        public String getEndpointRegion() {
            return this.request.regionId;
        }

        /**
         * Extract {@link ImageEnumerationTaskState} from {@code request.taskReference} and set it
         * to {@link #imageEnumTaskState}.
         */
        private DeferredResult<AWSImageEnumerationContext> getImageEnumTaskState(
                AWSImageEnumerationContext context) {

            Operation op = Operation.createGet(context.request.taskReference);

            return context.service
                    .sendWithDeferredResult(op, ImageEnumerationTaskState.class)
                    .thenApply(state -> {
                        context.imageEnumTaskState = state;
                        return context;
                    });
        }

        /**
         * Create Amazon client prior core page-by-page enumeration.
         */
        @Override
        protected DeferredResult<AWSImageEnumerationContext> enumeratePageByPage(
                AWSImageEnumerationContext context) {

            return DeferredResult.completed(context)
                    .thenCompose(this::createAmazonClient)
                    .thenCompose(ctx -> super.enumeratePageByPage(ctx));
        }

        protected DeferredResult<AWSImageEnumerationContext> createAmazonClient(
                AWSImageEnumerationContext context) {
            DeferredResult<AWSImageEnumerationContext> r = new DeferredResult<>();
            context.awsClient = ((AWSImageEnumerationAdapterService) context.service).clientManager
                    .getOrCreateEC2Client(
                            context.endpointAuthState,
                            context.getEndpointRegion(),
                            context.service,
                            t -> r.fail(t));
            if (context.awsClient != null) {
                r.complete(context);
            }
            return r;
        }

        @Override
        protected DeferredResult<RemoteResourcesPage> getExternalResources(String nextPageLink) {

            // AWS does not support pagination of images so we internally partition all results thus
            // simulating paging
            return loadExternalResources().thenApply(imagesIterator -> {

                RemoteResourcesPage page = new RemoteResourcesPage();

                if (imagesIterator.hasNext()) {
                    for (Image image : imagesIterator.next()) {
                        page.resourcesPage.put(image.getImageId(), image);
                    }
                }

                // Return a non-null nextPageLink to the parent so we are called back.
                if (imagesIterator.hasNext()) {
                    page.nextPageLink = "awsImages_" + (imagesIterator.pageNumber() + 1);
                } else {
                    this.service.logFine(() -> "Enumerating AWS images: TOTAL number "
                            + imagesIterator.totalNumber());
                }

                return page;
            });
        }

        private DeferredResult<PartitionedIterator<Image>> loadExternalResources() {

            if (this.awsImages != null) {
                return DeferredResult.completed(this.awsImages);
            }

            boolean isPublic = this.request.requestType == ImageEnumerateRequestType.PUBLIC;

            DescribeImagesRequest request = new DescribeImagesRequest()
                    .withFilters(new Filter(AWSConstants.AWS_IMAGE_STATE_FILTER)
                            .withValues(AWSConstants.AWS_IMAGE_STATE_AVAILABLE))
                    .withFilters(new Filter(AWSConstants.AWS_IMAGE_IS_PUBLIC_FILTER)
                            .withValues(Boolean.toString(isPublic)));

            // Apply additional filtering to AWS images (used by tests)
            if (this.imageEnumTaskState.filter != null
                    && !this.imageEnumTaskState.filter.isEmpty()) {

                // List<Filter> type
                final Type listOfFiltersType = new TypeToken<List<Filter>>() {}.getType();

                // Deserialize the JSON string to a list of AWS Filters
                List<Filter> filters = Utils.fromJson(
                        this.imageEnumTaskState.filter, listOfFiltersType);

                // NOTE: use withFilters(Filter...) to append NOT withFilter(List<>)
                request.withFilters(filters.toArray(new Filter[0]));
            }

            String msg = "Enumerating AWS images by " + request;

            // ALL AWS images are returned with a single call, NO pagination!
            AWSDeferredResultAsyncHandler<DescribeImagesRequest, DescribeImagesResult> handler =
                    new AWSDeferredResultAsyncHandler<>(this.service, msg);

            this.awsClient.describeImagesAsync(request, handler);

            return handler.toDeferredResult().thenApply(imagesResult -> {

                // "artificially" partition images once we load them all
                return this.awsImages = new PartitionedIterator<>(
                        imagesResult.getImages(), getImagesPageSize());
            });
        }

        @Override
        protected DeferredResult<LocalStateHolder> buildLocalResourceState(
                Image remoteImage, ImageState existingImageState) {

            LocalStateHolder holder = new LocalStateHolder();

            holder.localState = new ImageState();

            if (existingImageState == null) {
                // Create flow
                if (this.request.requestType == ImageEnumerateRequestType.PUBLIC) {
                    holder.localState.endpointType = this.endpointState.endpointType;
                }
            } else {
                // Update flow: do nothing
            }

            // Both flows - populate from remote Image
            holder.localState.name = remoteImage.getName();
            holder.localState.description = remoteImage.getDescription();
            holder.localState.osFamily = remoteImage.getPlatform();

            holder.localState.diskConfigs = new ArrayList<>();
            if (DeviceType.Ebs == DeviceType.fromValue(remoteImage.getRootDeviceType())) {
                for (BlockDeviceMapping blockDeviceMapping : remoteImage.getBlockDeviceMappings()) {
                    // blockDeviceMapping can be with noDevice
                    EbsBlockDevice ebs = blockDeviceMapping.getEbs();
                    if (ebs != null) {
                        DiskConfiguration diskConfig = new DiskConfiguration();
                        diskConfig.id = blockDeviceMapping.getDeviceName();
                        diskConfig.encrypted = ebs.getEncrypted();
                        diskConfig.persistent = true;
                        if (ebs.getVolumeSize() != null) {
                            diskConfig.capacityMBytes = ebs.getVolumeSize() * 1024;
                        }
                        diskConfig.properties = Collections.singletonMap(
                                VOLUME_TYPE, ebs.getVolumeType());

                        holder.localState.diskConfigs.add(diskConfig);
                    }
                }
            }

            for (Tag remoteImageTag : remoteImage.getTags()) {
                holder.remoteTags.put(remoteImageTag.getKey(), remoteImageTag.getValue());
            }

            return DeferredResult.completed(holder);
        }

        /**
         * <ul>
         * <li>During PUBLIC image enum explicitly set {@code imageType}.</li>
         * <li>During PRIVATE image enum setting of {@code tenantLinks} and {@code endpointType} (by
         * default logic) is enough.</li>
         * </ul>
         */
        @Override
        protected void customizeLocalStatesQuery(Builder qBuilder) {
            if (this.request.requestType == ImageEnumerateRequestType.PUBLIC) {
                qBuilder.addFieldClause(
                        ImageState.FIELD_NAME_ENDPOINT_TYPE,
                        this.endpointState.endpointType);
            }
        }
    }

    public AWSImageEnumerationAdapterService() {

        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    /**
     * Extend default 'start' logic with loading AWS client.
     */
    @Override
    public void handleStart(Operation op) {

        this.clientManager = AWSClientManagerFactory
                .getClientManager(AWSConstants.AwsClientType.EC2);

        super.handleStart(op);
    }

    /**
     * Extend default 'stop' logic with releasing AWS client.
     */
    @Override
    public void handleStop(Operation op) {

        returnClientManager(this.clientManager, AWSConstants.AwsClientType.EC2);

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

        AWSImageEnumerationContext ctx = new AWSImageEnumerationContext(
                this, op.getBody(ImageEnumerateRequest.class));

        if (ctx.request.isMockRequest) {
            // Complete the task with FINISHED
            completeWithSuccess(ctx);
            return;
        }

        final String msg = ctx.request.requestType + " images enumeration";

        logFine(() -> msg + ": STARTED");

        // Start image enumeration process...
        ctx.enumerate()
                .whenComplete((o, e) -> {
                    // Once done patch the calling task with correct stage.
                    if (e == null) {
                        logFine(() -> msg + ": COMPLETED");
                        completeWithSuccess(ctx);
                    } else {
                        logSevere(() -> msg + ": FAILED with " + Utils.toString(e));
                        completeWithFailure(ctx, e);
                    }
                });
    }

    private void completeWithFailure(AWSImageEnumerationContext ctx, Throwable exc) {
        ctx.taskManager.patchTaskToFailure(exc);
    }

    private void completeWithSuccess(AWSImageEnumerationContext ctx) {
        ctx.taskManager.finishTask();
    }

    /**
     * An iterator of sublists of a list, each of the same size (the final list may be smaller).
     */
    public static final class PartitionedIterator<T> implements Iterator<List<T>> {

        private final List<T> originalList;

        private final int partitionSize;

        private int lastIndex = 0;

        private int pageNumber = 0;

        private int totalNumber = 0;

        public PartitionedIterator(List<T> originalList, int partitionSize) {
            // we are tolerant to null values
            this.originalList = originalList == null ? Collections.emptyList() : originalList;
            this.partitionSize = partitionSize;
        }

        @Override
        public boolean hasNext() {
            return this.lastIndex < this.originalList.size();
        }

        /**
         * Returns the next partition from original list.
         */
        @Override
        public List<T> next() {
            if (!hasNext()) {
                throw new NoSuchElementException(
                        getClass().getSimpleName() + " has already been consumed.");
            }

            int beginIndex = this.lastIndex;

            this.lastIndex = Math.min(beginIndex + this.partitionSize, this.originalList.size());

            List<T> page = this.originalList.subList(beginIndex, this.lastIndex);

            this.pageNumber++;
            this.totalNumber += page.size();

            return page;
        }

        /**
         * Return the number of pages returned by {@link #next()} so far.
         */
        public int pageNumber() {
            return this.pageNumber;
        }

        /**
         * Return the total number of elements returned by {@link #next()} so far.
         */
        public int totalNumber() {
            return this.totalNumber;
        }
    }
}
