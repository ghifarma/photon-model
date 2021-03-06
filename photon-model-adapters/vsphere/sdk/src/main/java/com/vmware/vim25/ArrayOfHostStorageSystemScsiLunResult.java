
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfHostStorageSystemScsiLunResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfHostStorageSystemScsiLunResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="HostStorageSystemScsiLunResult" type="{urn:vim25}HostStorageSystemScsiLunResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfHostStorageSystemScsiLunResult", propOrder = {
    "hostStorageSystemScsiLunResult"
})
public class ArrayOfHostStorageSystemScsiLunResult {

    @XmlElement(name = "HostStorageSystemScsiLunResult")
    protected List<HostStorageSystemScsiLunResult> hostStorageSystemScsiLunResult;

    /**
     * Gets the value of the hostStorageSystemScsiLunResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hostStorageSystemScsiLunResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHostStorageSystemScsiLunResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostStorageSystemScsiLunResult }
     * 
     * 
     */
    public List<HostStorageSystemScsiLunResult> getHostStorageSystemScsiLunResult() {
        if (hostStorageSystemScsiLunResult == null) {
            hostStorageSystemScsiLunResult = new ArrayList<HostStorageSystemScsiLunResult>();
        }
        return this.hostStorageSystemScsiLunResult;
    }

}
