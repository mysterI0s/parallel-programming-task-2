import com.sun.net.httpserver.HttpServer;
import handler.RequestsHandlerHttp;
import util.PoolFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class WebServerHttp {

    private static final int PORT = 8000;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new RequestsHandlerHttp());

        server.setExecutor(PoolFactory.fromEnvironment());

        server.start();
        System.out.println("WebServerHttp is listening on port " + PORT);
    }
}
