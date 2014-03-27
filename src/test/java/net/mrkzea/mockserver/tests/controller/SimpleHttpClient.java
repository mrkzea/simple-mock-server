package net.mrkzea.mockserver.tests.controller;

import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.io.Serializable;
import java.net.URI;



public class SimpleHttpClient {


    private ApacheHttpClient4 httpClient;


    public SimpleHttpClient(){
        httpClient = new ApacheHttpClient4();
    }

    public String httpCall(String server, String path) {
        URI url = UriBuilder.fromPath(server + path)
                .build();

        return httpGet(url, String.class);
    }



    private <T extends Serializable> T httpGet(URI uri, Class<T> clazz) {
        try {
            return httpClient.resource(uri).accept(MediaType.APPLICATION_JSON_TYPE).get(clazz);
        } catch (UniformInterfaceException e) {
            return emptyResponse();
        } catch (ClientHandlerException e) {
            return emptyResponse();
        }
    }

    private <T extends Serializable> T emptyResponse() {
        return (T) new String();
    }


}
