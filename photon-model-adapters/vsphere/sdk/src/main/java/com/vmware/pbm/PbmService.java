package com.vmware.pbm;

import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.Service;

/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:23.488+05:30
 * Generated source version: 3.1.6
 * 
 */
@WebServiceClient(name = "PbmService", 
                  wsdlLocation = "classpath:com/vmware/wsdl/pbmService.wsdl",
                  targetNamespace = "urn:pbmService") 
public class PbmService extends Service {

    public final static URL WSDL_LOCATION;

    public final static QName SERVICE = new QName("urn:pbmService", "PbmService");
    public final static QName PbmPort = new QName("urn:pbmService", "PbmPort");
    static {
        URL url = PbmService.class.getClassLoader().getResource("com/vmware/wsdl/pbmService.wsdl");
        if (url == null) {
            java.util.logging.Logger.getLogger(PbmService.class.getName())
                .log(java.util.logging.Level.INFO, 
                     "Can not initialize the default wsdl from {0}", "classpath:com/vmware/wsdl/pbmService.wsdl");
        }       
        WSDL_LOCATION = url;   
    }

    public PbmService(URL wsdlLocation) {
        super(wsdlLocation, SERVICE);
    }

    public PbmService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public PbmService() {
        super(WSDL_LOCATION, SERVICE);
    }
    
    public PbmService(WebServiceFeature ... features) {
        super(WSDL_LOCATION, SERVICE, features);
    }

    public PbmService(URL wsdlLocation, WebServiceFeature ... features) {
        super(wsdlLocation, SERVICE, features);
    }

    public PbmService(URL wsdlLocation, QName serviceName, WebServiceFeature ... features) {
        super(wsdlLocation, serviceName, features);
    }    




    /**
     *
     * @return
     *     returns PbmPortType
     */
    @WebEndpoint(name = "PbmPort")
    public PbmPortType getPbmPort() {
        return super.getPort(PbmPort, PbmPortType.class);
    }

    /**
     * 
     * @param features
     *     A list of {@link javax.xml.ws.WebServiceFeature} to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.
     * @return
     *     returns PbmPortType
     */
    @WebEndpoint(name = "PbmPort")
    public PbmPortType getPbmPort(WebServiceFeature... features) {
        return super.getPort(PbmPort, PbmPortType.class, features);
    }

}
