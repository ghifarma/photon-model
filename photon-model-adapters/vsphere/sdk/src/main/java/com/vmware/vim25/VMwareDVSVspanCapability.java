
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDVSVspanCapability complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VMwareDVSVspanCapability"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="mixedDestSupported" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="dvportSupported" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="remoteSourceSupported" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="remoteDestSupported" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="encapRemoteSourceSupported" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="erspanProtocolSupported" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VMwareDVSVspanCapability", propOrder = {
    "mixedDestSupported",
    "dvportSupported",
    "remoteSourceSupported",
    "remoteDestSupported",
    "encapRemoteSourceSupported",
    "erspanProtocolSupported"
})
public class VMwareDVSVspanCapability
    extends DynamicData
{

    protected boolean mixedDestSupported;
    protected boolean dvportSupported;
    protected boolean remoteSourceSupported;
    protected boolean remoteDestSupported;
    protected boolean encapRemoteSourceSupported;
    protected Boolean erspanProtocolSupported;

    /**
     * Gets the value of the mixedDestSupported property.
     * 
     */
    public boolean isMixedDestSupported() {
        return mixedDestSupported;
    }

    /**
     * Sets the value of the mixedDestSupported property.
     * 
     */
    public void setMixedDestSupported(boolean value) {
        this.mixedDestSupported = value;
    }

    /**
     * Gets the value of the dvportSupported property.
     * 
     */
    public boolean isDvportSupported() {
        return dvportSupported;
    }

    /**
     * Sets the value of the dvportSupported property.
     * 
     */
    public void setDvportSupported(boolean value) {
        this.dvportSupported = value;
    }

    /**
     * Gets the value of the remoteSourceSupported property.
     * 
     */
    public boolean isRemoteSourceSupported() {
        return remoteSourceSupported;
    }

    /**
     * Sets the value of the remoteSourceSupported property.
     * 
     */
    public void setRemoteSourceSupported(boolean value) {
        this.remoteSourceSupported = value;
    }

    /**
     * Gets the value of the remoteDestSupported property.
     * 
     */
    public boolean isRemoteDestSupported() {
        return remoteDestSupported;
    }

    /**
     * Sets the value of the remoteDestSupported property.
     * 
     */
    public void setRemoteDestSupported(boolean value) {
        this.remoteDestSupported = value;
    }

    /**
     * Gets the value of the encapRemoteSourceSupported property.
     * 
     */
    public boolean isEncapRemoteSourceSupported() {
        return encapRemoteSourceSupported;
    }

    /**
     * Sets the value of the encapRemoteSourceSupported property.
     * 
     */
    public void setEncapRemoteSourceSupported(boolean value) {
        this.encapRemoteSourceSupported = value;
    }

    /**
     * Gets the value of the erspanProtocolSupported property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isErspanProtocolSupported() {
        return erspanProtocolSupported;
    }

    /**
     * Sets the value of the erspanProtocolSupported property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setErspanProtocolSupported(Boolean value) {
        this.erspanProtocolSupported = value;
    }

}
