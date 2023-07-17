package com.client.main;
import java.io.*;
import com.google.gson.stream.JsonReader;
 
 public final class UtilsClient {

    // costruttore privato perché è una utility class e non deve essere istanziata
    private UtilsClient() {}

    public static String projectDir = System.getProperty("user.dir");
    // legge il file di configurazione config.json e assegno i valori scelti dall'utente
    public static void parseConfig() throws IOException {
        // il path è Wordle(root) / settings / user_settings.json
        try (JsonReader jsonReader = new JsonReader(new FileReader(projectDir + "/settings/user_settings.json"))) {
            jsonReader.beginObject();
            while (jsonReader.hasNext()) {
                String name = jsonReader.nextName();
                switch (name) {
                case "username":
                    ClientMain.username = jsonReader.nextString();
                    break;
                case "password":
                    ClientMain.password = jsonReader.nextString();
                    break;
                case "ip":
                    ClientMain.ip = jsonReader.nextString();
                    break;
                case "port":
                    ClientMain.port = jsonReader.nextInt();
                    break;
                case "multicast_address":
                    ClientMain.multicast = jsonReader.nextString();
                    break;
                case "multicast_port":
                    ClientMain.multicast_port = jsonReader.nextInt();
                    break;
                case "timeout":
                    ClientMain.sockTimeout = jsonReader.nextInt();
                    break;
                case "max_login_retries":
                    ClientMain.max_login_retries = jsonReader.nextInt();
                    break;
                default:
                    jsonReader.skipValue();
                    break;
                }
            }
            jsonReader.endObject();
        }
        catch (FileNotFoundException e) {
            System.out.println("File not found");
        }
    }

    // metodo usato per eseguire un polling periodico per controllare se la parola da indovinare è stata aggiornata
    // l'attesa del polling tra una richiesta e l'altra è implementata tramite Thread.sleep() in ClientMain
    // questo metodo è usato solo quando l'utente ha terminato una partita (sia con vittoria che con sconfitta) e vuole giocarne un'altra mantenendo la stessa sessione
    public static boolean getUpdates(String username, PrintWriter writer, BufferedReader reader) throws IOException{
        writer.println("[PULL] " + username);
        writer.flush();
        String response = reader.readLine();
        if (response.equalsIgnoreCase("OK")) {
            return true; }
        return false;
    }

    // metodo usato dall'utente per registrarsi
    // non era richiesto dal progetto, ma per rendere la registrazione più realistica ho aggiunto un controllo sulla password scelta
    public static boolean register(String username, String password, PrintWriter writer) {
        // la password deve essere lunga almeno 8 caratteri e deve contenere (almeno) sia un numero che un carattere speciale
        if (password.length() < 8 || !password.matches(".*\\d.*") || !password.matches(".*[!@#$%^&*()_+].*")) {
            System.out.println("Password must be at least 8 characters long and include both a number and a special character");
            return false;
        }

        writer.println("[REGISTER] " + username + " " + password);
        writer.flush();
        return true;
    }

    // invia semplicemente una richiesta al server per effettuare il login
    public static void login(String username, String password, PrintWriter writer) {
        writer.println("[LOGIN] " + username + " " + password);
        writer.flush();
    }

    // logout: self-explanatory
    public static void logout(String username, PrintWriter writer) {
        writer.println("[LOGOUT] " + username);
        writer.flush();
    }

   
    // inizia una nuova partita, tutta la logica e i vari controlli sono implementati server-side
    public static void playWORDLE(PrintWriter writer, String username) {
        writer.println("[PLAYWORDLE] " + username);
        writer.flush();
    }

    // invia al server una parola (tentativo)
    public static void sendWord(String guess, PrintWriter writer) {
        writer.println("[GUESS] " + guess);
        writer.flush();                
    }

    // a fine partita, sia in caso di vittoria che di sconfitta, l'utente riceve dal server le proprie statistiche
    // questo metodo invia una richiesta al server per ricevere le statistiche
    public static void sendMeStatistics(String username, PrintWriter writer) {
        writer.println("[STATS] " + username);
        writer.flush();
    }

    // lancia un thread dedicato per la gestione di un eventuale SIGINT
    // altra logica per il SIGINT è implementata nel metodo joinGroup()
    public static void handleSIGINT(String username, PrintWriter writer) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("disconnecting...");
                writer.println("[LOGOUT] " + username);  
                writer.flush();
            }
        });
    }

    // l'utente può richiedere al server di condividere il risultato della partita sul gruppo sociale (multicast)
    public static void share(String msg, PrintWriter writer) {
        writer.println("[SHARE] " + msg);
        writer.flush();
    }

    // l'utente può richiedere al server di mostrargli le notifiche riguardo alle partite degli altri utenti
    public static void showMeSharing(String username, PrintWriter writer) {
        // showMeSharing(): mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti
        writer.println("[SHOWMESHARING] " + username);
        writer.flush();
    }

    // l'utente può richiedere al server di mostrargli la classifica corrente
    public static void showMeRanking(PrintWriter writer) {
        writer.println("[RANKS]");
        writer.flush();
    }
}