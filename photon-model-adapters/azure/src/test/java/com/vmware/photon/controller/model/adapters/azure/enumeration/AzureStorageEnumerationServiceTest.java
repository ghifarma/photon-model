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

package com.vmware.photon.controller.model.adapters.azure.enumeration;

import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_ACCOUNTS;
import static com.vmware.photon.controller.model.adapters.azure.constants.AzureConstants.AZURE_STORAGE_TYPE;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.photon.controller.model.adapterapi.ComputeEnumerateResourceRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapters.azure.base.AzureBaseTest;
import com.vmware.photon.controller.model.adapters.util.ComputeEnumerateAdapterRequest;
import com.vmware.photon.controller.model.resources.StorageDescriptionService;
import com.vmware.photon.controller.model.tasks.ProvisioningUtils;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Test to check azure storage account enumeration.
 */
public class AzureStorageEnumerationServiceTest extends AzureBaseTest {

    //Possible types of azure storage accounts
    public static final HashSet<String> typeOfStorageAccounts = new HashSet<String>(Arrays.asList
            ("Standard_LRS", "Standard_ZRS", "Standard_GRS", "Standard_RAGRS", "Premium_LRS"));

    @Test
    public void testAzureStorageAccountEnumeration() throws Throwable {

        kickOffStorageAccountEnumeration();

        // Exit if it is mock. Nothing to assert.
        if (this.isMock) {
            return;
        }

        // Get Storage Descriptions. Atleast one should exist. Maximum of 2 will be fetched.
        ServiceDocumentQueryResult result = ProvisioningUtils
                .queryDocumentsAndAssertExpectedCount(this
                                .getHost(), 1,
                        StorageDescriptionService.FACTORY_LINK, false);

        // Assert on returned storage description fields
        Iterator<Object> itr =  result.documents.values().iterator();
        while (itr.hasNext()) {
            StorageDescriptionService.StorageDescription storageDescription = Utils.fromJson(
                    itr.next(), StorageDescriptionService.StorageDescription.class);
            verifyStorageDescription(storageDescription);
        }

    }

    private void kickOffStorageAccountEnumeration() throws Throwable {

        //Create Compute Enumerate Adapter Request
        ComputeEnumerateResourceRequest resourceRequest = new ComputeEnumerateResourceRequest();
        resourceRequest.endpointLink = this.endpointState.documentSelfLink;
        resourceRequest.enumerationAction = EnumerationAction.START;
        resourceRequest.adapterManagementReference = UriUtils
                .buildUri(AzureEnumerationAdapterService.SELF_LINK);
        resourceRequest.resourcePoolLink = this.resourcePool.documentSelfLink;
        resourceRequest.resourceReference = UriUtils.buildUri(getHost(), "");
        resourceRequest.isMockRequest = this.isMock;
        ComputeEnumerateAdapterRequest request = new ComputeEnumerateAdapterRequest(resourceRequest, this.authState, this.computeStateWithDescription);

        //patch synchronously to enumeration service
        patchServiceSynchronously(AzureStorageEnumerationAdapterService.SELF_LINK, request);

    }

    /**
     * Assert azure storage account properties in storage descriptions
     */
    private static void verifyStorageDescription(StorageDescriptionService.StorageDescription storageDescription) {

        Assert.assertTrue("Azure Storage account name Cannot be empty ", !storageDescription.name
                .isEmpty());
        Assert.assertTrue("Azure Storage account type mismatch", typeOfStorageAccounts
                .contains(storageDescription.type));
        Assert.assertTrue("Azure Storage account encryption is not null",storageDescription
                .supportsEncryption != null);
        Assert.assertTrue("Azure Storage account type not set correctly in custom properties",
                storageDescription.customProperties.get(AZURE_STORAGE_TYPE).equals(AZURE_STORAGE_ACCOUNTS));

    }

}
