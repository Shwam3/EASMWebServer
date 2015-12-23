package com.shwam.easm.webserver.stomp;

import com.shwam.easm.webserver.WebServer;
import com.shwam.easm.webserver.stomp.handlers.TDHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.security.auth.login.LoginException;
import net.ser1.stomp.Listener;
import net.ser1.stomp.Version;

public class StompConnectionHandler
{
    private static StompClient client;

    private static ScheduledExecutorService executor = null;
    private static int    maxTimeoutWait = 300;
    private static int    timeoutWait = 10;
    private static int    wait = 0;
    public  static long   lastMessageTimeGeneral = System.currentTimeMillis();
    private static String appID = "";
    private static int    stompConnectionId = 1;

    private static final Listener handlerTD = TDHandler.getInstance();

    public static boolean connect() throws LoginException, IOException
    {
        printStomp(Version.VERSION, false);

        String username;
        String password;

        File loginFile = new File(WebServer.storageDir, "NROD_Login.properties");
        try (FileInputStream in = new FileInputStream(loginFile))
        {
            Properties loginProps = new Properties();
            loginProps.load(in);

            username = loginProps.getProperty("Username", "");
            password = loginProps.getProperty("Password", "");
        }
        catch (FileNotFoundException e)
        {
            printStomp("Unable to find login properties file (" + loginFile + ")", true);
            return false;
        }

        appID = username + "-WebServer-v" + WebServer.VERSION + "-";

        if ((username != null && username.equals("")) || (password != null && password.equals("")))
        {
            printStomp("Error retreiving login details (usr: " + username + ", pwd: " + password + ")", true);
            return false;
        }

        startTimeoutTimer();
        client = new StompClient("datafeeds.networkrail.co.uk", 61618, username, password, appID + stompConnectionId);

        if (client.isConnected())
        {
            printStomp("Connected to \"datafeeds.networkrail.co.uk:61618\"", false);
            printStomp("  ID:       " + appID + stompConnectionId, false);
            printStomp("  Username: " + username, false);
            printStomp("  Password: " + password, false);
        }
        else
        {
            printStomp("Could not connect to network rail's servers", true);
            return false;
        }

        client.addErrorListener((headers, body) ->
        {
            printStomp(headers.get("message").trim(), true);

            if (body != null && !body.isEmpty())
                printStomp(body.trim().replace("\n", "\n[Stomp]"), true);
        });

        client.subscribe("/topic/TD_ANG_SIG_AREA", "TD", handlerTD);

        try { Thread.sleep(100); }
        catch (InterruptedException e) {}

        return true;
    }

    public static void disconnect()
    {
        if (client != null && isConnected() && !isClosed())
            client.disconnect();
    }

    public static boolean isConnected()
    {
        if (client == null)
            return false;

        return client.isConnected();
    }

    public static boolean isClosed()
    {
        if (client == null)
            return false;

        return client.isClosed();
    }

    public static boolean isTimedOut()
    {
        long timeout = System.currentTimeMillis() - lastMessageTimeGeneral;

        return timeout >= getTimeoutThreshold() && getTimeoutThreshold() > 0;
    }

    private static long getTimeoutThreshold()
    {
        return 30000;
    }

    public static boolean wrappedConnect()
    {
        try
        {
            return connect();
        }
        catch (LoginException e)       { printStomp("Login Exception: " + e.getLocalizedMessage().split("\n")[0], true); }
        catch (UnknownHostException e) { printStomp("Unable to resolve host (datafeeds.networkrail.co.uk)", true); }
        catch (IOException e)          { printStomp("IO Exception:", true); WebServer.printThrowable(e, "Stomp"); }
        catch (Exception e)            { printStomp("Exception:", true); WebServer.printThrowable(e, "Stomp"); }

        return false;
    }

    private static void startTimeoutTimer()
    {
        if (executor != null)
        {
            executor.shutdown();

            try { executor.awaitTermination(2, TimeUnit.SECONDS); }
            catch(InterruptedException e) {}
        }

        executor = Executors.newScheduledThreadPool(1);

        // General timeout
        executor.scheduleWithFixedDelay(() ->
        {
            if (wait >= timeoutWait)
            {
                wait = 0;

                long time = System.currentTimeMillis() - lastMessageTimeGeneral;

                printStomp(String.format("Timeout: %02d:%02d:%02d (Threshold: %ss)", (time / (1000 * 60 * 60)) % 24, (time / (1000 * 60)) % 60, (time / 1000) % 60, (getTimeoutThreshold() / 1000)), isTimedOut() || !isConnected() || isClosed());

                if (isTimedOut() || !isConnected())
                {
                    timeoutWait = Math.min(maxTimeoutWait, timeoutWait + 10);

                    printStomp((isTimedOut() ? "Timed Out" : "") + (isTimedOut() && isClosed() ? ", " : "") + (isClosed() ? "Closed" : "") + ((isTimedOut() || isClosed()) && !isConnected() ? " & " : "") + (!isConnected() ? "Disconnected" : "") + " (" + timeoutWait + "s)", true);

                    try
                    {
                        if (client != null)
                            disconnect();

                        connect();
                    }
                    catch (LoginException e) { printStomp("Login Exception: " + e.getLocalizedMessage().split("\n")[0], true);}
                    catch (IOException e)    { printStomp("IO Exception reconnecting", true); WebServer.printThrowable(e, "Stomp"); }
                    catch (Exception e)      { printStomp("Exception reconnecting", true);  WebServer.printThrowable(e, "Stomp"); }
                }
            }
            else
                wait += 10;
        }, 10, 10, TimeUnit.SECONDS);
    }

    public static void setMaxTimeoutWait(int maxTimeoutWait)
    {
        StompConnectionHandler.maxTimeoutWait = Math.max(600, maxTimeoutWait);
    }

    public static void printStomp(String message, boolean toErr)
    {
        if (toErr)
            WebServer.printErr("[Stomp] " + message);
        else
            WebServer.printOut("[Stomp] " + message);
    }

    public static String getConnectionName() { return appID + stompConnectionId; }
    //public static int incrementConnectionId() { return ++stompConnectionId; }

    public static void ack(String ackId)
    {
        if (client != null)
            client.ack(ackId);
    }

    public static void printStompHeaders(Map<String, String> headers)
    {
        printStomp(
                String.format("Message received (topic: %s, time: %s, expires: %s, id: %s, ack: %s, subscription: %s, persistent: %s%s)",
                        String.valueOf(headers.get("destination")).replace("\\c", ":"),
                        WebServer.sdfTime.format(new Date(Long.parseLong(headers.get("timestamp")))),
                        WebServer.sdfTime.format(new Date(Long.parseLong(headers.get("expires")))),
                        String.valueOf(headers.get("message-id")).replace("\\c", ":"),
                        String.valueOf(headers.get("ack")).replace("\\c", ":"),
                        String.valueOf(headers.get("subscription")).replace("\\c", ":"),
                        String.valueOf(headers.get("persistent")).replace("\\c", ":"),
                        headers.size() > 7 ? ", + " + (headers.size()-7) + " more" : ""
                ), false);
    }
}