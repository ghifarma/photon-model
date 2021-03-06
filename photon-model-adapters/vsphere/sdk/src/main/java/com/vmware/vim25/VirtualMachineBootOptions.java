
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineBootOptions complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VirtualMachineBootOptions"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="bootDelay" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="enterBIOSSetup" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="efiSecureBootEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="bootRetryEnabled" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="bootRetryDelay" type="{http://www.w3.org/2001/XMLSchema}long" minOccurs="0"/&gt;
 *         &lt;element name="bootOrder" type="{urn:vim25}VirtualMachineBootOptionsBootableDevice" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="networkBootProtocol" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VirtualMachineBootOptions", propOrder = {
    "bootDelay",
    "enterBIOSSetup",
    "efiSecureBootEnabled",
    "bootRetryEnabled",
    "bootRetryDelay",
    "bootOrder",
    "networkBootProtocol"
})
public class VirtualMachineBootOptions
    extends DynamicData
{

    protected Long bootDelay;
    protected Boolean enterBIOSSetup;
    protected Boolean efiSecureBootEnabled;
    protected Boolean bootRetryEnabled;
    protected Long bootRetryDelay;
    protected List<VirtualMachineBootOptionsBootableDevice> bootOrder;
    protected String networkBootProtocol;

    /**
     * Gets the value of the bootDelay property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getBootDelay() {
        return bootDelay;
    }

    /**
     * Sets the value of the bootDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setBootDelay(Long value) {
        this.bootDelay = value;
    }

    /**
     * Gets the value of the enterBIOSSetup property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isEnterBIOSSetup() {
        return enterBIOSSetup;
    }

    /**
     * Sets the value of the enterBIOSSetup property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setEnterBIOSSetup(Boolean value) {
        this.enterBIOSSetup = value;
    }

    /**
     * Gets the value of the efiSecureBootEnabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isEfiSecureBootEnabled() {
        return efiSecureBootEnabled;
    }

    /**
     * Sets the value of the efiSecureBootEnabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setEfiSecureBootEnabled(Boolean value) {
        this.efiSecureBootEnabled = value;
    }

    /**
     * Gets the value of the bootRetryEnabled property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isBootRetryEnabled() {
        return bootRetryEnabled;
    }

    /**
     * Sets the value of the bootRetryEnabled property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setBootRetryEnabled(Boolean value) {
        this.bootRetryEnabled = value;
    }

    /**
     * Gets the value of the bootRetryDelay property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getBootRetryDelay() {
        return bootRetryDelay;
    }

    /**
     * Sets the value of the bootRetryDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setBootRetryDelay(Long value) {
        this.bootRetryDelay = value;
    }

    /**
     * Gets the value of the bootOrder property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the bootOrder property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getBootOrder().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VirtualMachineBootOptionsBootableDevice }
     * 
     * 
     */
    public List<VirtualMachineBootOptionsBootableDevice> getBootOrder() {
        if (bootOrder == null) {
            bootOrder = new ArrayList<VirtualMachineBootOptionsBootableDevice>();
        }
        return this.bootOrder;
    }

    /**
     * Gets the value of the networkBootProtocol property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNetworkBootProtocol() {
        return networkBootProtocol;
    }

    /**
     * Sets the value of the networkBootProtocol property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNetworkBootProtocol(String value) {
        this.networkBootProtocol = value;
    }

}
