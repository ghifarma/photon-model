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

package com.vmware.photon.controller.model.adapters.awsadapter;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEYID_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.PRIVATE_KEY_KEY;
import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.REGION_KEY;
import static com.vmware.photon.controller.model.adapters.awsadapter.TestAWSSetupUtils.setAwsClientMockInfo;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.PhotonModelMetricServices;
import com.vmware.photon.controller.model.PhotonModelServices;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.EndpointServiceTests;
import com.vmware.photon.controller.model.tasks.PhotonModelTaskServices;
import com.vmware.xenon.common.BasicReusableHostTestCase;

public class TestAWSEndpointService extends BasicReusableHostTestCase {
    public String accessKey = "access-key";
    public String secretKey = "secret-key";
    public String regionId = "us-east-1";
    public boolean isMock = true;

    @Before
    public void setUp() throws Exception {
        try {
            this.host.setMaintenanceIntervalMicros(TimeUnit.MILLISECONDS.toMicros(10));
            PhotonModelServices.startServices(this.host);
            PhotonModelMetricServices.startServices(this.host);
            PhotonModelTaskServices.startServices(this.host);
            AWSAdapters.startServices(this.host);

            this.host.setTimeoutSeconds(300);

            this.host.waitForServiceAvailable(PhotonModelServices.LINKS);
            this.host.waitForServiceAvailable(PhotonModelTaskServices.LINKS);
            this.host.waitForServiceAvailable(AWSAdapters.LINKS);

            this.host.log(Level.INFO, "Executing test with isMock = %s", this.isMock);
        } catch (Throwable e) {
            throw new Exception(e);
        }
    }

    @Test
    public void testValidateCredentials() throws Throwable {
        new EndpointServiceTests(this.host, this.regionId, this.isMock,
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AWS)
                .testValidateCredentials(createEndpointState());
    }

    @Test
    public void testCreateEndpoint() throws Throwable {
        new EndpointServiceTests(this.host, this.regionId, this.isMock,
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AWS)
                .testCreateEndpoint(createEndpointState());
    }

    @Test
    public void testCreateAndThenValidate() throws Throwable {
        new EndpointServiceTests(this.host, this.regionId, this.isMock,
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AWS)
                .testCreateAndThenValidate(createEndpointState());
    }

    @Test
    public void testShouldFailOnMissingData() throws Throwable {
        new EndpointServiceTests(this.host, this.regionId, this.isMock,
                ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_AWS)
                .testShouldFailOnMissingData(createEndpointState());
    }

    @After
    public void tearDown() throws InterruptedException {
        setAwsClientMockInfo(false, null);
    }

    public EndpointState createEndpointState() {
        EndpointState endpoint = new EndpointState();
        endpoint.endpointType = EndpointType.aws.name();
        endpoint.name = EndpointType.aws.name();
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put(REGION_KEY, this.regionId);
        endpoint.endpointProperties.put(PRIVATE_KEY_KEY, this.secretKey);
        endpoint.endpointProperties.put(PRIVATE_KEYID_KEY, this.accessKey);
        return endpoint;
    }

}
