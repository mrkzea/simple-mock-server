package net.mrkzea.mockserver;


import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;

import javax.net.ServerSocketFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleMockServer extends Thread {


    public SimpleMockServer(int port, long responseDelay, String packages) {

        if (serverStarted){
            return;
        }
        Reflections reflections = new Reflections(packages, new MethodAnnotationsScanner());
        annotatedMethods = reflections.getMethodsAnnotatedWith(MockServerConfig.class);

        List<SimpleMockResponse> simpleMockResponses = new ArrayList<SimpleMockResponse>();
        for (Method method : annotatedMethods) {
            MockServerConfig annotation = method.getAnnotation(MockServerConfig.class);
            MockResponse[] mocks = annotation.value();
            for (MockResponse mock : mocks) {
                String location = mock.url();
                String response = mock.response();
                String contentType = mock.contentType();
                int status = mock.statusCode();
                SimpleMockResponse simpleMockResponse = prepareResponse(location, contentType, response, status);
                simpleMockResponses.add(simpleMockResponse);
            }
        }

        setMockHttpServerResponses(simpleMockResponses.toArray(new SimpleMockResponse[simpleMockResponses.size()]));

        this.responseDelay = responseDelay;
        ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
        try {
            serverSocket = serverSocketFactory.createServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException("Could not construct server", e);
        }
        this.packages = packages;
        startServer();
    }


    public synchronized void startServer() {
        if (serverStarted){
            return;
        }
        start();
        serverStarted = true;
        waitForServerToStart();
    }



    private synchronized void waitForServerToStart() {
        try {
            wait(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }





    public SimpleMockResponse prepareResponse(String location, String contentType, String expectedResponseFile, int status) {
        SimpleMockResponse mockResponse = new SimpleMockResponse();
        mockResponse.setResponseContentType(contentType);
        InputStream s = getClass().getClassLoader().getResourceAsStream(expectedResponseFile);
        if (s == null) {
            mockResponse.setResponseCode(500);
        } else {
            String content = readStream(s);
            mockResponse.setResponseContent(content);
            mockResponse.setResponseUrl(location);
        }
        mockResponse.setResponseCode(status);
        return mockResponse;
    }

    public void processResponses(){

    }

//    public void test(String location, ){
//    }



    public static class SimpleMockResponse {

        private int responseCode = 200;
        private Map<String, String> responseHeaders = new HashMap<String, String>();
        private byte[] responseContent = "received message".getBytes();
        private String responseContentType = "application/json;charset=utf-8";
        private boolean responseContentEchoRequest;
        private String responseUrl;

        public void setResponseHeaders(Map<String, String> headers) {
            responseHeaders.clear();
            responseHeaders.putAll(headers);
        }

        public void setMockResponseHeader(String name, String value) {
            responseHeaders.put(name, value);
        }

        public Map<String, String> getResponseHeaders() {
            return responseHeaders;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseContent(String content) {
            responseContent = content.getBytes();
        }

        public void setResponseContent(byte[] content) {
            responseContent = content;
        }


        public byte[] getResponseContent() {
            return responseContent;
        }

        public void setResponseContentType(String type) {
            responseContentType = type;
        }

        public String getResponseContentType() {
            return responseContentType;
        }

        public boolean getResponseContentEchoRequest() {
            return responseContentEchoRequest;
        }

        public void setResponseUrl(String url) {
            this.responseUrl = url;
        }

        public String getResponseUrl() {
            return responseUrl;
        }

    }



    private Thread serverThread = null;
    private ServerSocket serverSocket = null;
    private volatile boolean serverStarted = false;
    private ServerSocketFactory serverSocketFactory = null;
    private int serverPort;
    private int readTimeOut = 5000;
    private int delayResponseTime = 0;
    private static byte[] NEW_LINE = "\r\n".getBytes();
    private String requestMethod = null;
    private String requestUrl = null;
    private Map<String, List<String>> requestHeaders = new HashMap<String, List<String>>();
    private ByteArrayOutputStream requestContent = new ByteArrayOutputStream();
    private Map<String, SimpleMockResponse> mockHttpServerResponses = new HashMap<String, SimpleMockResponse>();
    private AtomicInteger nrOfRequests = new AtomicInteger(0);
    private long responseDelay;
    private Set<Method> annotatedMethods;
    private String packages; // where the annotations are, for example "net.mrkzea.controller"






    private synchronized void waitForServerToStop() {
        try {
            wait(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }



    public void run() {
        serverThread = Thread.currentThread();
        executeLoop();
    }



    private void executeLoop() {
        serverStarted();
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                HttpProcessor processor = new HttpProcessor(socket);
                processor.run();
            }
        } catch (IOException e) {
            if (e instanceof SocketException) {
                if (!("Socket closed".equalsIgnoreCase(e.getMessage()) || "Socket is closed"
                        .equalsIgnoreCase(e.getMessage()))) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } finally {
            serverStopped();
        }
    }


    private synchronized void serverStarted() {
        notifyAll();
    }


    private synchronized void serverStopped() {
        notifyAll();
    }



    public synchronized void stopServer() {
        requestUrl = null;
        mockHttpServerResponses = null;

        if (!serverStarted) {
            return;
        }

        try {
            serverStarted = false;
            serverThread.interrupt();
            serverSocket.close();
            waitForServerToStop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private class HttpProcessor {

        private Socket socket;

        public HttpProcessor(Socket socket) throws SocketException {
            socket.setSoTimeout(readTimeOut);
            socket.setKeepAlive(false);
            this.socket = socket;
        }

        public void run() {
            try {
                processRequest(socket);
                processResponse(socket);
            } catch (IOException e) {
                if (e instanceof SocketException) {
                    if (!("socket closed".equalsIgnoreCase(e.getMessage()))) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } else {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } finally {
                try {
                    socket.shutdownOutput();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void processRequest(Socket socket) throws IOException {
            requestContent.reset();
            BufferedInputStream is = new BufferedInputStream(socket.getInputStream());
            String requestMethodHeader = new String(readLine(is));
            if (requestMethodHeader == null) {
                return;
            }
            processRequestMethod(requestMethodHeader);
            processRequestHeaders(is);
            processRequestContent(is);
        }

        private void processRequestMethod(String requestMethodHeader) {
            String[] parts = requestMethodHeader.split(" ");
            if (parts.length < 2) {
                throw new RuntimeException("illegal http request");
            }
            requestMethod = parts[0];
            requestUrl = parts[1];
        }

        private void processRequestHeaders(InputStream is) throws IOException {
            requestHeaders.clear();
            System.out.println(requestUrl);
            byte[] line = null;
            while ((line = readLine(is)) != null) {
                String lineStr = new String(line);
                if ("".equals(lineStr.trim())) {
                    break;
                }
                addRequestHeader(lineStr);
            }
        }

        private void processRequestContent(InputStream is) throws NumberFormatException,
                IOException {
            if (!("PUT".equals(requestMethod) || "POST".equals(requestMethod))) {
                return;
            }

            List<String> transferEncodingValues = requestHeaders.get("Transfer-Encoding");
            String transferEncoding =
                    (transferEncodingValues == null || transferEncodingValues.isEmpty()) ? null
                            : transferEncodingValues.get(0);
            if ("chunked".equals(transferEncoding)) {
                processChunkedContent(is);
            } else {
                processRegularContent(is);
            }

            if (mockHttpServerResponses.get(requestUrl).getResponseContentEchoRequest()) {
                mockHttpServerResponses.get(requestUrl).setResponseContent(requestContent.toByteArray());
            }

        }

        private void processRegularContent(InputStream is) throws IOException {
            List<String> contentLengthValues = requestHeaders.get("Content-Length");
            String contentLength =
                    (contentLengthValues == null || contentLengthValues.isEmpty()) ? null
                            : contentLengthValues.get(0);
            if (contentLength == null) {
                return;
            }
            int contentLen = Integer.parseInt(contentLength);
            byte[] bytes = new byte[contentLen];
            is.read(bytes);
            requestContent.write(bytes);
        }

        private void processChunkedContent(InputStream is) throws IOException {
            requestContent.write("".getBytes());
            byte[] chunk = null;
            byte[] line = null;
            boolean lastChunk = false;
            while (!lastChunk && (line = readLine(is)) != null) {

                String lineStr = new String(line);
                if ("0".equals(lineStr)) {
                    lastChunk = true;
                }

                if (!lastChunk) {
                    int chunkLen = Integer.parseInt(lineStr, 16);
                    chunk = getChunk(is, chunkLen);
                    readLine(is);
                    requestContent.write(chunk);
                }
            }
            if (lastChunk) {
                readLine(is);
            }
        }

        private byte[] readLine(InputStream is) throws IOException {
            int n;
            ByteArrayOutputStream tmpOs = new ByteArrayOutputStream();
            while ((n = is.read()) != -1) {
                if (n == '\r') {
                    n = is.read();
                    if (n == '\n') {
                        return tmpOs.toByteArray();
                    } else {
                        tmpOs.write('\r');
                        if (n != -1) {
                            tmpOs.write(n);
                        } else {
                            return tmpOs.toByteArray();
                        }
                    }
                } else if (n == '\n') {
                    return tmpOs.toByteArray();
                } else {
                    tmpOs.write(n);
                }
            }
            return tmpOs.toByteArray();
        }

        private byte[] getChunk(InputStream is, int len) throws IOException {
            ByteArrayOutputStream chunk = new ByteArrayOutputStream();
            int read = 0;
            int totalRead = 0;
            byte[] bytes = new byte[512];
            while (totalRead < len) {
                read = is.read(bytes, 0, Math.min(bytes.length, len - totalRead));
                chunk.write(bytes, 0, read);
                totalRead += read;
            }
            return chunk.toByteArray();
        }

        private void addRequestHeader(String line) {
            String[] parts = line.split(": ");
            List<String> values = requestHeaders.get(parts[0]);
            if (values == null) {
                values = new ArrayList<String>();
                requestHeaders.put(parts[0], values);
            }
            values.add(parts[1]);
        }

        private void processResponse(Socket socket) throws IOException {
            if (!delayResponse())
                return;

            OutputStream sos = socket.getOutputStream();
            BufferedOutputStream os = new BufferedOutputStream(sos);
            SimpleMockResponse mockHttpServerResponse = mockHttpServerResponses.get(requestUrl);
            if (mockHttpServerResponse != null) {
                int mockResponseCode = mockHttpServerResponse.getResponseCode();
                os.write(("HTTP/1.1 " + mockResponseCode).getBytes());
            } else {
                os.write(("HTTP/1.1 " + "500").getBytes());
            }
            os.write(NEW_LINE);
            processResponseHeaders(os);
            processResponseContent(os);
            os.flush();
            nrOfRequests.getAndAdd(1);
        }

        private boolean delayResponse() {
            if (delayResponseTime > 0) {
                try {
                    Thread.sleep(delayResponseTime);
                    return true;
                } catch (InterruptedException e) {
                    return false;
                }
            }
            return true;
        }

        private void processResponseContent(OutputStream os) throws IOException {
            if (mockHttpServerResponses.get(requestUrl) != null) {
                if (mockHttpServerResponses.get(requestUrl).getResponseContent() == null) {
                    return;
                }
                os.write(mockHttpServerResponses.get(requestUrl).getResponseContent());
            }
        }

        private void processResponseHeaders(OutputStream os) throws IOException {
            addServerResponseHeaders();
            System.out.println(requestUrl);
            if (mockHttpServerResponses.get(requestUrl) != null) {
                for (String header : mockHttpServerResponses.get(requestUrl).getResponseHeaders().keySet()) {
                    os.write((header + ": " + mockHttpServerResponses.get(requestUrl)
                            .getResponseHeaders().get(header)).getBytes());
                    os.write(NEW_LINE);
                }
                os.write(NEW_LINE);
            }
        }

        private void addServerResponseHeaders() throws IOException {
            SimpleMockResponse response = mockHttpServerResponses.get(requestUrl);
            if (response != null) {
                Map<String, String> mockResponseHeaders = response.getResponseHeaders();
                mockResponseHeaders.put("Content-Type", response.getResponseContentType());
                mockResponseHeaders.put("Content-Length", response.getResponseContent().length + "");
                mockResponseHeaders.put("Server", "Mock HTTP Server v1.0");
                mockResponseHeaders.put("Connection", "closed");
            }
        }
    }

    public String readStream(InputStream in) {
        StringBuffer expectedBuff = new StringBuffer();

        try {
            BufferedReader i = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = i.readLine()) != null) {
                expectedBuff.append(line);
            }
            i.close();
        } catch (IOException e) {
            System.out.println("ERROR: Unable to read stream!");
        }
        return expectedBuff.toString();
    }


    public void setReadTimeout(int milliseconds) {
        readTimeOut = milliseconds;
    }

    public void setDelayResponse(int milliseconds) {
        delayResponseTime = milliseconds;
    }

    public String getRequestContentAsString() {
        return requestContent.toString();
    }

    public byte[] getRequestContent() {
        return requestContent.toByteArray();
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }


    public void setMockHttpServerResponses(SimpleMockResponse... responses) {
        mockHttpServerResponses.clear();
        for (int i = 0; i < responses.length; i++) {
            mockHttpServerResponses.put(responses[i].getResponseUrl(), responses[i]);
        }
    }

    public void updateMockHttpServerResponse(SimpleMockResponse response) {
        mockHttpServerResponses.put(response.getResponseUrl(), response);
    }

    public void clearResponses() {
        mockHttpServerResponses.clear();
    }


    public Map<String, SimpleMockResponse> getMockHttpServerResponses() {
        return mockHttpServerResponses;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getNrOfRequests() {
        return nrOfRequests.get();
    }
}
