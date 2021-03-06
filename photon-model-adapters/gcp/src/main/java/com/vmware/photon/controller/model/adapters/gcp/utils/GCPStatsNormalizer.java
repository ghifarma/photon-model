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

package com.vmware.photon.controller.model.adapters.gcp.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.photon.controller.model.adapters.gcp.constants.GCPConstants;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;

/**
 * Converts GCP constants to corresponding Photon-Model constants.
 */
public class GCPStatsNormalizer {
    private static final Map<String, String> PHOTON_MODEL_UNIT_MAP;
    private static final Map<String, String> PHOTON_MODEL_STATS_MAP;

    static {
        // Map of GCP-specific Units to Photon-Model Units
        Map<String, String> unitMap = new HashMap<>();
        unitMap.put(GCPConstants.UNIT_BYTE,
                PhotonModelConstants.UNIT_BYTES);
        unitMap.put(GCPConstants.UNIT_PERCENT,
                PhotonModelConstants.UNIT_PERCENT);
        unitMap.put(GCPConstants.UNIT_COUNT,
                PhotonModelConstants.UNIT_COUNT);
        PHOTON_MODEL_UNIT_MAP = Collections.unmodifiableMap(unitMap);

        // Map of GCP-specific stat keys to Photon-Model stat keys
        Map<String, String> statMap = new HashMap<>();
        statMap.put(GCPConstants.CPU_UTILIZATION,
                PhotonModelConstants.CPU_UTILIZATION_PERCENT);
        statMap.put(GCPConstants.NETWORK_IN_BYTES,
                PhotonModelConstants.NETWORK_IN_BYTES);
        statMap.put(GCPConstants.NETWORK_OUT_BYTES,
                PhotonModelConstants.NETWORK_OUT_BYTES);
        statMap.put(GCPConstants.NETWORK_IN_PACKETS,
                PhotonModelConstants.NETWORK_PACKETS_IN_COUNT);
        statMap.put(GCPConstants.NETWORK_OUT_PACKETS,
                PhotonModelConstants.NETWORK_PACKETS_OUT_COUNT);
        statMap.put(GCPConstants.DISK_READ_BYTES,
                PhotonModelConstants.DISK_READ_BYTES);
        statMap.put(GCPConstants.DISK_READ_OPERATIONS,
                PhotonModelConstants.DISK_READ_OPS_COUNT);
        statMap.put(GCPConstants.DISK_WRITE_BYTES,
                PhotonModelConstants.DISK_WRITE_BYTES);
        statMap.put(GCPConstants.DISK_WRITE_OPERATIONS,
                PhotonModelConstants.DISK_WRITE_OPS_COUNT);
        PHOTON_MODEL_STATS_MAP = Collections.unmodifiableMap(statMap);
    }

    public static String getNormalizedUnitValue(String cloudSpecificUnit) {
        return PHOTON_MODEL_UNIT_MAP.get(cloudSpecificUnit);
    }

    public static String getNormalizedStatKeyValue(String cloudSpecificStatKey) {
        return PHOTON_MODEL_STATS_MAP.get(cloudSpecificStatKey);
    }
}