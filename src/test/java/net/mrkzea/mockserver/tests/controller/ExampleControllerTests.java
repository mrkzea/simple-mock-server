package net.mrkzea.mockserver.tests.controller;


import net.mrkzea.mockserver.MockResponse;
import net.mrkzea.mockserver.MockServerConfig;
import net.mrkzea.mockserver.SimpleMockServer;
import org.junit.Before;
import org.junit.Test;

public class ExampleControllerTests {


    ExampleController controller;


    @Before
    public void setUp(){
        new SimpleMockServer(12345, 20, "net.mrkzea.mockserver");
        controller = new ExampleController("http://localhost:12345");
        // here mock your actual http client to call your mock server instead of actual end rest endpoint

        // then start mock server


    }


    @Test
    @MockServerConfig({
            @MockResponse(
                    url = "/api/path/which/you/are/testing/response1.json",
                    response = "mocks/response1.json")})
    public void testSomeRestResponse(){
        // here make actual controller calls, which will in turn call mock server as configured above
        String response = controller.restCall("/api/path/which/you/are/testing/response1.json");
        System.out.println(response);
    }


}
