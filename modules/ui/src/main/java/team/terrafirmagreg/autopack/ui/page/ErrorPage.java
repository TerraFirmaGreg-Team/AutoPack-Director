package team.terrafirmagreg.autopack.ui.page;

import java.awt.*;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import javax.swing.*;
import team.terrafirmagreg.autopack.locale.Messages;
import team.terrafirmagreg.autopack.locale.UserErrors;
import team.terrafirmagreg.autopack.manage.InstallError;

public class ErrorPage extends JPanel {
    private final CountDownLatch closeLatch = new CountDownLatch(1);

    public ErrorPage(Collection<InstallError> errors) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel(Messages.get("autopack.error.page.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        Color errorForeground = UIManager.getColor("Label.errorForeground");
        title.setForeground(errorForeground != null ? errorForeground : new Color(180, 30, 30));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setBackground(UIManager.getColor("Panel.background"));

        StringBuilder sb = new StringBuilder(Messages.get("autopack.error.page.intro")).append("\n\n");
        for (InstallError error : errors) {
            String bullet = error.getLevel() == Level.SEVERE
                    ? Messages.get("autopack.error.page.severe")
                    : Messages.get("autopack.error.page.warning");
            sb.append(bullet).append(' ').append(error.getMessage()).append('\n');
            String detail = UserErrors.formatDetail(error);
            if (detail != null) {
                sb.append("        ")
                        .append(Messages.get("autopack.error.page.detail", detail))
                        .append('\n');
            }
            sb.append('\n');
        }
        text.setText(sb.toString());
        text.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(500, 180));
        add(scroll, BorderLayout.CENTER);

        JButton closeButton = new JButton(Messages.get("autopack.error.page.close"));
        closeButton.setToolTipText(Messages.get("autopack.error.page.close.tooltip"));
        closeButton.addActionListener(e -> closeLatch.countDown());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void waitForClose() throws InterruptedException {
        closeLatch.await();
    }
}
