package net.mrkzea.mockserver;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;


public class SimpleHttpClient {


    public String httpCall(String server, String path) {
        return httpGet(server + path);
    }


    public static String httpGet(String url) {
        StringBuffer buff = new StringBuffer();
        try {
            URL server = new URL(url);
            URLConnection sc = server.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            sc.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                buff.append(inputLine);
            in.close();
        } catch (MalformedURLException e) {
            throw new RuntimeException("Mallformed URL", e);
        } catch (IOException e) {
            throw new RuntimeException("Connection problems", e);
        }
        return buff.toString();
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


}
