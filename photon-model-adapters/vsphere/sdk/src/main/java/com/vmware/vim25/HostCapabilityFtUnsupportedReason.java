
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostCapabilityFtUnsupportedReason.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostCapabilityFtUnsupportedReason"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="vMotionNotLicensed"/&gt;
 *     &lt;enumeration value="missingVMotionNic"/&gt;
 *     &lt;enumeration value="missingFTLoggingNic"/&gt;
 *     &lt;enumeration value="ftNotLicensed"/&gt;
 *     &lt;enumeration value="haAgentIssue"/&gt;
 *     &lt;enumeration value="unsupportedProduct"/&gt;
 *     &lt;enumeration value="cpuHvUnsupported"/&gt;
 *     &lt;enumeration value="cpuHwmmuUnsupported"/&gt;
 *     &lt;enumeration value="cpuHvDisabled"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostCapabilityFtUnsupportedReason")
@XmlEnum
public enum HostCapabilityFtUnsupportedReason {

    @XmlEnumValue("vMotionNotLicensed")
    V_MOTION_NOT_LICENSED("vMotionNotLicensed"),
    @XmlEnumValue("missingVMotionNic")
    MISSING_V_MOTION_NIC("missingVMotionNic"),
    @XmlEnumValue("missingFTLoggingNic")
    MISSING_FT_LOGGING_NIC("missingFTLoggingNic"),
    @XmlEnumValue("ftNotLicensed")
    FT_NOT_LICENSED("ftNotLicensed"),
    @XmlEnumValue("haAgentIssue")
    HA_AGENT_ISSUE("haAgentIssue"),
    @XmlEnumValue("unsupportedProduct")
    UNSUPPORTED_PRODUCT("unsupportedProduct"),
    @XmlEnumValue("cpuHvUnsupported")
    CPU_HV_UNSUPPORTED("cpuHvUnsupported"),
    @XmlEnumValue("cpuHwmmuUnsupported")
    CPU_HWMMU_UNSUPPORTED("cpuHwmmuUnsupported"),
    @XmlEnumValue("cpuHvDisabled")
    CPU_HV_DISABLED("cpuHvDisabled");
    private final String value;

    HostCapabilityFtUnsupportedReason(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostCapabilityFtUnsupportedReason fromValue(String v) {
        for (HostCapabilityFtUnsupportedReason c: HostCapabilityFtUnsupportedReason.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
