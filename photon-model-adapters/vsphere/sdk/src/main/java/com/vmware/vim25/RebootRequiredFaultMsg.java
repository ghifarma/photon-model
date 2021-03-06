
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:06.975+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "RebootRequiredFault", targetNamespace = "urn:vim25")
public class RebootRequiredFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.RebootRequired rebootRequiredFault;

    public RebootRequiredFaultMsg() {
        super();
    }
    
    public RebootRequiredFaultMsg(String message) {
        super(message);
    }
    
    public RebootRequiredFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public RebootRequiredFaultMsg(String message, com.vmware.vim25.RebootRequired rebootRequiredFault) {
        super(message);
        this.rebootRequiredFault = rebootRequiredFault;
    }

    public RebootRequiredFaultMsg(String message, com.vmware.vim25.RebootRequired rebootRequiredFault, Throwable cause) {
        super(message, cause);
        this.rebootRequiredFault = rebootRequiredFault;
    }

    public com.vmware.vim25.RebootRequired getFaultInfo() {
        return this.rebootRequiredFault;
    }
}
