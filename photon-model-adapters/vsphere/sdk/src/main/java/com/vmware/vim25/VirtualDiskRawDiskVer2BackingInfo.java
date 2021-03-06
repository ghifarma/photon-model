
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskRawDiskVer2BackingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualDiskRawDiskVer2BackingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VirtualDeviceDeviceBackingInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="descriptorFileName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="uuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="changeId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="sharing" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualDiskRawDiskVer2BackingInfo", propOrder = {
    "descriptorFileName",
    "uuid",
    "changeId",
    "sharing"
})
@XmlSeeAlso({
    VirtualDiskPartitionedRawDiskVer2BackingInfo.class
})
public class VirtualDiskRawDiskVer2BackingInfo
    extends VirtualDeviceDeviceBackingInfo
{

    @XmlElement(required = true)
    protected String descriptorFileName;
    protected String uuid;
    protected String changeId;
    protected String sharing;

    /**
     * Gets the value of the descriptorFileName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDescriptorFileName() {
        return descriptorFileName;
    }

    /**
     * Sets the value of the descriptorFileName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDescriptorFileName(String value) {
        this.descriptorFileName = value;
    }

    /**
     * Gets the value of the uuid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Sets the value of the uuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setUuid(String value) {
        this.uuid = value;
    }

    /**
     * Gets the value of the changeId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getChangeId() {
        return changeId;
    }

    /**
     * Sets the value of the changeId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setChangeId(String value) {
        this.changeId = value;
    }

    /**
     * Gets the value of the sharing property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSharing() {
        return sharing;
    }

    /**
     * Sets the value of the sharing property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSharing(String value) {
        this.sharing = value;
    }

}
