import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class P2PChatUI {

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JTextField peerField;
    private DefaultListModel<String> peerListModel;
    private JList<String> peerList;

    private String host;
    private int port;

    private Set<String> peers = ConcurrentHashMap.newKeySet();

    public P2PChatUI(String host, int port) {
        this.host = host;
        this.port = port;
        createUI();
        startServer();
    }

    // ---------------- UI ----------------
    private void createUI() {
        frame = new JFrame("P2P Chat - " + host + ":" + port);
        frame.setSize(700, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Left: chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setPreferredSize(new Dimension(460, 0));
        frame.add(chatScroll, BorderLayout.CENTER);

        // Right: peers list
        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(220, 0));
        peerListModel = new DefaultListModel<>();
        peerList = new JList<>(peerListModel);
        JScrollPane peerScroll = new JScrollPane(peerList);
        right.add(new JLabel("Peers"), BorderLayout.NORTH);
        right.add(peerScroll, BorderLayout.CENTER);

        JButton btnRemove = new JButton("Remove Selected");
        btnRemove.addActionListener(e -> removeSelectedPeer());
        right.add(btnRemove, BorderLayout.SOUTH);

        frame.add(right, BorderLayout.EAST);

        // Top panel: add peer
        JPanel top = new JPanel(new BorderLayout(8, 8));
        peerField = new JTextField();
        JButton btnAdd = new JButton("Add Peer (ip:port)");
        btnAdd.addActionListener(e -> addPeer());
        top.add(peerField, BorderLayout.CENTER);
        top.add(btnAdd, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(top, BorderLayout.NORTH);

        // Bottom panel: message + send
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        messageField = new JTextField();
        JButton btnSend = new JButton("Send");
        btnSend.addActionListener(e -> sendMessage());
        bottom.add(messageField, BorderLayout.CENTER);
        bottom.add(btnSend, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(bottom, BorderLayout.SOUTH);

        // keyboard Enter to send
        messageField.addActionListener(e -> sendMessage());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        appendSystem("UI ready. Host: " + host + " Port: " + port);
    }

    private void appendSystem(String s) {
        chatArea.append("[System] " + s + "\n");
    }

    // ---------------- Peers ----------------
    private void addPeer() {
        String peer = peerField.getText().trim();
        if (peer.isEmpty()) return;
        if (!peer.matches("^[^:]+:\\d+$")) {
            appendSystem("Invalid peer format. Use ip:port");
            return;
        }
        peers.add(peer);
        if (!peerListModel.contains(peer)) peerListModel.addElement(peer);
        peerField.setText("");
        appendSystem("Added peer: " + peer);
    }

    private void removeSelectedPeer() {
        String sel = peerList.getSelectedValue();
        if (sel != null) {
            peers.remove(sel);
            peerListModel.removeElement(sel);
            appendSystem("Removed peer: " + sel);
        }
    }

    // ---------------- Network / Send ----------------
    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty()) return;
        String composed = "[" + host + ":" + port + "] " + msg;
        broadcast(composed);
        chatArea.append("You: " + msg + "\n");
        messageField.setText("");
    }

    private void broadcast(String msg) {
        if (peers.isEmpty()) {
            appendSystem("No peers to send to.");
            return;
        }
        for (String peer : peers) {
            new Thread(() -> {
                String[] p = peer.split(":");
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(p[0], Integer.parseInt(p[1])), 3000);
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    writer.println(msg);
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                        appendSystem("Failed to send to " + peer + " (" + e.getMessage() + ")")
                    );
                }
            }).start();
        }
    }

    // ---------------- Server ----------------
    private void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                appendSystem("Server listening on " + host + ":" + port);
                while (true) {
                    Socket socket = server.accept();
                    new Thread(() -> handleIncoming(socket)).start();
                }
            } catch (IOException e) {
                appendSystem("Server error: " + e.getMessage());
            }
        }).start();
    }

    private void handleIncoming(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String msg = reader.readLine();
            if (msg != null) {
                SwingUtilities.invokeLater(() -> chatArea.append("📥 " + msg + "\n"));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> appendSystem("Error receiving message: " + e.getMessage()));
        }
    }

    // ---------------- Main ----------------
    public static void main(String[] args) {
        String host = JOptionPane.showInputDialog(null, "Enter your host (e.g. 127.0.0.1):");
        String portStr = JOptionPane.showInputDialog(null, "Enter port (e.g. 5000):");
        if (host == null || portStr == null) return;
        int port;
        try {
            port = Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Invalid port.");
            return;
        }
        SwingUtilities.invokeLater(() -> new P2PChatUI(host.trim(), port));
    }
}
