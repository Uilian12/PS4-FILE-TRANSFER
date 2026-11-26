

package com.ps4filetransfer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {

    EditText ip, port, remotePath;
    TextView status, filesText;
    ArrayList<Uri> selectedFiles = new ArrayList<>();
    StringBuilder names = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 30, 25, 25);
        layout.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("PS4 FILE TRANSFER");
        title.setTextSize(25);
        title.setTextColor(0xFFFFFFFF);
        layout.addView(title);

        ip = new EditText(this);
        ip.setSingleLine();
        ip.setHint("IP do PS4");
        ip.setTextColor(0xFFFFFFFF);
        ip.setHintTextColor(0xFFAAAAAA);
        layout.addView(ip);

        port = new EditText(this);
        port.setSingleLine();
        port.setHint("Porta FTP");
        port.setText("2121");
        port.setInputType(2);
        port.setTextColor(0xFFFFFFFF);
        layout.addView(port);

        remotePath = new EditText(this);
        remotePath.setSingleLine();
        remotePath.setText("/data/");
        remotePath.setTextColor(0xFFFFFFFF);
        remotePath.setHintTextColor(0xFFAAAAAA);
        layout.addView(remotePath);

        Button connect = new Button(this);
        connect.setText("TESTAR CONEXÃO");
        layout.addView(connect);

        Button select = new Button(this);
        select.setText("SELECIONAR ARQUIVOS");
        layout.addView(select);

        filesText = new TextView(this);
        filesText.setTextColor(0xFFFFFFFF);
        layout.addView(filesText);

        Button send = new Button(this);
        send.setText("ENVIAR PARA PS4");
        layout.addView(send);

        status = new TextView(this);
        status.setTextColor(0xFF00FF99);
        status.setText("Aguardando conexão...");
        layout.addView(status);

        setContentView(layout);

        connect.setOnClickListener(v -> testConnection());
        select.setOnClickListener(v -> selectFiles());
        send.setOnClickListener(v -> uploadFiles());
    }

    private void selectFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode,
            int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK
                && data != null) {
            selectedFiles.clear();
            names.setLength(0);

            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    selectedFiles.add(uri);
                    names.append(getFileName(uri)).append("\n");
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                selectedFiles.add(uri);
                names.append(getFileName(uri));
            }

            filesText.setText(names.toString());
            status.setText(selectedFiles.size() + " arquivo(s) selecionado(s)");
        }
    }

    private String getFileName(Uri uri) {
        String result = "arquivo";
        Cursor cursor = getContentResolver().query(
                uri, null, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    private void testConnection() {
        new Thread(() -> {
            try {
                String host = ip.getText().toString().trim();
                int p = Integer.parseInt(port.getText().toString());

                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, p), 5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                String response = reader.readLine();
                socket.close();

                runOnUiThread(() ->
                        status.setText("PS4 encontrado: " + response));

            } catch (Exception e) {
                runOnUiThread(() ->
                        status.setText("Erro: " + e.getMessage()));
            }
        }).start();
    }

    private void uploadFiles() {
        if (selectedFiles.isEmpty()) {
            status.setText("Selecione os arquivos primeiro.");
            return;
        }

        new Thread(() -> {
            for (Uri uri : selectedFiles) {
                try {
                    uploadOne(uri);
                    runOnUiThread(() ->
                            status.setText("Enviado: " + getFileName(uri)));
                } catch (Exception e) {
                    runOnUiThread(() ->
                            status.setText("Falha: " + e.getMessage()));
                    return;
                }
            }

            runOnUiThread(() ->
                    status.setText("Transferência concluída!"));

        }).start();
    }

    private String readReply(BufferedReader reader) throws Exception {
        String line = reader.readLine();
        if (line == null) throw new IOException("Conexão encerrada");

        StringBuilder reply = new StringBuilder(line);

        if (line.length() >= 4 && line.charAt(3) == '-') {
            String end = line.substring(0, 3) + " ";
            while ((line = reader.readLine()) != null) {
                reply.append("\n").append(line);
                if (line.startsWith(end)) break;
            }
        }
        return reply.toString();
    }

    private void command(BufferedWriter writer,
            BufferedReader reader, String cmd) throws Exception {
        writer.write(cmd + "\r\n");
        writer.flush();
        readReply(reader);
    }

    private void uploadOne(Uri uri) throws Exception {
        String host = ip.getText().toString().trim();
        int p = Integer.parseInt(port.getText().toString());
        String folder = remotePath.getText().toString().trim();

        if (!folder.endsWith("/")) folder += "/";

        Socket control = new Socket();
        control.connect(new InetSocketAddress(host, p), 10000);
        control.setSoTimeout(15000);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(control.getInputStream()));
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(control.getOutputStream()));

        readReply(reader);

        command(writer, reader, "USER anonymous");
        command(writer, reader, "PASS android@");
        command(writer, reader, "TYPE I");

        command(writer, reader, "PASV");

        writer.write("PASV\r\n");
        writer.flush();
        String reply = readReply(reader);

        Matcher matcher = Pattern.compile(
                "\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)")
                .matcher(reply);

        if (!matcher.find()) {
            control.close();
            throw new IOException("Modo PASV não disponível: " + reply);
        }

        String dataHost = matcher.group(1) + "." +
                matcher.group(2) + "." +
                matcher.group(3) + "." + matcher.group(4);

        if (dataHost.equals("0.0.0.0")) dataHost = host;

        int dataPort = Integer.parseInt(matcher.group(5)) * 256
                + Integer.parseInt(matcher.group(6));

        Socket data = new Socket();
        data.connect(new InetSocketAddress(dataHost, dataPort), 10000);

        String filename = getFileName(uri)
                .replace("/", "_")
                .replace("\\", "_");

        writer.write("STOR " + folder + filename + "\r\n");
        writer.flush();

        String storReply = readReply(reader);

        if (!storReply.startsWith("125") &&
                !storReply.startsWith("150")) {
            data.close();
            control.close();
            throw new IOException("FTP recusou arquivo: " + storReply);
        }

        try (InputStream input =
                     getContentResolver().openInputStream(uri);
             OutputStream output = data.getOutputStream()) {

            if (input == null) throw new IOException("Arquivo inválido");

            byte[] buffer = new byte[65536];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
        }

        data.close();
        readReply(reader);
        control.close();
    }
}
