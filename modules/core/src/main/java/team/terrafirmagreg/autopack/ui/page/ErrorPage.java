package team.terrafirmagreg.autopack.ui.page;

import team.terrafirmagreg.autopack.core.manage.InstallError;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

public class ErrorPage extends JPanel {
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    public ErrorPage(Collection<InstallError> errors) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Installation Failed");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(UIManager.getColor("nb.errorForeground") != null
            ? UIManager.getColor("nb.errorForeground") : new Color(180, 30, 30));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setBackground(UIManager.getColor("Panel.background"));

        StringBuilder sb = new StringBuilder(
            "The following errors occurred and the installation could not complete.\n"
                + "Please check your internet connection or contact the modpack author.\n\n");
        for (InstallError error : errors) {
            String bullet = error.getLevel() == Level.SEVERE ? "[ERROR]" : "[WARN]";
            sb.append(bullet).append(' ').append(error.getMessage()).append('\n');
            Throwable cause = error.getException() != null ? error.getException().getCause() : null;
            if (cause != null && cause.getMessage() != null) {
                sb.append("        Caused by: ").append(cause.getMessage()).append('\n');
            }
            sb.append('\n');
        }
        text.setText(sb.toString());
        text.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(500, 180));
        add(scroll, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> closeLatch.countDown());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void waitForClose() throws InterruptedException {
        closeLatch.await();
    }
}
