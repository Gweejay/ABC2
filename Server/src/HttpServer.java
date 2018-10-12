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
        private String resource = null;
        private int code = 100;
        private String message = "Continue", response = "Continue";
        private String Allow = "GET, POST, UPDATE, DELETE";
        private String address = "";

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

        private void transfer (int code, String message,String response, String allow) {
            if (allow != "") {
                this.Allow = allow;
            }
            this.transfer (code,message,response);
        }

        private SocketProcessor(Socket s) throws Throwable {
            this.s = s;
            this.is = s.getInputStream();
            this.os = s.getOutputStream();
        }

        public void run() {
            try {
                readRequest();
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
            System.out.println("3. Client processing finished (" + methodtype + ")");
        }

        private void writeResponse(String str) throws Throwable {

            try (FileWriter writer = new FileWriter ("General/History.txt", true)) {
                DateFormat dateFormat = new SimpleDateFormat (" dd/MM/yy HH:mm");
                Date date = new Date();
                writer.append ("\r\n").write (methodtype + " http://localhost:8080/" + resource + dateFormat.format(date));
                writer.flush ();
            }

            str = "<!DOCTYPE HTML><html><head><meta charset=\"utf-8\"></head>\r\n<body>\r\n<div>\r\n" + str + "\r\n</div>\r\n</body>\r\n</html>";

            String response;
            response = http + " " + code + " " + message + "\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: " + str.length () + "\r\n" +
                    "Allow: " + Allow + "\r\n" +
                    "Connection: close\r\n\r\n";

            String result = response + str;
            os.write(result.getBytes());
            os.flush();
        }

        private void get () throws Throwable {
            System.out.println ("Запрос на получение: " + resource);
            response = "";
            if (!("".equalsIgnoreCase(resource))) {
                try (BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream (resource), StandardCharsets.UTF_8))) {
                    String str;
                    while ((str = reader.readLine ()) != null) {
                        response += str + "<br>";
                    }
                    transfer (200, "OK");
                    reader.close();
                } catch (FileNotFoundException e) {
                    transfer (404, "Not Found", "Not Found");
                }
            } else {
                transfer (200, "OK", "Hello", "GET, POST");
            }
        }

        private void delete ()  {
            System.out.println ("Запрос на удаление: " + resource);
            if (resource.substring (0,2).equalsIgnoreCase ("id")) {
                File file = new File (resource);
                if (file.delete ()) {
                    transfer (202, "Accepted", "Файл успешно удален");
                } else {
                    transfer (404, "Not Found", "Not Found");
                }
            } else {
                transfer (403, "Forbidden", "Error: Аccess denied");
            }
        }

        private void update (String data) throws Throwable {
            System.out.println ("Запрос на обновление: " + resource);
            if (!("".equalsIgnoreCase(resource))) {
                try (FileWriter updater = new FileWriter (resource, false)) {
                    updater.write (data);
                    updater.flush ();
                    transfer (202, "Accepted", "Success update");
                } catch (FileNotFoundException e) {
                    transfer (404, "Not Found", "Not Found");
                }
            } else {transfer (405, "Method Not Allowed", "Method Not Allowed", "GET, POST");}
        }

        private void post (String operator) throws Throwable {
            System.out.println ("Сохранение файла: " + address);
            FileWriter writer = new FileWriter ( address, false);
            writer.write(operator);
            transfer (201, "Created", "<p>Successfully created: your file is on "
                    + "<a href =\""
                    + "http://localhost:8080/" + address
                    + "\">Ссылка</a></p>");
            writer.flush ();
            writer.close();
        }

        private Boolean access() throws Throwable {
            boolean b = true;
            try {
                BufferedReader reader = new BufferedReader (new InputStreamReader (new FileInputStream ("General/List.txt"), StandardCharsets.UTF_8));
                {
                    System.out.println ("Проверка доступа к: " + resource);
                    String str;
                    while ((str = reader.readLine ()) != null) {
                        if (str.equalsIgnoreCase (resource)) {
                            b=false;
                            transfer (403, "Forbidden", "Error: Аccess denied");
                        }
                    }
                    reader.close ();
                }
            } catch(FileNotFoundException e) {
                transfer (500, "Internal Server Error", "Internal Server Error");
                b=false;
            }

            return b;
        }

        private String format (String s) {

            s = s.substring ((s.indexOf ("\r\n\r\n")+4), s.length ());
            String index = "; filename=\"";
            String txt = s;
            int x = txt.indexOf (index), y = index.length ();

            if (x>0) {
                txt = txt.substring (x + y, txt.length ());
                txt = txt.substring (0, txt.indexOf ("\""));
                txt = txt.substring (txt.indexOf ("."), txt.length ());
            } else {
                txt=".txt";
            }
            if (methodtype.equalsIgnoreCase ("POST")) {
                address = "id" + newID (1) + txt;
            }
            if (s.contains ("------WebKitForm")) {
                s = s.substring ((s.indexOf ("\r\n\r\n") + 4), s.lastIndexOf ("------WebKitForm"));
            }
            return s;
        }

        private int newID (int id) {
            try (BufferedReader uploader = new BufferedReader (new InputStreamReader (new FileInputStream ("General/Base.txt"), StandardCharsets.UTF_8))) {
                String str = uploader.readLine ();
                id += Integer.parseInt (str);
                uploader.close ();
            } catch (IOException e) { }
            try (FileWriter uploader = new FileWriter ("General/Base.txt", false)) {
                uploader.append (id + "");
                uploader.flush ();
                uploader.close ();
            } catch (IOException e) { }

            return id;
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
                            resource = str.substring (1, str.length ());
                            break;
                        case 3:
                            if (!str.equalsIgnoreCase (http)) {
                                transfer (505 , "Http Version Not Supported", "Http Version Not Supported");
                            } break;
                        default:
                            { /*do nothing*/ }
                            break;
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

        private void readRequest() throws Throwable {

            String operator = "";
            int streamSize = 8192;
            byte[] buffer = new byte[streamSize];
            boolean once = false;

            while (true){
                int readBytesCount = is.read(buffer);

                if (readBytesCount > 128) {
                    /* Чтение потока более 128 байт */
                    String FL = new String (buffer).substring (0, new String (buffer).indexOf ("\r\n"));
                    checkFL (FL);
                    while (readBytesCount == streamSize)
                    {
                        operator = operator + (new String (buffer).trim ());
                        readBytesCount = is.read (buffer);
                    }
                    operator = operator + (new String (buffer).trim ());
                    break;
                }
                else { /* Чтение потока отправленного через telnet (посимвольно) */
                    if (readBytesCount == 1 || readBytesCount > 3 ) { /*Символ или строка(для корректной работы требуется не менее 4 байт информации)*/
                        System.out.println (new String (buffer).trim ());
                        if ( (new String (buffer).trim ().length ()) == 0 ) {
                            operator += " ";
                        }
                        else {
                            operator += new String (buffer).trim ();
                        }
                    }
                    if (readBytesCount == 2) { /*Enter*/
                        if (once) {
                            break;
                        }
                        System.out.println ("Enter");
                        operator += "\r\n";
                        checkFL (operator);
                        once = true;
                    }
                    else { once = false; }

                    if (readBytesCount == 3) { /*Стрелки*/
                        transfer ( 400, "Bad request", "Error of read stream");
                        break;
                    }
                }
            }
            operator=format(operator);
            if (code == 100) {
                switch (methodtype) {
                    case "GET": {
                        if (access ()) get ();
                    }
                    break;

                    case "DELETE": {
                        if (access ()) delete ();
                    }
                    break;

                    case "UPDATE": {
                        if (access ()) update (operator);
                    }
                    break;

                    case "POST": {
//                        if (!"".equalsIgnoreCase (resource))
//                        {
//                            if (access ()) update (operator);
//                        }
//                        else
                        post (operator);
                    }
                    break;

                    default: {
                        transfer (501, "Not Implemented", "Not Implemented");
                        methodtype = "Undefined";
                    }
                    break;
                }
            }
        }
    }
}