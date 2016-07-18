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

package com.vmware.photon.controller.model.adapters.gcp.constants;

/**
 * GCP related constants.
 */
public class GCPConstants {
    // GCP API URIs
    private static final String BASE_URI = "https://www.googleapis.com";
    private static final String GCP_API_VERSION = "v1";
    private static final String BASE_COMPUTE_TEMPLATE_URI = BASE_URI + "/compute/"
            + GCP_API_VERSION + "/projects/%s/zones/%s";
    public static final String LIST_VM_TEMPLATE_URI = BASE_COMPUTE_TEMPLATE_URI + "/instances";

    // GCP API Constants
    public static final String MAX_RESULTS = "maxResults";
    public static final String PAGE_TOKEN = "pageToken";

    // GCP Auth URIs
    private static final String OAUTH_API_VERSION = "v4";
    public static final String TOKEN_REQUEST_URI = BASE_URI + "/oauth2/"
            + OAUTH_API_VERSION + "/token";

    // GCP Auth Constants
    // This is the prefix of the request body, which is used to get access token.
    public static final String TOKEN_REQUEST_BODY_TEMPLATE =
            "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=%s";
    // This is the prefix of the authorization header prefix.
    public static final String AUTH_HEADER_BEARER_PREFIX = "Bearer ";
    public static final String PRIVATE_KEY = "PRIVATE KEY";
    public static final String DEFAULT_AUTH_TYPE = "GoogleAuth";

    // GCP Disk Properties
    public static final String DEFAULT_DISK_SOURCE_IMAGE = "defaultDiskSourceImage";
    public static final String DISK_AUTO_DELETE = "autoDelete";
    public static final String DISK_TYPE_PERSISTENT = "PERSISTENT";
    public static final String DEFAULT_DISK_SERVICE_REFERENCE = "defaultDiskServiceReference";
    public static final long DEFAULT_DISK_CAPACITY = 10000L;

    // GCP CPU Properties
    public static final String CPU_PLATFORM = "CPUPlatform";
    public static final String DEFAULT_CPU_PLATFORM = "Ivy Bridge";
    public static final String DEFAULT_IMAGE_REFERENCE = "Canonical:UbuntuServer:14.04.3-LTS:latest";

    // GCP Instance Status Constants
    public static final String INSTANCE_STATUS_PROVISIONING = "PROVISIONING";
    public static final String INSTANCE_STATUS_STAGING = "STAGING";
    public static final String INSTANCE_STATUS_RUNNING = "RUNNING";
    public static final String INSTANCE_STATUS_STOPPING = "STOPPING";
    public static final String INSTANCE_STATUS_SUSPENDED = "SUSPENDED";
    public static final String INSTANCE_STATUS_SUSPENDING = "SUSPENDING";
    public static final String INSTANCE_STATUS_TERMINATED = "TERMINATED";

    // GCP Time Constants
    public static final long ONE_HOUR_IN_SECOND = 3600L;

    // GCP Operation Constants
    public static final String OPERATION_STATUS_DONE = "DONE";

    // GCP Region Constants
    public static final String UNKNOWN_REGION = "Unknown";
    public static final String EASTERN_US = "Eastern US";
    public static final String CENTRAL_US = "Central US";
    public static final String WESTERN_EUROPE = "Western Europe";
    public static final String EAST_ASIA = "East Asia";

    // GCP Zone Constants
    public static final String US_EAST1_B = "us-east1-b";
    public static final String US_EAST1_C = "us-east1-c";
    public static final String US_EAST1_D = "us-east1-d";
    public static final String US_CENTRAL1_A = "us-central1-a";
    public static final String US_CENTRAL1_B = "us-central1-b";
    public static final String US_CENTRAL1_C = "us-central1-c";
    public static final String US_CENTRAL1_F = "us-central1-f";
    public static final String EUROPE_WEST1_B = "europe-west1-b";
    public static final String EUROPE_WEST1_C = "europe-west1-c";
    public static final String EUROPE_WEST1_D = "europe-west1-d";
    public static final String ASIA_EAST1_A = "asia-east1-a";
    public static final String ASIA_EAST1_B = "asia-east1-b";
    public static final String ASIA_EAST1_C = "asia-east1-c";
}