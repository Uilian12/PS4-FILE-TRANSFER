package com.ps4filetransfer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import android.database.Cursor;
import android.provider.OpenableColumns;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    EditText ip, port, user, pass, folder;
    TextView status;
    Uri selectedFile;
    String fileName;
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(25, 25, 25, 25);

        TextView title = new TextView(this);
        title.setText("PS4 FILE TRANSFER");
        title.setTextSize(25);
        layout.addView(title);

        ip = new EditText(this);
        ip.setHint("IP do PS4");
        layout.addView(ip);

        port = new EditText(this);
        port.setHint("Porta FTP");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText("2121");
        layout.addView(port);

        user = new EditText(this);
        user.setHint("Usuário FTP");
        user.setText("anonymous");
        layout.addView(user);

        pass = new EditText(this);
        pass.setHint("Senha FTP");
        pass.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(pass);

        folder = new EditText(this);
        folder.setHint("Pasta de destino");
        folder.setText("/data");
        layout.addView(folder);

        Button choose = new Button(this);
        choose.setText("SELECIONAR ARQUIVO");
        layout.addView(choose);

        Button send = new Button(this);
        send.setText("ENVIAR PARA O PS4");
        layout.addView(send);

        status = new TextView(this);
        status.setText("Aguardando arquivo...");
        layout.addView(status);

        setContentView(layout);

        choose.setOnClickListener(v -> selectFile());
        send.setOnClickListener(v -> uploadFile());
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode,
            int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK
                && data != null) {
            selectedFile = data.getData();

            Cursor cursor = getContentResolver().query(
                    selectedFile, null, null, null, null);

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(
                                OpenableColumns.DISPLAY_NAME);
                        fileName = index >= 0
                                ? cursor.getString(index) : "arquivo";
                    }
                } finally {
                    cursor.close();
                }
            }

            if (fileName == null) fileName = "arquivo";

            status.setText("Arquivo selecionado: " + fileName);
        }
    }

    private void uploadFile() {
        if (selectedFile == null) {
            Toast.makeText(this, "Selecione um arquivo primeiro",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final String host = ip.getText().toString().trim();
        final String ftpUser = user.getText().toString().trim();
        final String ftpPass = pass.getText().toString();
        final String destination = folder.getText().toString().trim();
        final String remotePort = port.getText().toString().trim();
        final Uri uri = selectedFile;
        final String name = fileName;

        if (host.isEmpty() || remotePort.isEmpty()
                || ftpUser.isEmpty()) {
            status.setText("Preencha IP, porta e usuário.");
            return;
        }

        sendStatus("Conectando ao PS4...");

        executor.execute(() -> {
            FTPClient ftp = new FTPClient();

            try {
                ftp.connect(host, Integer.parseInt(remotePort));

                if (!ftp.login(ftpUser, ftpPass)) {
                    throw new IOException("Falha no login FTP.");
                }

                ftp.enterLocalPassiveMode();
                ftp.setFileType(FTP.BINARY_FILE_TYPE);

                if (!ftp.changeWorkingDirectory(destination)) {
                    throw new IOException(
                            "Não foi possível acessar a pasta " + destination);
                }

                try (InputStream input =
                        getContentResolver().openInputStream(uri)) {

                    if (input == null) {
                        throw new IOException("Não foi possível abrir o arquivo.");
                    }

                    boolean uploaded = ftp.storeFile(name, input);

                    if (!uploaded) {
                        throw new IOException(
                                "Falha no envio: " + ftp.getReplyString());
                    }
                }

                sendStatus("Arquivo enviado com sucesso!");

            } catch (Exception e) {
                sendStatus("Erro: " + e.getMessage());

            } finally {
                try {
                    if (ftp.isConnected()) {
                        ftp.logout();
                        ftp.disconnect();
                    }
                } catch (IOException ignored) {
                }
            }
        });
    }

    private void sendStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
            }
            
