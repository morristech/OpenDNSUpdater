package fr.guillaumevillena.opendnsupdater.event;

public class IpUpdatedEvent {
    private String ip;

    public IpUpdatedEvent(String ip) {

        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }
}
