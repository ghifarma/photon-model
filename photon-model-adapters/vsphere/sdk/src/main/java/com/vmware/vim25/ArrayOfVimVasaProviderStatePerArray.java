
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfVimVasaProviderStatePerArray complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfVimVasaProviderStatePerArray"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="VimVasaProviderStatePerArray" type="{urn:vim25}VimVasaProviderStatePerArray" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfVimVasaProviderStatePerArray", propOrder = {
    "vimVasaProviderStatePerArray"
})
public class ArrayOfVimVasaProviderStatePerArray {

    @XmlElement(name = "VimVasaProviderStatePerArray")
    protected List<VimVasaProviderStatePerArray> vimVasaProviderStatePerArray;

    /**
     * Gets the value of the vimVasaProviderStatePerArray property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vimVasaProviderStatePerArray property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVimVasaProviderStatePerArray().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VimVasaProviderStatePerArray }
     * 
     * 
     */
    public List<VimVasaProviderStatePerArray> getVimVasaProviderStatePerArray() {
        if (vimVasaProviderStatePerArray == null) {
            vimVasaProviderStatePerArray = new ArrayList<VimVasaProviderStatePerArray>();
        }
        return this.vimVasaProviderStatePerArray;
    }

}
