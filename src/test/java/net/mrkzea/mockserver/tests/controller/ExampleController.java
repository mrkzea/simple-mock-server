package net.mrkzea.mockserver.tests.controller;


import net.mrkzea.mockserver.SimpleHttpClient;

public class ExampleController {


    SimpleHttpClient client;
    String server;

    public ExampleController(String server){
        client = new SimpleHttpClient();
        this.server = server;

    }

    public String restCall(String url){
        return client.httpCall(server, url);

    }





}
