package handler;

import singleton.TaskExecutor;
import singleton.TasksList;
import util.Log;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RequestsHandlerSocket implements Runnable {

    private Socket socket;

    public RequestsHandlerSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            handle();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handle() throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        InputStream is = socket.getInputStream();
        InputStreamReader isReader = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isReader);

        String response = "";
        String requestType = getRequestType(br);
        switch (requestType) {
            case "GET":
                response = TasksList.INSTANCE.list();
                break;
            case "POST":
                String params = getParameters(br);
                Log.info("New task requested -> " + params);
                response = TaskExecutor.INSTANCE.run(params);
                break;
        }

        // A status line and headers make this a real HTTP response; without them
        // current HTTP clients (curl, browsers) reject the reply as HTTP/0.9.
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();

        out.close();
        br.close();
        socket.close();
    }

    /**
     * Get the type of the request: GET, POST and etc.
     * @param in
     * @return String
     * @throws IOException
     */
    private String getRequestType(BufferedReader in) throws IOException {
        String request = in.readLine();
        request = request.substring(0, request.indexOf(" ")).trim();
        return request;
    }

    /**
     * Get the POST parameters to be used to create the tasks
     * @param in
     * @return String
     * @throws IOException
     */
    private String getParameters(BufferedReader in) throws IOException {
        StringBuilder values = new StringBuilder();
        while(in.ready()){
            values.append((char) in.read());
        }
        List<String> list = Arrays.asList(values.toString().split("\r"));
        String result = list.get(list.size()-1).trim();
        return result;
    }
}
