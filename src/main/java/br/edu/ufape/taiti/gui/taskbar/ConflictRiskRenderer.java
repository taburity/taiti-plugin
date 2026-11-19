package br.edu.ufape.taiti.gui.taskbar;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ConflictRiskRenderer extends JPanel implements TableCellRenderer {

    private final RoundedLabel levelLabel;
    private final RiskBar progressBar;
    private final JLabel percentageLabel;
    private final JPanel centerPanel;

    public ConflictRiskRenderer() {

        setLayout(new BorderLayout());
        setOpaque(true);

        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

        levelLabel = new RoundedLabel();
        levelLabel.setForeground(Color.WHITE);
        levelLabel.setPreferredSize(new Dimension(65, 22));

        progressBar = new RiskBar();

        percentageLabel = new JLabel();
        percentageLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        centerPanel = new JPanel(new BorderLayout(8, 0));
        centerPanel.setBorder(
                BorderFactory.createEmptyBorder(0, 15, 0, 0)
        );
        centerPanel.setOpaque(false);

        centerPanel.add(progressBar, BorderLayout.CENTER);
        centerPanel.add(percentageLabel, BorderLayout.EAST);

        JPanel badgePanel = new JPanel(new GridBagLayout());
        badgePanel.setOpaque(false);
        badgePanel.add(levelLabel);

        add(badgePanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else {
            setBackground(table.getBackground());
        }

        double risk = value instanceof Number
                ? ((Number) value).doubleValue()
                : -1;

        if (risk < 0) {

            levelLabel.setText("—");
            levelLabel.setBackground(new Color(80, 80, 80));

            progressBar.setVisible(false);

            percentageLabel.setText(
                    "<html><center>Link tests to<br>calculate risk</center></html>"
            );

            percentageLabel.setForeground(new Color(180, 180, 180));

            return this;
        }

        progressBar.setVisible(true);

        percentageLabel.setForeground(
                isSelected
                        ? table.getSelectionForeground()
                        : table.getForeground()
        );

        percentageLabel.setText((int) risk + "%");

        progressBar.setValue((int) risk);

        if (risk >= 70) {

            levelLabel.setText("HIGH");

            levelLabel.setBackground(new Color(190, 45, 45));

            progressBar.setFillColor(new Color(190, 45, 45));

        } else if (risk >= 40) {

            levelLabel.setText("MEDIUM");

            levelLabel.setBackground(new Color(215, 165, 25));

            progressBar.setFillColor(new Color(215, 165, 25));

        } else {

            levelLabel.setText("LOW");

            levelLabel.setBackground(new Color(30, 150, 70));

            progressBar.setFillColor(new Color(30, 150, 70));
        }

        return this;
    }

    /**
     * Badge arredondado
     */
    private static class RoundedLabel extends JLabel {

        public RoundedLabel() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(getBackground());

            g2.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    12,
                    12
            );

            super.paintComponent(g2);

            g2.dispose();
        }
    }

    /**
     * Barra de risco customizada
     */
    private static class RiskBar extends JComponent {

        private int value;
        private Color fillColor = new Color(255, 80, 80);

        public void setValue(int value) {
            this.value = Math.max(0, Math.min(100, value));
            repaint();
        }

        public void setFillColor(Color fillColor) {
            this.fillColor = fillColor;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 18);
        }

        @Override
        protected void paintComponent(Graphics g) {

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int width = getWidth();

            // Altura REAL da barra
            int barHeight = 4;

            // Centraliza verticalmente
            int y = (getHeight() - barHeight) / 2;

            // Fundo
            g2.setColor(new Color(60, 60, 60));
            g2.fillRoundRect(
                    0,
                    y,
                    width,
                    barHeight,
                    barHeight,
                    barHeight
            );

            // Preenchimento
            int fillWidth = (int) (width * (value / 100.0));

            g2.setColor(fillColor);
            g2.fillRoundRect(
                    0,
                    y,
                    fillWidth,
                    barHeight,
                    barHeight,
                    barHeight
            );

            g2.dispose();
        }
    }
}