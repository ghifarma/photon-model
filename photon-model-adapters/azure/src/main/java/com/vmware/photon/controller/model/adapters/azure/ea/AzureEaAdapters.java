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

package com.vmware.photon.controller.model.adapters.azure.ea;

import java.util.logging.Level;

import com.vmware.photon.controller.model.adapters.azure.AzureUriPaths;
import com.vmware.photon.controller.model.adapters.azure.ea.endpoint.AzureEaEndpointAdapterService;
import com.vmware.photon.controller.model.adapters.azure.ea.enumeration.AzureSubscriptionsEnumerationService;
import com.vmware.photon.controller.model.adapters.azure.ea.stats.AzureCostStatsService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.util.EndpointAdapterUtils;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Helper class that starts azure ea adapters
 */
public class AzureEaAdapters {

    public static final String[] LINKS = {
            AzureCostStatsService.SELF_LINK,
            AzureSubscriptionsEnumerationService.SELF_LINK,
            AzureEaEndpointAdapterService.SELF_LINK };

    /**
     * The link of Azure EA configuration registered in {@link PhotonModelAdaptersRegistryService
     * End-point Adapters Registry}.
     */
    public static String CONFIG_LINK = UriUtils.buildUriPath(
            PhotonModelAdaptersRegistryService.FACTORY_LINK,
            EndpointType.azure_ea.name());

    public static void startServices(ServiceHost host) throws Throwable {
        try {
            // Eventually AzureSubscriptionsEnumerationService
            // will be started inside the cost stats adapter
            host.startService(new AzureCostStatsService());
            host.startService(new AzureSubscriptionsEnumerationService());
            host.startService(new AzureEaEndpointAdapterService());

            EndpointAdapterUtils.registerEndpointAdapters(
                    host, EndpointType.azure_ea, LINKS, AzureUriPaths.AZURE_EA_ADAPTER_LINK_TYPES);
        } catch (Exception e) {
            host.log(Level.WARNING, "Exception staring Azure ea adapters: %s",
                    Utils.toString(e));
        }
    }
}
