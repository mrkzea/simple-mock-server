package com.simplemockserver.singlethreaded;

import junit.framework.Assert;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.node.ArrayNode;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class SimpleMockServerTests {

    public static final String HOST = "http://localhost:10000";

    protected ObjectMapper mapper;

    public SimpleMockServerTests(){
        mapper = new ObjectMapper();
        mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
    }

    private static SimpleMockServer mockServer;


    @Before
    public void setUp() throws Exception{
        if (mockServer == null){
            startMockServer(10000, 200);
        }
    }

    public void assertTest(String url, String expectedFile){
        String request = requestUrl(url);
        Assert.assertEquals(readStream(getClass().getClassLoader().getResourceAsStream(expectedFile)), request);
    }


    @Test
    public void testSingleThreadedServer(){
        File dir = new File(getClass().getClassLoader().getResource("mocks").getFile());
        String[] children = dir.list();
        for(String expectedFile : children){
            assertTest("/server/" + expectedFile, "mocks/" + expectedFile);
        }
    }


    @Test
    public void testSingleThreadedWithMultipleThreads() throws Exception{
        int nrOfThreads = 10;
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(nrOfThreads);

        for(int i = 0 ; i < nrOfThreads ; i++){
            Thread t = new Thread(){
                public void run(){
                    try{
                        startGate.await();
                        try{
                            testSingleThreadedServer();
                        }finally {
                            endGate.countDown();
                        }
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
            };
            t.start();
        }
        long start = System.currentTimeMillis();
        startGate.countDown();
        endGate.await();
        long duration= System.currentTimeMillis() - start;
        System.out.println(duration);
    }



    public void startMockServer(int port, int delay) throws Exception{
//        if(mockServer != null && mockServer.isAlive()) stopMockServer();

        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("mockmap.json");
        String mappings = readStream(resourceAsStream);
        ArrayNode mappingsArr = (ArrayNode)mapper.readTree(mappings);

        Map<String, SimpleMockServer.MockResponse> responses = new HashMap<String, SimpleMockServer.MockResponse>();

        for(int i = 0 ; i < mappingsArr.size() ; i++){
            String location = mappingsArr.get(i).get("location").getTextValue();
            String response = mappingsArr.get(i).get("response").getTextValue();

            InputStream s = getClass().getClassLoader().getResourceAsStream(response);
            SimpleMockServer.MockResponse mockResponse = new SimpleMockServer.MockResponse();
            mockResponse.setContent(readStream(s));
            responses.put(location, mockResponse);
        }
        mockServer = new SimpleMockServer(port, responses, delay);
    }


    public static String readStream(InputStream in) {
        StringBuffer expectedBuff = new StringBuffer();

        try {
            BufferedReader i = new BufferedReader(new InputStreamReader(in, "UTF8"));
            String line;
            while ((line = i.readLine()) != null) {
                expectedBuff.append(line);
            }
            i.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return expectedBuff.toString();
    }



    public String requestUrl(String url) {
        StringBuffer buff = new StringBuffer();

        try {
            URL server = new URL(HOST + url);
            URLConnection sc = server.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            sc.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                buff.append(inputLine);
            in.close();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buff.toString();
    }
}
