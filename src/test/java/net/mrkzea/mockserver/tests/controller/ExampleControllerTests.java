package net.mrkzea.mockserver.tests.controller;


import net.mrkzea.mockserver.SimpleMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ExampleControllerTests {


    ExampleController controller;

    SimpleMockServer mockServer;

    @Before
    public void setUp(){
        mockServer = new SimpleMockServer(12345, 20, "net.mrkzea.mockserver");
        controller = new ExampleController("http://localhost:12345");
    }


    @After
    public void clean(){
        mockServer.stopServer();
    }


    @Test
    @SimpleMockServer.MockServerConfig({
            @SimpleMockServer.MockResponse(
                    url = "/api/path/which/you/are/testing/response1.json",
                    response = "mocks/response1.json")})
    public void testSomeRestResponse(){
        // here make actual controller calls, which will in turn call mock server as configured above
        String response = controller.restCall("/api/path/which/you/are/testing/response1.json");
        System.out.println(response);
    }


}
