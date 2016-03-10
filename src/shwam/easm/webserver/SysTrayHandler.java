package shwam.easm.webserver;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import org.java_websocket.WebSocket;
import shwam.easm.webserver.stomp.StompConnectionHandler;

public class SysTrayHandler
{
    private static TrayIcon trayIcon;

    public static void initSysTray()
    {
        if (SystemTray.isSupported())
        {
            final PopupMenu pm = new PopupMenu();
            final MenuItem exit = new MenuItem("Exit");
            final MenuItem status = new MenuItem("Status");
            final MenuItem data = new MenuItem("View Data");
            final MenuItem motd = new MenuItem("Edit MOTD");
            final Menu     logs = new Menu("Logs");
            final MenuItem logsFile = new Menu("Open Todays log");
            final MenuItem logsFolder = new Menu("Open log folder");
            final MenuItem reconnect = new MenuItem("Reconnect");

            ActionListener menuListener = evt ->
            {
                Object src = evt.getSource();
                if (src == exit)
                {
                    StompConnectionHandler.disconnect();
                    StompConnectionHandler.printStomp("Disconnected", false);

                    System.exit(0);
                }
                else if (src == status)
                {
                    Collection<WebSocket> conns = Collections.unmodifiableCollection(WebServer.webSocket.connections());
                    StringBuilder statusMsg = new StringBuilder();
                    statusMsg.append("WebSocket:");
                    statusMsg.append("\n  Connections: ").append(conns.size());
                    conns.stream().forEachOrdered(c -> statusMsg.append("\n    ").append(c.getRemoteSocketAddress().getAddress().getHostAddress()).append(":").append(c.getRemoteSocketAddress().getPort()));
                    statusMsg.append("\nStomp:");
                    statusMsg.append("\n  Connected: ").append(StompConnectionHandler.isConnected() && !StompConnectionHandler.isClosed() ? "Yes" : "No");
                    statusMsg.append("\n  Timeout: ").append((System.currentTimeMillis() - StompConnectionHandler.lastMessageTimeGeneral)/1000f).append("s");

                    WebServer.printOut(statusMsg.toString().replace("\n", "\n[Status]"));

                    JOptionPane.showMessageDialog(null, statusMsg.toString());
                }
                else if (src == data)
                    WebServer.guiData.setVisible(true);
                else if (src == motd)
                {
                    String newMOTD = (String) JOptionPane.showInputDialog(null, "Input a new MOTD", "Edit MOTD", JOptionPane.QUESTION_MESSAGE, null, null, WebServer.TDData.getOrDefault("XXMOTD", ""));

                    if (newMOTD != null)
                    {
                        newMOTD = newMOTD.replaceAll("%date%", WebServer.sdfDate.format(new Date()));
                        newMOTD = newMOTD.replaceAll("%time%", WebServer.sdfTime.format(new Date()));
                        newMOTD = newMOTD.trim();

                        WebServer.TDData.put("XXMOTD", newMOTD);

                        try
                        {
                            File motdFile = new File(WebServer.storageDir, "MOTD.txt");
                            if (!motdFile.exists())
                            {
                                motdFile.getParentFile().mkdirs();
                                motdFile.createNewFile();
                            }

                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(motdFile)))
                            {
                                bw.write(newMOTD);
                                bw.write("\r\n");
                            }
                        }
                        catch (IOException e) { WebServer.printThrowable(e, "MOTD"); }
                    }

                    String update = new StringBuilder("{\"Message\":{")
                        .append("\"type\":\"").append(MessageType.SEND_UPDATE.getName()).append("\",")
                        .append("\"message\":{")
                        .append("\"XXMOTD\":\"").append(newMOTD).append("\"")
                        .append("}}}")
                    .toString();

                    Collections.unmodifiableCollection(WebServer.webSocket.connections()).stream().forEach(c -> c.send(update));
                }
                else if (src == logsFile)
                {
                    try { Desktop.getDesktop().open(WebServer.logFile); }
                    catch (IOException e) {}
                }
                else if (src == logsFolder)
                {
                    try { Runtime.getRuntime().exec("explorer.exe /select," + WebServer.logFile); }
                    catch (IOException e) {}
                }
                else if (src == reconnect)
                {
                    StompConnectionHandler.disconnect();
                    StompConnectionHandler.wrappedConnect();
                }
            };

            status.addActionListener(menuListener);
            data.addActionListener(menuListener);
            motd.addActionListener(menuListener);
            reconnect.addActionListener(menuListener);
            logsFile.addActionListener(menuListener);
            logsFolder.addActionListener(menuListener);
            exit.addActionListener(menuListener);

            logs.add(logsFile);
            logs.add(logsFolder);

            pm.add(status);
            pm.add(data);
            pm.add(motd);
            pm.add(reconnect);
            pm.add(logs);
            pm.addSeparator();
            pm.add(exit);

            try
            {
                trayIcon = new TrayIcon(ImageIO.read(SysTrayHandler.class.getResource("/shwam/easm/webserver/resources/Icon.png")));
                trayIcon.setToolTip("EASM Web Server (v" + WebServer.VERSION + ")");
                trayIcon.setImageAutoSize(true);
                trayIcon.setPopupMenu(pm);
                SystemTray.getSystemTray().add(trayIcon);
            }
            catch (IOException | AWTException e) {}
        }
    }
}