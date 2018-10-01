import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;



public class HttpServer {

    public static void main(String[] args) throws Throwable {
        ServerSocket ss = new ServerSocket(8080);
        while (true) {
            System.out.println("1. Wait for clients");
            Socket s = ss.accept();
            System.out.println("2. Client accepted");
            new Thread(new SocketProcessor(s)).start();
        }
    }

    private static class SocketProcessor implements Runnable {

        private Socket s;
        private InputStream is;
        private OutputStream os;
        private long startTime = System.currentTimeMillis();
        private long timeSpent;
        private String[] timeArr = new String[10];

        private SocketProcessor(Socket s) throws Throwable {
            this.s = s;
            this.is = s.getInputStream();
            this.os = s.getOutputStream();
        }

        public void run() {
            try {
                String response=readRequest();
                writeResponse(response);
            } catch (Throwable t) {
                /*do nothing*/
            } finally {
                try {
                    s.close();
                } catch (Throwable t) {
                    /*do nothing*/
                }
            }
            System.out.println("4. Client processing finished");
            timeSpent = System.currentTimeMillis() - startTime;
            timeArr[1] = ("Время выполнения программы: " + timeSpent + " миллисекунд");
            System.out.println (timeArr[1]);
        }

        private void writeResponse(String str) throws Throwable {
            /* История запросов */
            try (FileWriter writer = new FileWriter ("History.txt", true)) {
                DateFormat dateFormat = new SimpleDateFormat (" dd/MM/yy HH:mm");
                Date date = new Date();
                writer.append ("\r\n").write (str + dateFormat.format(date));
                writer.flush ();
            }

            str = "<html>\r\n<body>\r\n<h1>\r\n" + str + "\r\n</h1>\r\n</body>\r\n</html>";

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Server: 123\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: " + str.length() + "\r\n" +
                    "Connection: close\r\n\r\n";

            String result = response + str;

            os.write(result.getBytes());
            os.flush();
        }

        private void readFile() throws Throwable {

            try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("LastUpdate.txt"), StandardCharsets.UTF_8))) {
                String str;
                System.out.println ("GET-запрос:");
                while ((str = reader.readLine ()) != null) {
                    System.out.println ("Строка из файла: " + str);
                }
            }
        }

        private void changeFile(String str) throws Throwable {

            try (FileWriter updater = new FileWriter ("LastUpdate.txt", false)) {
                updater.write (str);
                updater.flush ();
            }
        }

        private String readRequest() throws Throwable, IOException {

            String parameters = null, methodtype = null;
            String[] lines = new String[120];
            DateFormat dateFormat = new SimpleDateFormat (" dd/MM/yy HH:mm");
            Date date = new Date();
            byte[] buffer = new byte[1024];

            FileWriter writer = new FileWriter ("File.txt", false);
            boolean once = false;
            while (true){
                int readBytesCount = is.read(buffer);

                if (readBytesCount > 128) { /* Чтение потока отправленного через браузер */
                    methodtype = new String(buffer).trim().substring (0,8);
                    methodtype = methodtype.substring (0,methodtype.indexOf(" "));

                    if ("POST".equalsIgnoreCase(methodtype)) {
                        writer.write (new String (buffer).trim ());
                        writer.flush ();
                    }
                    break;
                }
                else { /* Чтение потока отправленного через telnet (посимвольно) */
                    if (readBytesCount == 1 || readBytesCount > 3 ) { /*Символ или строка(для корректной работы требуется не менее 4 байт информации)*/
                        System.out.println (new String (buffer).trim ());
                        if ( (new String (buffer).trim ().length ()) == 0 ) {
                            writer.write (" "); /*Запись пробела*/
                        }
                        else {
                            writer.write (new String (buffer).trim ());
                        }
                    }

                    if (readBytesCount == 2) { /*Enter*/
                        if (once) {
                            break;
                        }
                        System.out.println ("Enter");
                        writer.append("\r\n").write (new String (buffer).trim ());
                        once = true;
                    }
                    else { once = false; }

                    if (readBytesCount == 3) { /*Стрелки*/
                        System.out.println ("ERROR of read stream");
                        break;
                    }
                    writer.flush ();
                }
            }
            /* Second checking method type (for TELNET)*/
            try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("File.txt"), StandardCharsets.UTF_8))) {
                if (methodtype == null) {
                    methodtype = reader.readLine().trim();
                    if (methodtype.trim ().indexOf (" ") > 0) {
                        methodtype = methodtype.substring (0, methodtype.indexOf (" "));
                    }
                }
            }
            finally {

                methodtype=methodtype.toUpperCase().trim ();

                switch (methodtype) {

                    case "GET": { readFile(); } break;

                    case "UPDATE": { changeFile(dateFormat.format(date)); } break;

                    case "DELETE": { changeFile(""); } break;

                    case "POST": {
                        try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("File.txt"), StandardCharsets.UTF_8))) {

                            once = false;
                            int h = 0;
                            while (true) {
                                String str = reader.readLine ().trim ();
                                if (str != null) if (str.length () != 0){
                                    h++;
                                    lines[h] = str;
                                }
                                if (str == null || str.length () == 0 || once) {
                                    if (once) {
                                        break;
                                    }
                                    once = true;
                                }
                            }
                            writer = new FileWriter ("Requests.txt", true);
                            /* Обработка заголовков запроса(перезапись в Requests.txt)*/
                            String[] mainHeaders, mainHeadersValue;
                            mainHeaders = new String[30];
                            mainHeadersValue = new String[30];

                            for (int i = 1, x, y; i <= h; i++) {

                                writer.write (lines[i] + "\r\n");
                                if (lines[i].indexOf (":") > 0) {
                                    x = lines[i].indexOf (":");
                                    y = lines[i].length ();
                                    mainHeaders[i] = lines[i].substring (0, x);
                                    mainHeadersValue[i] = lines[i].substring (x + 2, y);
                                }
                                parameters = lines[i].trim ();
                                if (!parameters.contains ("=")) {
                                    parameters = null;
                                }
                            }
                            writer.append ("\r\n");
                            writer.flush ();
                        }
                        methodtype += " <br>Параметры: " + parameters;
                    } break;

                    default: {
                        methodtype = "Undefined type or incorrect request";
                    } break;
                }
            }
            return methodtype;
        }
    }
}