import java.io.*;
import java.net.*;

public class NetworkClient {
    private final Socket socket;
    private final PrintWriter out; // enviar mensagens ao servidor
    private final BufferedReader in; // receber mensagens do servidor
    private final String name; 
    
    // Estabelece a ligação com o servidor
    public NetworkClient(String ip, int port, String name) throws IOException {
        this.socket = new Socket(ip, port);  // Conecta-se ao servidor no IP e porta fornecidos
        this.out = new PrintWriter(socket.getOutputStream(), true); 
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream())); 
        this.name = name; 
        out.println(name); 
    }

    // Envia uma mensagem ao servidor
    public void sendMessage(String msg) {
        out.println(msg);
    }

    // Lê uma mensagem do servidor
    public String readMessage() throws IOException {
        return in.readLine();
    }

    // Fecha a conexão com o servidor
    public void close() throws IOException {
        try { in.close(); } catch (IOException ignored) {}
        out.close();
        socket.close();
    }

    // Verifica se o cliente está conectado ao servidor
    public boolean isConnected() {
        return socket.isConnected() && !socket.isClosed();
    }
}
