import java.io.*;
import java.net.*;
import java.util.concurrent.CompletableFuture;


class Scratch {

    static final String DATA_STRING = "data string";

    public static void main(String[] args) throws Exception {
        try (Server s = new Server();
             SocketWriter cw = new SocketWriter(s.ia, s.port)) {
            CompletableFuture<?> s_fut = CompletableFuture.runAsync(s);
            CompletableFuture<?> cw_fut = CompletableFuture.runAsync(cw);
            CompletableFuture<?> s_cw_future = CompletableFuture.allOf(s_fut, cw_fut);
            s_cw_future.join();
        }
    }

    public static class SocketWriter implements Runnable, AutoCloseable {
        Socket s;
        OutputStream os;

        SocketWriter(InetAddress remote, int port) throws IOException {
            s = new Socket(remote, port);
        }

        @Override
        public void run() {
            try {
                os = s.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            PrintStream out = new PrintStream(os);
            out.println("Client: " + DATA_STRING);
        }

        @Override
        public void close() throws IOException {
            s.close();
        }
    }

    public static class Server implements Runnable, AutoCloseable {

        ServerSocket ss;
        Socket cs;
        BufferedReader is;
        PrintStream os;
        InetAddress ia;
        int port;

        Server() throws Exception {
            ss = new ServerSocket(0);
            ia = ss.getInetAddress();
            port = ss.getLocalPort();
        }

        @Override
        public void run() {
            try {
                cs = ss.accept();
                is = new BufferedReader(new InputStreamReader(cs.getInputStream()));
                os = new PrintStream(cs.getOutputStream());

                is.read();
                os.println("Server: " + DATA_STRING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void close() throws IOException {
            ss.close();
        }
    }
}