import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.File;



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

        private String http = "HTTP/1.1";
        private String methodtype = null;
        private String resourse = null;
        private String parameters = null;
        private int code = 500;
        private String message = "Internal Server Error";
        private String response = "";

        private void transfer (int code, String message) {
            this.transfer (code,message,"");
        }

        private void transfer (int code, String message,String response) {
            if (code != 0 && message != "") {
                this.code = code;
                this.message = message;
            }
            if (response != "") {
                this.response = response;
            }
        }

        private SocketProcessor(Socket s) throws Throwable {
            this.s = s;
            this.is = s.getInputStream();
            this.os = s.getOutputStream();
        }

        public void run() {
            try {
                System.out.println (readRequest());
                writeResponse(this.response);
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
        }

        private void writeResponse(String str) throws Throwable {
            /* История запросов */
            try (FileWriter writer = new FileWriter ("History.txt", true)) {
                DateFormat dateFormat = new SimpleDateFormat (" dd/MM/yy HH:mm");
                Date date = new Date();
                writer.append ("\r\n").write (methodtype + " http://localhost:8080/" + resourse + dateFormat.format(date));
                writer.flush ();
            }

            str = "<html>\r\n<body>\r\n<b>\r\n" + str + "\r\n</b>\r\n</body>\r\n</html>";

            String response;
            response = http + " " + code + " " + message + "\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
//                    "Accept-Charset: utf-8\r\n" +
//                    "Accept-Language: ru\r\n" +
//                    "Content-Language: ru\r\n" +
                    "Content-Length: " + str.length () + "\r\n" +
                    "Allow: GET, POST, UPDATE, DELETE \r\n" +
                    "Connection: close\r\n\r\n";


            String result = response + str;
            os.write(result.getBytes());
            os.flush();
        }

        private void get (String res) throws Throwable {
            if (!("".equalsIgnoreCase(res))) {
                try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream (res), StandardCharsets.UTF_8))) {
                    String str;
                    while ((str = reader.readLine ()) != null) {
                        response += str + "<br>";
                    }
                    transfer (200, "OK");
                    reader.close();
                } catch (FileNotFoundException e) {
                    transfer (404, "Not Found", "Not Found");
                }
            }
            else {transfer (200, "OK", "Hello");}
        }

        private void update (String res) throws Throwable {
            try (FileWriter updater = new FileWriter ("LastUpdate.txt", false)) {
                updater.write (res);
                updater.flush ();
                transfer (202, "Accepted", "Success update");
            }
            catch ( FileNotFoundException e ) {
                transfer (404, "Not Found", "Not Found");
            }
        }

        private void delete (String res)  {
            File file = new File ("C:\\Users\\nadirov-aa\\Downloads\\WebApp\\Server\\" + res);
            if (file.delete ()) {
                transfer (202, "Accepted", "Файл успешно удален");
            } else {
                transfer (404, "Not Found", "Not Found");
            }
        }

        private Boolean access() throws Throwable {
            boolean b = true;
            try {
                BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("SystemFiles.txt"), StandardCharsets.UTF_8));
                {
                    String str;
                    while ((str = reader.readLine ()) != null) {
                        if (str.equalsIgnoreCase (resourse)) {
                            b=false;
                            transfer (403, "Forbidden", "Error: Аccess denied");
                        }
                    }
                    reader.close ();
                }
            } catch(FileNotFoundException e) { }

            return b;
        }

        private void checkFL (String line) {
            int x = 0, y = 0;
            String str;
            try {
                while (x >= 0) {
                    y++;
                    x = line.indexOf (" ");
                    if (x > 0) {
                        str = line.substring (0, x);
                        line = line.substring (x + 1, line.length ());
                    } else str = line;
                    switch (y) {
                        case 1:
                            methodtype = str;
                            break;
                        case 2:
                            resourse = str.substring (1, str.length ());
                            break;
                        case 3:
//                            http = str; /* don't save */
                            break;
                        default: { /*do nothing*/ }
                    }
                }
                if ( y < 3 ) {
                    transfer (400,"Bad Request", "Bad Request");
                }
            }
            catch (Exception e) {
                transfer (400,"Bad Request", "Bad Request");
            }
        }

        private String readRequest() throws Throwable {

            String[] lines = new String[120];
            DateFormat dateFormat = new SimpleDateFormat (" dd/MM/yy HH:mm");
            Date date = new Date();

            int streamSize = 1024;
            byte[] buffer = new byte[streamSize];
            FileWriter writer = new FileWriter ("File.txt", false);
            boolean once = false;

            while (true){
                int readBytesCount = is.read(buffer);

                if (readBytesCount > 128) {
                    /* Чтение потока отправленного через браузер */
                    String FL = new String (buffer).substring (0, new String (buffer).indexOf ("\r\n"));
                    checkFL (FL);

                    if (true)
                    {
                        while (readBytesCount >= streamSize)
                        {
                            writer.write (new String (buffer).trim ());
                            readBytesCount = is.read(buffer);
                        }
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
                        transfer ( 400, "Bad request", "Error of read stream");
                        break;
                    }
                    writer.flush ();
                }
            }
            writer.close ();
            /* Second checking method type (for TELNET)*/
            if (code == 500) {
                try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("File.txt"), StandardCharsets.UTF_8))) {
                    if (methodtype == null) {
                        String FL = reader.readLine ().trim ();
                        if (true) {
                            checkFL (FL);
                        }
                    }
                } finally {

                    switch (methodtype) {

                        case "GET": {
                            if (access ()) get (resourse);
                        }
                        break;

                        case "UPDATE": {
                            update (dateFormat.format (date));
                        }
                        break;

                        case "DELETE": {
                            if (access ()) delete (resourse);
                        }
                        break;

                        case "POST": {
                            try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("File.txt"), StandardCharsets.UTF_8))) {

                                once = false;
                                int h = 0;
                                try {
                                    while (true) {
                                        String str = reader.readLine ().trim ();
                                        if (str != null) if (str.length () != 0) {
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
                                } catch (Exception ex) {
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
                                if (parameters != null) {
                                    transfer (201, "Created", "Successfully created: " + parameters);
                                } else {
                                    transfer (500, "Internal Server Error", "Error of transfer data : " + parameters);
                                }
                            }

                        }
                        break;

                        default: {

                            if (code == 500) {
//                            transfer (405,"Method Not Allowed", "Method Not Allowed");
                                transfer (501, "Not Implemented", "Not Implemented");
                            }
                            methodtype = "Undefined";
                        }
                        break;
                    }
                }
            }
            return methodtype;
        }
    }
}