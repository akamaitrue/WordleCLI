package com.server.main.multicast;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.server.main.GameServer;
import com.server.main.UtilsServer;

public final class Multicast extends GameServer {

    // costruttore privato per evitare che venga istanziata
    private Multicast() {}

    // metodo usato dal server per inviare un messaggio multicast al gruppo sociale
    public static void sendMulticast(String username, String word, MulticastSocket s, String host, int port) {
        try {
            long timestamp = System.currentTimeMillis();
            // convert timestamp to date
            Date date = new Date(timestamp);
            String strDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(date);

            final String msg;

            // get content of notifications.json
            JsonArray notificationsArray = GameServer.gson.fromJson(new FileReader(UtilsServer.projectDir + "/data/notifications.json"), JsonArray.class);
            if (notificationsArray == null) { notificationsArray = new JsonArray(); }

            synchronized(notificationsArray) {
                // add new notification
                JsonObject newNotification = new JsonObject();
                newNotification.addProperty("timestamp", strDate);
                if (username.split(" ")[0].equalsIgnoreCase("Leaderboard")) {
                    newNotification.addProperty("message", username);
                    msg = username;
                }
                else {
                    if (username.split(" ")[0].equalsIgnoreCase("Server")) {
                        newNotification.addProperty("message", username);
                        msg = username;
                    }
                    else {
                        // condivisione social
                        newNotification.addProperty("username", username);
                        newNotification.addProperty("word", word);
                        // rimuovi \n da WordleServer.socialScore.get(username)
                        newNotification.addProperty("message", GameServer.socialScore.get(username).stripIndent());
                        msg = word + " " + username + " \n" + GameServer.socialScore.get(username);
                        notifications.add(msg);
                    }
                }
                notificationsArray.add(newNotification);
                // sort notifications by timestamp
                notificationsArray = UtilsServer.sortJsonArray(notificationsArray, "timestamp");

                PrintWriter writer = new PrintWriter(UtilsServer.projectDir + "/data/notifications.json");
                writer.print("");
                writer.close();

                FileWriter file = new FileWriter(UtilsServer.projectDir + "/data/notifications.json");
                file.write(GameServer.gson.toJson(notificationsArray));
                file.close();
            }

            byte[] buf = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(host), port);
            // invio del datagramma
            s.send(packet);
        } catch (IOException e) {
            System.err.println("Errore in sendMulticast: " + e.getMessage());
        }
    }


    // metodo usato dal client quando, dopo il login, si unisce al gruppo multicast per ricevere le notifiche
    public static void joinGroup(String host, int port, Socket s, MulticastSocket ms, Thread t) {
        byte[] buf = new byte[8192]; // buffer di 8KB
        // c'è un thread apposito che si mette in ascolto di messaggi multicast e li gestisce
        t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ms.joinGroup(InetAddress.getByName(host));
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        // ricevo il datagramma udp, lo converto in stringa e lo stampo
                        ms.receive(packet);
                        // svuota il buffer
                        String received = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("[ MULTICAST ]: " + received);
                        // la notifica viene aggiunta alla lista delle notifiche
                        notifications.add(received);
                        if (received.equalsIgnoreCase("Server shutdown")) {
                            // se ricevo il messaggio di chiusura del server, esco dal gruppo multicast e chiudo la socket
                            break;
                        }
                    }
                    ms.leaveGroup(InetAddress.getByName(host));
                    ms.close();
                    s.close();
                    System.exit(0);
                } catch (IOException e) {
                    if (s.isClosed()) {
                        //System.err.println("Socket chiusa");
                        return;
                    }
                    System.err.println("Errore in joinGroup: " + e.getMessage());
                }
            } 
        });
        t.start();
    }
}
