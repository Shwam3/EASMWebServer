package shwam.easm.webserver.stomp.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.ser1.stomp.Listener;
import org.json.JSONArray;
import org.json.JSONObject;
import shwam.easm.webserver.MessageType;
import shwam.easm.webserver.WebServer;
import shwam.easm.webserver.stomp.StompConnectionHandler;

public class TDHandler implements Listener
{
    private static Listener instance = null;
    private TDHandler()
    {
        File TDDataFile = new File(WebServer.storageDir, "Logs" + File.separator + "TD" + File.separator + "TDData.json");
        if (TDDataFile.exists())
        {
            String jsonString = "";
            try (BufferedReader br = new BufferedReader(new FileReader(TDDataFile)))
            {
                String line;
                while ((line = br.readLine()) != null)
                    jsonString += line;
            }
            catch (IOException e) { WebServer.printThrowable(e, "TD"); }

            try
            {
                JSONObject json = new JSONObject(jsonString).getJSONObject("TDData");
                
                for (String key : json.keySet())
                    if (key.length() == 6)
                        WebServer.TDData.put(key, json.getString(key));

                if (WebServer.TDData.size() > 0)
                    WebServer.printOut("[TD] Initialised Data");
            }
            catch (IllegalArgumentException e) { WebServer.printThrowable(e, "TD"); }
        }

        File MOTDFile = new File(WebServer.storageDir, "MOTD.txt");
        if (MOTDFile.exists())
        {
            String motd = "";
            try (BufferedReader br = new BufferedReader(new FileReader(MOTDFile)))
            {
                String line;
                while ((line = br.readLine()) != null)
                    motd += line;

                if (motd.isEmpty())
                    motd = "No problems";
            }
            catch (IOException e) { WebServer.printThrowable(e, "TD"); }

            WebServer.TDData.put("XXMOTD", motd);
        }
    }
    public static Listener getInstance()
    {
        if (instance == null)
            instance = new TDHandler();

        return instance;
    }

    @Override
    public void message(Map<String, String> headers, String body)
    {
        StompConnectionHandler.printStompHeaders(headers);

        //<editor-fold defaultstate="collapsed" desc="TD Data">
        JSONArray messageList = new JSONArray(body);

        Map<String, String> updateMap = new HashMap<>();

        for (int j = 0; j < messageList.length(); j++)
        {
            JSONObject obj = messageList.getJSONObject(j);
            try
            {
                String msgType = obj.keySet().toArray(new String[0])[0];
                JSONObject indvMsg = obj.getJSONObject(msgType);

                indvMsg.put("address", indvMsg.getString("area_id") + indvMsg.optString("address"));

                switch (msgType.toUpperCase())
                {
                    case "CA_MSG":
                        updateMap.put(indvMsg.getString("area_id") + indvMsg.getString("from"), "");
                        updateMap.put(indvMsg.getString("area_id") + indvMsg.getString("to"), indvMsg.getString("descr"));
                        break;
                    case "CB_MSG":
                        updateMap.put(indvMsg.getString("area_id") + indvMsg.getString("from"), "");
                        break;
                    case "CC_MSG":
                        updateMap.put(indvMsg.getString("area_id") + indvMsg.getString("to"), indvMsg.getString("descr"));
                        break;

                    case "SF_MSG":
                    {
                        char[] data = toBinaryString(Integer.parseInt(indvMsg.getString("data"), 16)).toCharArray();

                        for (int i = 0; i < data.length; i++)
                        {
                            String changedBit = Integer.toString(8 - i);
                            String address = indvMsg.get("address") + ":" + changedBit;

                            updateMap.put(address, String.valueOf(data[i]));
                        }
                        break;
                    }

                    case "SG_MSG":
                    case "SH_MSG":
                    {
                        String addrStart = indvMsg.getString("address").substring(0, 3);
                        String addrEnd = indvMsg.getString("address").substring(3);

                        int data[] = { Integer.parseInt(indvMsg.getString("data").substring(0, 2), 16),
                            Integer.parseInt(indvMsg.getString("data").substring(2, 4), 16),
                            Integer.parseInt(indvMsg.getString("data").substring(4, 6), 16),
                            Integer.parseInt(indvMsg.getString("data").substring(6, 8), 16) };

                        String[] addresses = {indvMsg.getString("address"),
                            addrStart + (addrEnd.equals("0") ? "1" : addrEnd.equals("4") ? "5" : addrEnd.equals("8") ? "9" : "D"),
                            addrStart + (addrEnd.equals("0") ? "2" : addrEnd.equals("4") ? "6" : addrEnd.equals("8") ? "A" : "E"),
                            addrStart + (addrEnd.equals("0") ? "3" : addrEnd.equals("4") ? "7" : addrEnd.equals("8") ? "B" : "F")};

                        for (int i = 0; i < data.length; i++)
                            updateMap.put(addresses[i], Integer.toString(data[i]));

                        break;
                    }
                }
            }
            catch (Exception e) { WebServer.printThrowable(e, "TD"); }
        }

        WebServer.TDData.putAll(updateMap);
        WebServer.guiData.updateData();
        //</editor-fold>

        JSONObject container = new JSONObject();
        JSONObject message = new JSONObject();
        message.put("type", MessageType.SEND_UPDATE.getName());
        message.put("timestamp", System.currentTimeMillis());
        message.put("message", updateMap);
        container.put("Message", message);
        
        String msgString = container.toString();
        Collections.unmodifiableCollection(WebServer.webSocket.connections()).stream().forEach(c -> c.send(msgString));
        
        //StringBuilder sb = new StringBuilder("{\"Message\":{");
        //sb.append("\"type\":\"").append(MessageType.SEND_UPDATE.getName()).append("\",");
        //sb.append("\"message\":{");

        //updateMap.entrySet().stream()
        //        .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));

        //if (sb.charAt(sb.length()-1) == ',')
        //    sb.deleteCharAt(sb.length()-1);
        //sb.append("}}}");

        //Collections.unmodifiableCollection(WebServer.webSocket.connections()).stream().forEach(c -> c.send(sb.toString()));
        
        StompConnectionHandler.lastMessageTimeGeneral = System.currentTimeMillis();
        StompConnectionHandler.ack(headers.get("ack"));
    }

    public static String toBinaryString(int i)
    {
        return String.format("%" + ((int) Math.ceil(Integer.toBinaryString(i).length() / 8f) * 8) + "s", Integer.toBinaryString(i)).replace(" ", "0");
    }

    public long getTimeout() { return System.currentTimeMillis() - StompConnectionHandler.lastMessageTimeGeneral; }
    public long getTimeoutThreshold() { return 30000; }
}