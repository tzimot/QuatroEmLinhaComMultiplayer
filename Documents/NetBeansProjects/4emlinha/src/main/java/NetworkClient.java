import java.io.*;
import java.net.*;

public class NetworkClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String name;

    public NetworkClient(String ip, int port, String name) throws IOException {
        this.socket = new Socket(ip, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.name = name;
        out.println(name);
    }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    public String readMessage() throws IOException {
        if (in != null) {
            return in.readLine();
        }
        return null;
    }

    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
