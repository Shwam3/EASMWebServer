package com.shwam.easm.webserver.stomp.handlers;

import com.shwam.easm.webserver.MessageType;
import com.shwam.easm.webserver.WebServer;
import com.shwam.easm.webserver.stomp.StompConnectionHandler;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jsonparser.JSONParser;
import net.ser1.stomp.Listener;

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
                Map<String, Object> json = (Map<String, Object>) JSONParser.parseJSON(jsonString).get("TDData");

                json.entrySet().stream().filter(p -> p.getKey().length() == 6).forEach(p -> WebServer.TDData.put(p.getKey(), (String) p.getValue()));

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
        List<Map<String, Map<String, String>>> messageList = (List<Map<String, Map<String, String>>>) JSONParser.parseJSON("{\"TDMessage\":" + body + "}").get("TDMessage");

        Map<String, String> updateMap = new HashMap<>();

        for (Map<String, Map<String, String>> map : messageList)
        {
            try
            {
                String msgType = map.keySet().toArray(new String[0])[0];
                Map<String, String> indvMsg = map.get(msgType);

                indvMsg.put("address", indvMsg.get("area_id") + indvMsg.get("address"));

                switch (msgType.toUpperCase())
                {
                    case "CA_MSG":
                        updateMap.put(indvMsg.get("area_id") + indvMsg.get("from"), "");
                        updateMap.put(indvMsg.get("area_id") + indvMsg.get("to"), indvMsg.get("descr"));
                        break;
                    case "CB_MSG":
                        updateMap.put(indvMsg.get("area_id") + indvMsg.get("from"), "");
                        break;
                    case "CC_MSG":
                        updateMap.put(indvMsg.get("area_id") + indvMsg.get("to"), indvMsg.get("descr"));
                        break;

                    case "SF_MSG":
                    {
                        char[] data = toBinaryString(Integer.parseInt(indvMsg.get("data"), 16)).toCharArray();

                        for (int i = 0; i < data.length; i++)
                        {
                            String changedBit = Integer.toString(8 - i);
                            String address = indvMsg.get("address") + ":" + changedBit;

                            if (!WebServer.TDData.containsKey(address)) //|| !WebServer.TDData.get(address).equals(String.valueOf(data[i])))
                                updateMap.put(address, String.valueOf(data[i]));
                        }
                        break;
                    }

                    case "SG_MSG":
                    case "SH_MSG":
                    {
                        String addrStart = indvMsg.get("address").substring(0, 3);
                        String addrEnd = indvMsg.get("address").substring(3);

                        int data[] = { Integer.parseInt(indvMsg.get("data").substring(0, 2), 16),
                            Integer.parseInt(indvMsg.get("data").substring(2, 4), 16),
                            Integer.parseInt(indvMsg.get("data").substring(4, 6), 16),
                            Integer.parseInt(indvMsg.get("data").substring(6, 8), 16) };

                        String[] addresses = {indvMsg.get("address"),
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

        StringBuilder sb = new StringBuilder("{\"Message\":{");
        sb.append("\"type\":\"").append(MessageType.SEND_UPDATE.getName()).append("\",");
        sb.append("\"message\":{");

        updateMap.entrySet().stream()
                .forEach(p -> sb.append("\"").append(p.getKey()).append("\":\"").append(p.getValue()).append("\","));

        if (sb.charAt(sb.length()-1) == ',')
            sb.deleteCharAt(sb.length()-1);
        sb.append("}}}");

        Collections.unmodifiableCollection(WebServer.webSocket.connections()).stream().forEach(c -> c.send(sb.toString()));

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