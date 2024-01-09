package net.jan.moddirector.core.ui;

import net.jan.moddirector.core.configuration.modpack.ModpackConfiguration;
import net.minecraftforge.fml.exit.QualifiedExit;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;

public class RestartRequiredDialog extends JDialog {
    public RestartRequiredDialog(ModpackConfiguration configuration) throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(1);
        JFrame frame = new JFrame(configuration.packName());
        JButton button = new JButton("Entendido");
        button.addActionListener(e -> {
            if(configuration.refuseLaunch()) {
                QualifiedExit.exit(0);
            }
            latch.countDown();
            frame.dispose();
        });
        String text = "Instalação concluída, por favor reinicie seu jogo para completar a inicialização.";
        JLabel label = new JLabel(this.asHtml(text), SwingConstants.CENTER);
        frame.add(label, BorderLayout.CENTER);
        frame.add(button, BorderLayout.SOUTH);
        frame.setSize(330, 120);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        latch.await();
    }

    private String asHtml(String content) {
        return "<html>" + content + "</html>";
    }
}
