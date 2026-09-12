package team.terrafirmagreg.autopack.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import javax.swing.*;
import team.terrafirmagreg.autopack.locale.Messages;

public class WarningDisplay {
    public static void show(String body) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        showDialog(Messages.get("autopack.warning.title"), body, Messages.get("autopack.warning.ok"));
    }

    private static void showDialog(String title, String message, String okLabel) {
        CountDownLatch latch = new CountDownLatch(1);

        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                latch.countDown();
            }
        });

        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setOpaque(false);
        textArea.setFocusable(false);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(null);
        scrollPane.setPreferredSize(new Dimension(600, Math.min(message.split("\n").length * 20, 500)));

        JButton button = new JButton(okLabel);
        button.addActionListener(e -> {
            frame.dispose();
            latch.countDown();
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(button, BorderLayout.SOUTH);

        frame.getContentPane().add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        try {
            latch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
