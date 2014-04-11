Usage:



Useful to test REST APIs without deploying anything to a container. You can just annotate your test methods and
the mocks server will ensure that the response is available for your controller.


Imagine you want to test an endpoint within your controller which is calling some external provider and retrieving
json response. You would pass your mock client to the controller and configure it to return your expected response.

    @Test
    @SimpleMockServer.MockServerConfig({
            @SimpleMockServer.MockResponse(
                    url = "/api/path/which/you/are/testing/response1.json",
                    response = "mocks/response1.json")})
    public void testSomeRestResponse(){

    }


All you need is one class. SimpleMockServer. All the code is intentionally put in single file to save you from
including any dependencies.

Requires Java8 for compilation.

