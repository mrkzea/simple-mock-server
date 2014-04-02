package net.mrkzea.mockserver;

import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static net.mrkzea.mockserver.SimpleHttpClient.*;

public class SimpleMockServerTests {

    public static final String HOST = "http://localhost:10000";

    private static SimpleMockServer mockServer;


    @Before
    public void setUp() {
        mockServer = new SimpleMockServer(10000, 20, "net.mrkzea.mockserver");
    }

    @After
    public void clean() {
        mockServer.stopServer();
    }

    public void assertTest(String url, String expectedFile) {
        String request = httpGet(HOST + url);
        Assert.assertEquals(readStream(getClass().getClassLoader().getResourceAsStream(expectedFile)), request);
    }


    @Test
    @SimpleMockServer.MockServerConfig({
            @SimpleMockServer.MockResponse(
                    url = "/server/response1.json",
                    response = "mocks/response1.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response2.json",
                    response = "mocks/response2.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response3.json",
                    response = "mocks/response3.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response4.json",
                    response = "mocks/response4.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response5.json",
                    response = "mocks/response5.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response6.json",
                    response = "mocks/response6.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response7.json",
                    response = "mocks/response7.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response8.json",
                    response = "mocks/response8.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response9.json",
                    response = "mocks/response9.json"),
            @SimpleMockServer.MockResponse(
                    url = "/server/response10.json",
                    response = "mocks/response10.json")
    })
    public void testSingleThreadedServer() {
        File dir = new File(getClass().getClassLoader().getResource("mocks").getFile());
        String[] children = dir.list();
        for (String expectedFile : children) {
            assertTest("/server/" + expectedFile, "mocks/" + expectedFile);
        }
    }


    @Test
    public void testSingleThreadedWithMultipleThreads() throws Exception {
        int nrOfThreads = 10;
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(nrOfThreads);
        Set<Future<Boolean>> futures = new HashSet<Future<Boolean>>();

        ExecutorService executor = Executors.newFixedThreadPool(nrOfThreads);
        for (int i = 0; i < nrOfThreads; i++) {
            Callable callable = new Callable() {
                @Override
                public Object call() throws Exception {
                    try {
                        startGate.await();
                        testSingleThreadedServer();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return false;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    } finally {
                        endGate.countDown();
                    }
                    return true;
                }
            };
            futures.add(executor.submit(callable));
        }
        long start = System.currentTimeMillis();
        startGate.countDown();
        endGate.await();
        long duration = System.currentTimeMillis() - start;
        System.out.println(duration);

        for (Future<Boolean> future : futures) {
            Assert.assertTrue(future.get());
        }
    }


    @Test
    public void testAnnotations() {
        Set<Method> methodsWithinPackage = mockServer.getMethodsWithinPackage("net.mrkzea.mockserver");
        System.out.println(methodsWithinPackage);

        for (Method m : methodsWithinPackage) {
            SimpleMockServer.MockResponse[] value = (SimpleMockServer.MockResponse[]) m.getAnnotation(SimpleMockServer.MockServerConfig.class).value();
            System.out.println(value);
        }
    }


}
