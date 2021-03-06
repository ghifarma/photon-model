
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VAppIPAssignmentInfoAllocationSchemes.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VAppIPAssignmentInfoAllocationSchemes"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="dhcp"/&gt;
 *     &lt;enumeration value="ovfenv"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VAppIPAssignmentInfoAllocationSchemes")
@XmlEnum
public enum VAppIPAssignmentInfoAllocationSchemes {

    @XmlEnumValue("dhcp")
    DHCP("dhcp"),
    @XmlEnumValue("ovfenv")
    OVFENV("ovfenv");
    private final String value;

    VAppIPAssignmentInfoAllocationSchemes(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VAppIPAssignmentInfoAllocationSchemes fromValue(String v) {
        for (VAppIPAssignmentInfoAllocationSchemes c: VAppIPAssignmentInfoAllocationSchemes.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
