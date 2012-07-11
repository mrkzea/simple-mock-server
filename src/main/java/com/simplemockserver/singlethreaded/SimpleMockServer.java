package com.simplemockserver.singlethreaded;

import javax.net.ServerSocketFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class SimpleMockServer extends Thread {

    private ServerSocket serverSocket;

    private Map<String, MockResponse> responses;

    private long responseDelay;

    /*
    *  Used to construct new MockServer instance,
    *  A mapping between url and expected response must be passed as responses map.
    */
    public SimpleMockServer(int port, Map<String, MockResponse> responses, long responseDelay){
        if(responses != null && responses.size() > 0){
            this.responses = responses;
        }else{
            //TO-DO: implement searching for mapping on classpath
            throw new RuntimeException("Must pass response mappings.. ");
        }
        this.responseDelay = responseDelay;
        ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
        try {
            serverSocket = serverSocketFactory.createServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException("Could not construct server", e);
        }
        start();
    }

    public SimpleMockServer(int port, Map<String, MockResponse> responses){
        this(port, responses, 0);
    }

    public SimpleMockServer(int port){
        this(port, null, 0);
    }

    public SimpleMockServer(int port, long responseDelay){
        this(port, null, responseDelay);
    }



    static class MockResponse{
        private String content;
        public void setContent(String content) { this.content = content; }
        public String getContent() { return content; }

        private String mockUrl;
        public String getMockUrl() { return mockUrl; }
        public void setMockUrl(String mockUrl) { this.mockUrl = mockUrl; }

        //TO-DO: contentType, responseHeaders, responseHeaders

    }


    public void run(){

        while(true){
            Socket socket=null;
            try {
                socket = serverSocket.accept();
                if (responseDelay > 0) sleep(responseDelay);

                //TO-DO: add thread pool and executor
                process(socket);

            } catch (IOException e) {
                throw new RuntimeException("Could not open socket", e);
            } catch (InterruptedException e) {
                e.printStackTrace();
                interrupt();
            } finally {
                try {
                    socket.shutdownOutput();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private void process(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String line = reader.readLine();
        String method = line.split(" ")[0];
        String url = line.split(" ")[1];
        if(!method.equals("GET")) throw new RuntimeException("Not supported HTTP METHOD: " + method);
        //TO-DO: process request headers

        if(responses.containsKey(url)){
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //TO-DO: process response headers
            out.write(responses.get(url).getContent());
            out.flush();
        }
    }
}
