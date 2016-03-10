package shwam.easm.webserver;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.java_websocket.server.WebSocketServer;
import shwam.easm.webserver.stomp.StompConnectionHandler;

public class WebServer
{
    public static final String VERSION = "1";

    public static final File storageDir = new File(System.getProperty("user.home", "C:") + File.separator + ".easigmap");

    public  static final int    port = 6322;
    public  static WebSocketServer webSocket;
    public  static DataGui      guiData;
    public  static boolean      stop = true;

    public static SimpleDateFormat sdfTime          = new SimpleDateFormat("HH:mm:ss");
    public static SimpleDateFormat sdfDate          = new SimpleDateFormat("dd/MM/yy");
    public static SimpleDateFormat sdfDateTime      = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
    public static SimpleDateFormat sdfDateTimeShort = new SimpleDateFormat("dd/MM HH:mm:ss");

    public  static File         logFile;
    private static PrintStream  logStream;
    private static String       lastLogDate = "";

    public  static Map<String, String> TDData = new HashMap<>();

    public static void main(String[] args)
    {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) { printThrowable(e, "Look & Feel"); }

        Date logDate = new Date();
        logFile = new File(storageDir, "Logs" + File.separator + "WebServer" + File.separator + sdfDate.format(logDate).replace("/", "-") + ".log");
        logFile.getParentFile().mkdirs();
        lastLogDate = sdfDate.format(logDate);

        try { logStream = new PrintStream(new FileOutputStream(logFile, logFile.length() > 0), true); }
        catch (FileNotFoundException e) { printErr("Could not create log file"); printThrowable(e, "Startup"); }

        try { EventQueue.invokeAndWait(() -> guiData = new DataGui()); }
        catch (InvocationTargetException | InterruptedException e) { printThrowable(e, "Startup"); }

        SysTrayHandler.initSysTray();

        if (StompConnectionHandler.wrappedConnect())
            printOut("[Stomp] Started");
        else
            printErr("[Stomp] Unble to start Stomp");

        webSocket = new EASMWebSocket(port);
        webSocket.start();

        Timer FullUpdateMessenger = new Timer("FullUpdateMessenger");
        FullUpdateMessenger.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                try
                {
                    Map<String, Object> Message = new HashMap<>();
                    Map<String, Object> content = new HashMap<>();
                    content.put("type", MessageType.SEND_UPDATE.getName());
                    content.put("timestamp", Long.toString(System.currentTimeMillis()));
                    Message.put("Message", content);
                    StringBuilder sb = new StringBuilder("{\"Message\":{");
                    sb.append("\"type\":\"").append(MessageType.SEND_UPDATE.getName()).append("\",");
                    sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
                    sb.append("\"message\":{");

                    TDData.entrySet().stream()
                            .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));

                    if (sb.charAt(sb.length()-1) == ',')
                        sb.deleteCharAt(sb.length()-1);
                    sb.append("}}}");

                    Collections.unmodifiableCollection(webSocket.connections()).stream().forEach(c -> c.send(sb.toString()));
                    printOut("[WebSocket] Updated all clients");
                }
                catch (Exception e) { printThrowable(e, "SendAll"); }
            }
        }, 500, 1000*60);

        //Timer compatWebClientUpdater = new Timer("compatWebClientUpdater");
        //compatWebClientUpdater.scheduleAtFixedRate(new TimerTask()
        //{
        //    @Override
        //    public void run()
        //    {
        //        StringBuilder sb = new StringBuilder("{\"Message\":{");
        //        sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",\n");
        //        sb.append("\"message\":{\n");

        //        TDData.entrySet().stream()
        //                .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\",\n"));

        //        if (sb.charAt(sb.length()-2) == ',')
        //            sb.deleteCharAt(sb.length()-2);
        //        sb.append("}}}");
        //        sb.trimToSize();

        //        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new URL(ftpBaseUrl + "webclient/data/message.json").openConnection().getOutputStream()), 8192))
        //        {
        //            bw.write(sb.toString());
        //            bw.newLine();
        //        }
        //        catch (Exception ex) { printThrowable(ex, "StaticMapUpdater"); }
        //        printOut("Uploaded compat file");
        //    }
        //}, 500, 10*1000);
    }

    public static void printThrowable(Throwable t, String name)
    {
        printErr((name != null && !name.isEmpty() ? "[" + name + "] " : "") + t.toString());

        for (StackTraceElement element : t.getStackTrace())
            printErr((name != null && !name.isEmpty() ? "[" + name + "] -> " : "-> ") + element.toString());

        for (Throwable sup : t.getSuppressed())
            printThrowable0(sup, name);

        printThrowable0(t.getCause(), name);
    }

    private static void printThrowable0(Throwable t, String name)
    {
        if (t != null)
        {
            printErr((name != null && !name.isEmpty() ? "[" + name + "] " : "") + t.toString());

            for (StackTraceElement element : t.getStackTrace())
                printErr((name != null && !name.isEmpty() ? "[" + name + "] -> " : " -> ") + element.toString());
        }
    }

    public static void printOut(String message)
    {
        if (message != null && !message.equals(""))
            if (!message.contains("\n"))
                print("[" + sdfDateTime.format(new Date()) + "] " + message, false);
            else
                for (String msgPart : message.trim().split("\n"))
                    print("[" + sdfDateTime.format(new Date()) + "] " + msgPart, false);
    }

    public static void printErr(String message)
    {
        if (message != null && !message.equals(""))
            if (!message.contains("\n"))
                print("[" + sdfDateTime.format(new Date()) + "] !!!> " + message + " <!!!", false);
            else
                for (String msgPart : message.trim().split("\n"))
                    print("[" + sdfDateTime.format(new Date()) + "] !!!> " + msgPart + " <!!!", true);
    }

    private static synchronized void print(String message, boolean toErr)
    {
        if (toErr)
            System.err.println(message);
        else
            System.out.println(message);

        filePrint(message);
    }
    private static synchronized void filePrint(String message)
    {
        Date logDate = new Date();
        if (!lastLogDate.equals(sdfDate.format(logDate)))
        {
            logStream.flush();
            logStream.close();

            lastLogDate = sdfDate.format(logDate);

            logFile = new File(storageDir, "Logs" + File.separator + "WebServer" + File.separator + lastLogDate.replace("/", "-") + ".log");
            logFile.getParentFile().mkdirs();

            try
            {
                logFile.createNewFile();
                logStream = new PrintStream(new FileOutputStream(logFile, true), true);
            }
            catch (IOException e) { printErr("Could not create log file"); printThrowable(e, "Logging"); }
        }

        logStream.println(message);
    }
}