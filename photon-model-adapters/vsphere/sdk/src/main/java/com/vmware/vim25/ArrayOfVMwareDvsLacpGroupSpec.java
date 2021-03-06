
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfVMwareDvsLacpGroupSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfVMwareDvsLacpGroupSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="VMwareDvsLacpGroupSpec" type="{urn:vim25}VMwareDvsLacpGroupSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfVMwareDvsLacpGroupSpec", propOrder = {
    "vMwareDvsLacpGroupSpec"
})
public class ArrayOfVMwareDvsLacpGroupSpec {

    @XmlElement(name = "VMwareDvsLacpGroupSpec")
    protected List<VMwareDvsLacpGroupSpec> vMwareDvsLacpGroupSpec;

    /**
     * Gets the value of the vMwareDvsLacpGroupSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vMwareDvsLacpGroupSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVMwareDvsLacpGroupSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VMwareDvsLacpGroupSpec }
     * 
     * 
     */
    public List<VMwareDvsLacpGroupSpec> getVMwareDvsLacpGroupSpec() {
        if (vMwareDvsLacpGroupSpec == null) {
            vMwareDvsLacpGroupSpec = new ArrayList<VMwareDvsLacpGroupSpec>();
        }
        return this.vMwareDvsLacpGroupSpec;
    }

}
