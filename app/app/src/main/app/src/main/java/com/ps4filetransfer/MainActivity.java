

package com.ps4filetransfer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

public class MainActivity extends Activity {

    EditText ip, porta, usuario, senha, caminho;
    TextView status;
    Button escolher, enviar;
    Uri arquivoSelecionado;
    String nomeArquivo = "";
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout tela = new LinearLayout(this);
        tela.setOrientation(LinearLayout.VERTICAL);
        tela.setPadding(30, 30, 30, 30);
        tela.setBackgroundColor(0xFF151515);

        TextView titulo = new TextView(this);
        titulo.setText("PS4 FILE TRANSFER");
        titulo.setTextSize(25);
        titulo.setTextColor(0xFFFFFFFF);
        tela.addView(titulo);

        ip = campo(tela, "IP do PS4");
        porta = campo(tela, "Porta FTP (2121)");
        porta.setText("2121");
        usuario = campo(tela, "Usuário FTP");
        usuario.setText("anonymous");
        senha = campo(tela, "Senha FTP");
        caminho = campo(tela, "Pasta de destino no PS4");
        caminho.setText("/data");

        escolher = new Button(this);
        escolher.setText("ESCOLHER ARQUIVO");
        tela.addView(escolher);

        enviar = new Button(this);
        enviar.setText("ENVIAR PARA O PS4");
        tela.addView(enviar);

        status = new TextView(this);
        status.setTextColor(0xFFFFFFFF);
        status.setText("Informe os dados do PS4.");
        status.setPadding(0, 25, 0, 0);
        tela.addView(status);

        setContentView(tela);

        escolher.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 100);
        });

        enviar.setOnClickListener(v -> enviarArquivo());
    }

    private EditText campo(LinearLayout tela, String dica) {
        EditText campo = new EditText(this);
        campo.setSingleLine(true);
        campo.setHint(dica);
        campo.setHintTextColor(0xFFAAAAAA);
        campo.setTextColor(0xFFFFFFFF);
        tela.addView(campo);
        return campo;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            arquivoSelecionado = data.getData();

            String caminhoNome = arquivoSelecionado.getLastPathSegment();
            if (caminhoNome != null) {
                int pos = caminhoNome.lastIndexOf('/');
                nomeArquivo = caminhoNome.substring(pos + 1);
            }

            status.setText("Arquivo selecionado: " + nomeArquivo);
        }
    }

    private void enviarArquivo() {
        if (arquivoSelecionado == null) {
            status.setText("Escolha um arquivo primeiro.");
            return;
        }

        final String endereco = ip.getText().toString().trim();
        final String portaTexto = porta.getText().toString().trim();
        final String login = usuario.getText().toString().trim();
        final String senhaTexto = senha.getText().toString();
        final String destino = caminho.getText().toString().trim();

        if (endereco.isEmpty() || portaTexto.isEmpty() || destino.isEmpty()) {
            status.setText("Preencha IP, porta e pasta de destino.");
            return;
        }

        final Uri uri = arquivoSelecionado;
        final String nome = nomeArquivo;

        enviar.setEnabled(false);
        status.setText("Conectando ao PS4...");

        executor.execute(() -> {
            FTPClient ftp = new FTPClient();

            try {
                int numeroPorta = Integer.parseInt(portaTexto);
                ftp.connect(endereco, numeroPorta);

                if (!ftp.login(login, senhaTexto)) {
                    throw new Exception("Falha no login FTP.");
                }

                ftp.enterLocalPassiveMode();
                ftp.setFileType(FTP.BINARY_FILE_TYPE);

                if (!ftp.changeWorkingDirectory(destino)) {
                    throw new Exception("Pasta de destino não encontrada: " + destino);
                }

                InputStream entrada = getContentResolver().openInputStream(uri);

                if (entrada == null) {
                    throw new Exception("Não foi possível abrir o arquivo.");
                }

                boolean sucesso;
                try (InputStream arquivo = entrada) {
                    sucesso = ftp.storeFile(nome, arquivo);
                }

                if (!sucesso) {
                    throw new Exception("O PS4 recusou o envio do arquivo.");
                }

                runOnUiThread(() -> status.setText("Arquivo enviado com sucesso!"));

            } catch (Exception e) {
                String erro = e.getMessage();
                runOnUiThread(() -> status.setText("Erro: " + erro));

            } finally {
                try {
                    if (ftp.isConnected()) {
                        ftp.logout();
                        ftp.disconnect();
                    }
                } catch (Exception ignored) {
                }

                runOnUiThread(() -> enviar.setEnabled(true));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
