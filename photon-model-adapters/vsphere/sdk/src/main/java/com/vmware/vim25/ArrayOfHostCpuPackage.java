
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostCpuPackage complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostCpuPackage"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HostCpuPackage" type="{urn:vim25}HostCpuPackage" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostCpuPackage", propOrder = {
    "hostCpuPackage"
})
public class ArrayOfHostCpuPackage {

    @XmlElement(name = "HostCpuPackage")
    protected List<HostCpuPackage> hostCpuPackage;

    /**
     * Gets the value of the hostCpuPackage property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostCpuPackage property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostCpuPackage().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostCpuPackage }
     * 
     * 
     */
    public List<HostCpuPackage> getHostCpuPackage() {
        if (hostCpuPackage == null) {
            hostCpuPackage = new ArrayList<HostCpuPackage>();
        }
        return this.hostCpuPackage;
    }

}
