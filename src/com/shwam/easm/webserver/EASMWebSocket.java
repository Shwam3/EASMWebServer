package com.shwam.easm.webserver;

import java.net.InetSocketAddress;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class EASMWebSocket extends WebSocketServer
{
    public EASMWebSocket(int port) { super(new InetSocketAddress(port)); }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake)
    {
        printWebSocket("Open connection to " + conn.getRemoteSocketAddress().getAddress().getHostAddress() + ":" + conn.getRemoteSocketAddress().getPort(), false);

        StringBuilder sb = new StringBuilder("{\"Message\":{");
        sb.append("\"type\":\"").append(MessageType.SEND_ALL.getName()).append("\",");
        sb.append("\"message\":{");

        WebServer.TDData.entrySet().stream()
                .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));

        if (sb.charAt(sb.length()-1) == ',')
            sb.deleteCharAt(sb.length()-1);
        sb.append("}}}");

        conn.send(sb.toString());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote)
    {
        printWebSocket(
            String.format("Close connection to %s (%s%s)",
                conn.getRemoteSocketAddress().getAddress().getHostAddress(),
                code,
                reason != null && !reason.isEmpty() ? "/" + reason : ""
            ),
        false);
    }

    @Override
    public void onMessage(WebSocket conn, String message)
    {
        printWebSocket("Message (" + conn.getRemoteSocketAddress().getAddress().getHostAddress() + "):\n  " + message, false);
    }

    @Override
    public void onError(WebSocket conn, Exception ex)
    {
        if (conn != null)
        {
            printWebSocket("Error (" + conn.getRemoteSocketAddress().getAddress().getHostAddress() + "):", true);
            conn.close(CloseFrame.ABNORMAL_CLOSE, ex.getMessage());
        }
        WebServer.printThrowable(ex, "WebSocket" + (conn != null ? "-" + conn.getRemoteSocketAddress().getAddress().getHostAddress() : ""));
    }

    public static void printWebSocket(String message, boolean toErr)
    {
        if (toErr)
            WebServer.printErr("[WebSocket] " + message);
        else
            WebServer.printOut("[WebSocket] " + message);
    }
}