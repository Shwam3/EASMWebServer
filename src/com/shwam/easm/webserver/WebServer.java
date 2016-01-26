package com.shwam.easm.webserver;

import com.shwam.easm.webserver.stomp.StompConnectionHandler;
import java.awt.EventQueue;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.java_websocket.server.WebSocketServer;

public class WebServer
{
    public static final String VERSION = "1";

    public static final File storageDir = new File(System.getProperty("user.home", "C:") + File.separator + ".easigmap");
    public static String ftpBaseUrl = "";

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

        try
        {
            File ftpLoginFile = new File(storageDir, "Website_FTP_Login.properties");
            if (!ftpLoginFile.exists())
            {
                ftpLoginFile.getParentFile().mkdirs();
                ftpLoginFile.createNewFile();
            }

            Properties ftpLogin = new Properties();
            ftpLogin.load(new FileInputStream(ftpLoginFile));

            ftpBaseUrl = "ftp://" + ftpLogin.getProperty("Username", "") + ":" + ftpLogin.getProperty("Password", "") + "@ftp.easignalmap.altervista.org/";
        }
        catch (FileNotFoundException e) {}
        catch (IOException e) { printThrowable(e, "FTP Login"); }

        try { EventQueue.invokeAndWait(() -> guiData = new DataGui()); }
        catch (InvocationTargetException | InterruptedException e) { printThrowable(e, "Startup"); }

        SysTrayHandler.initSysTray();

        if (StompConnectionHandler.wrappedConnect())
            printOut("[Stomp] Started");
        else
            printErr("[Stomp] Unble to start Stomp");

        webSocket = new EASMWebSocket(port);
        webSocket.start();

        Timer infrequentTasks = new Timer("infrequentTasks");
        infrequentTasks.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                //try { updateIP(false); }
                //catch (Exception e) { printThrowable(e, "IPUpdater"); }

                try
                {
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
                    printOut("[TD] Updated all clients");
                }
                catch (Exception e) { printThrowable(e, "SendAll"); }
            }
        }, 500, 1000*60*5);

        Timer compatWebClientUpdater = new Timer("compatWebClientUpdater");
        compatWebClientUpdater.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                //try
                //{
                //    if (!InetAddress.getByName("easignalmap.altervista.org").isReachable(2000))
                //    {
                //        printErr("[StaticMapUpdater] \"http://easignalmap.altervista.org/\" is unreachable");
                //        return;
                //    }
                //}
                //catch (IOException e) { printThrowable(e, "StaticMapUpdater"); return; }

                StringBuilder sb = new StringBuilder("{\"Message\":{");
                sb.append("\"timestamp\":").append(System.currentTimeMillis()).append(",\n");
                sb.append("\"message\":{\n");

                TDData.entrySet().stream()
                        .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\",\n"));

                if (sb.charAt(sb.length()-2) == ',')
                    sb.deleteCharAt(sb.length()-2);
                sb.append("}}}");
                sb.trimToSize();

                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new URL(ftpBaseUrl + "webclient/data/message.json").openConnection().getOutputStream()), 8192))
                {
                    bw.write(sb.toString());
                    bw.newLine();
                }
                catch (IOException ex) { printThrowable(ex, "StaticMapUpdater"); }
            }
        }, 500, 30*1000);

    }

//    public static void updateIP(boolean force)
//    {
//        if (!force)
//        {
//            try
//            {
//                if (!InetAddress.getByName("easignalmap.altervista.org").isReachable(2000))
//                {
//                    printErr("[IPUpdater] \"http://easignalmap.altervista.org/\" is unavailable via ping");
//                    return;
//                }
//            }
//            catch (IOException e)
//            {
//                printErr("[IPUpdater] IOException: " + e.toString());
//                return;
//            }
//        }
//
//        String externalIP = "127.0.0.1";
//        try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL("http://checkip.amazonaws.com/").openStream())))
//        {
//            externalIP = br.readLine();
//        }
//        catch (ConnectException e) { printErr("[IPUpdater] Unable to connect to amazomaws"); }
//        catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
//        catch (IOException e) { printThrowable(e, "IPUpdater"); }
//
//        String externalIPOld = "";
//        if (!force)
//        {
//            try (BufferedReader br = new BufferedReader(new InputStreamReader(new URL(ftpBaseUrl + "server.ip;type=i").openStream())))
//            {
//                externalIPOld = br.readLine();
//            }
//            catch (ConnectException e) { printErr("[IPUpdater] Unable to connect to altervista"); }
//            catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
//            catch (IOException e) { printThrowable(e, "IPUpdater"); }
//        }
//
//        if (!externalIP.equals(externalIPOld) || force)
//        {
//            try
//            {
//                URLConnection con = new URL(ftpBaseUrl + "server.ip;type=i").openConnection();
//                con.setConnectTimeout(10000);
//                con.setReadTimeout(10000);
//                try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(con.getOutputStream())))
//                {
//                    bw.write(externalIP);
//
//                    printOut("[IPUpdater] Updated server IP to " + externalIP);
//                }
//                catch (ConnectException e) { printErr("[IPUpdater] Unable to connect to FTP server"); }
//                catch (SocketTimeoutException e) { printErr("[IPUpdater] Socket timeout"); }
//                catch (IOException e) { printThrowable(e, "IPUpdater"); }
//            }
//            catch (IOException e) { printThrowable(e, "IPUpdater"); }
//        }
//        else
//        {
//            printOut("[IPUpdater] Not updating IP (" + externalIP + ")");
//        }
//    }

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