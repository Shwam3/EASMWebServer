package shwam.easm.webserver;

public enum MessageType
{
    SOCKET_CLOSE (0x00, "SOCKET_CLOSE"),

    HEARTBEAT_REQUEST (0x01, "HEARTBEAT_REQUEST"),
    HEARTBEAT_REPLY   (0x02, "HEARTBEAT_REPLY"),

    REQUEST_ALL        (0x10, "REQUEST_ALL"),
    REQUEST_HIST_TRAIN (0x11, "REQUEST_HIST_TRAIN"),
    REQUEST_HIST_BERTH (0x12, "REQUEST_HIST_BERTH"),
    SET_NAME           (0x13, "SET_NAME"),

    SEND_ALL        (0x20, "SEND_ALL"),
    SEND_HIST_TRAIN (0x21, "SEND_HIST_TRAIN"),
    SEND_HIST_BERTH (0x22, "SEND_HIST_BERTH"),
    SEND_PROB_BERTH (0x23, "SEND_PROB_BERTH"),
    SEND_UPDATE     (0x24, "SEND_UPDATE"),

    SEND_MESSAGE (0x30, "SEND_MESSGE"),

    UNKNOWN_MESSAGE (-1, "UNKNOWN_MESSAGE");

    private final int    value;
    private final String name;

    private MessageType(int value, String name)
    {
        this.value = value;
        this.name  = name;
    }

    public int    getValue() { return value; }
    public String getName()  { return name;  }

    public static MessageType getType(int typeInt)
    {
        for (MessageType type : values())
            if (type.value == typeInt)
                return type;

        return UNKNOWN_MESSAGE;
    }

    public static MessageType getType(String typeString)
    {
        for (MessageType type : values())
            if (type.name.equalsIgnoreCase(typeString))
                return type;

        return UNKNOWN_MESSAGE;
    }
}