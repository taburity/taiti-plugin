package br.edu.ufape.taiti.gui.taskbar;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Painel exibido dentro de RiskDetails (PanelRisk) com os detalhes de risco
 * de conflito da task selecionada.
 *
 * Possui dois estados, alternados via CardLayout:
 *  - NO_TEST: a task não possui testes/cenários vinculados (alerta amarelo).
 *  - RISK: a task possui testes vinculados e um risco de conflito calculado
 *          (verde / amarelo / vermelho, conforme o percentual).
 *
 * Uso (dentro do TaskBarGUI, por exemplo no showTaskDetails):
 *
 *   riskDetailsPanel.showNoTest();
 *   // ou
 *   riskDetailsPanel.showRisk(conflictRate); // conflictRate de 0 a 100
 *
 * Este componente é autocontido: basta adicioná-lo dentro do JPanel
 * "PanelRisk" criado no UI Designer (ver passo a passo).
 */
public class RiskDetailsPanel extends JPanel {

    private static final String CARD_NO_TEST = "NO_TEST";
    private static final String CARD_RISK = "RISK";

    // Cores base (mesma paleta do ConflictRiskRenderer)
    private static final Color COLOR_LOW = new Color(30, 150, 70);
    private static final Color COLOR_MEDIUM = new Color(215, 165, 25);
    private static final Color COLOR_HIGH = new Color(190, 45, 45);
    private static final Color COLOR_WARNING = new Color(215, 165, 25);

    private final CardLayout cardLayout;

    // --- Card: Nenhum teste vinculado ---
    private JPanel noTestCard;

    // --- Card: Risco calculado ---
    private JPanel riskCard;
    private JLabel riskTitleLabel;
    private JLabel riskPercentageLabel;
    private RiskBar riskBar;
    private JLabel riskDescriptionLabel;

    public RiskDetailsPanel() {
        cardLayout = new CardLayout();
        setLayout(cardLayout);
        setOpaque(true);

        noTestCard = buildNoTestCard();
        riskCard = buildRiskCard();

        add(noTestCard, CARD_NO_TEST);
        add(riskCard, CARD_RISK);

        cardLayout.show(this, CARD_NO_TEST);
    }

    /**
     * Mostra o estado de alerta: nenhum teste vinculado à task.
     */
    public void showNoTest() {
        System.out.println("[DEBUG] RiskDetailsPanel.showNoTest() chamado");
        cardLayout.show(this, CARD_NO_TEST);
    }

    /**
     * Mostra o estado de risco calculado.
     *
     * @param conflictRate percentual de risco de conflito (0 a 100)
     */
    public void showRisk(double conflictRate) {
        System.out.println("[DEBUG] RiskDetailsPanel.showRisk(" + conflictRate + ") chamado");
        int risk = (int) Math.max(0, Math.min(100, conflictRate));

        riskPercentageLabel.setText(risk + "%");
        riskBar.setValue(risk);

        Color levelColor;
        String levelText;
        String description;

        if (risk >= 70) {
            levelColor = COLOR_HIGH;
            levelText = "ALTO";
            description = "There is a high chance of conflict with ongoing tasks due to shared files.";
        } else if (risk >= 40) {
            levelColor = COLOR_MEDIUM;
            levelText = "MÉDIO";
            description = "There is a moderate chance of conflict with ongoing tasks due to shared files.";
        } else {
            levelColor = COLOR_LOW;
            levelText = "BAIXO";
            description = "Low chance of conflict with tasks currently underway.";
        }

        riskTitleLabel.setText("Risk of Conflict: " + levelText);
        riskTitleLabel.setForeground(levelColor);
        riskBar.setFillColor(levelColor);
        riskDescriptionLabel.setText(wrapHtml(description));

        // Fundo do card muda sutilmente conforme o nível (mistura com o fundo escuro base)
        riskCard.setBackground(blendWithBackground(levelColor));

        cardLayout.show(this, CARD_RISK);
    }

    // ----------------------------------------------------------------
    // Construção dos cards
    // ----------------------------------------------------------------

    private JPanel buildNoTestCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_WARNING, 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setBackground(blendWithBackground(COLOR_WARNING));

        JLabel warningTitle = new JLabel("\u26A0 No linked tests");
        warningTitle.setFont(warningTitle.getFont().deriveFont(Font.BOLD, 13f));
        warningTitle.setForeground(COLOR_WARNING);
        warningTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel warningBody = new JLabel(wrapHtml(
                "To calculate the risk of conflict, you need to link the Cucumber tests that validate this task."
        ));
        warningBody.setForeground(new Color(220, 220, 220));
        warningBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        warningBody.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));

        JPanel hintBox = new JPanel();
        hintBox.setLayout(new BoxLayout(hintBox, BoxLayout.Y_AXIS));
        hintBox.setBorder(BorderFactory.createCompoundBorder(
                createDashedBorder(COLOR_WARNING),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        hintBox.setOpaque(false);
        hintBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hintTitle = new JLabel("\uD83D\uDDB1 DOUBLE-CLICK ON THE TASK");
        hintTitle.setFont(hintTitle.getFont().deriveFont(Font.BOLD, 11f));
        hintTitle.setForeground(COLOR_WARNING);
        hintTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hintSubtitle = new JLabel("to link tests");
        hintSubtitle.setForeground(new Color(220, 220, 220));
        hintSubtitle.setBorder(BorderFactory.createEmptyBorder(1, 0, 4, 0));
        hintSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hintDetail = new JLabel(wrapHtml(
                "This will open the screen for selecting .feature files and test scenarios."
        ));
        hintDetail.setForeground(new Color(190, 190, 190));
        Font detailFont = hintDetail.getFont();
        hintDetail.setFont(detailFont.deriveFont(detailFont.getSize2D() - 1f));
        hintDetail.setAlignmentX(Component.LEFT_ALIGNMENT);

        hintBox.add(hintTitle);
        hintBox.add(hintSubtitle);
        hintBox.add(hintDetail);

        card.add(warningTitle);
        card.add(warningBody);
        card.add(hintBox);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JPanel buildRiskCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_HIGH, 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        card.setBackground(blendWithBackground(COLOR_HIGH));

        riskTitleLabel = new JLabel("Risk of Conflict: HIGH");
        riskTitleLabel.setFont(riskTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        riskTitleLabel.setForeground(COLOR_HIGH);
        riskTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        riskTitleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        riskPercentageLabel = new JLabel("0%");
        riskPercentageLabel.setFont(riskPercentageLabel.getFont().deriveFont(Font.BOLD, 26f));
        riskPercentageLabel.setForeground(Color.WHITE);
        riskPercentageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        riskBar = new RiskBar();
        riskBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        riskBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        riskBar.setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));

        riskDescriptionLabel = new JLabel();
        riskDescriptionLabel.setForeground(new Color(220, 220, 220));
        riskDescriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(riskTitleLabel);
        card.add(riskPercentageLabel);
        card.add(riskBar);
        card.add(riskDescriptionLabel);
        card.add(Box.createVerticalGlue());

        return card;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static String wrapHtml(String text) {
        return "<html><div style='width: 220px;'>" + text + "</div></html>";
    }

    private static Border createDashedBorder(Color color) {
        return BorderFactory.createDashedBorder(color, 4, 4);
    }

    /**
     * Mistura a cor de destaque com o fundo escuro padrão, para criar
     * um tom sutil (não saturado) por trás do card.
     */
    private static Color blendWithBackground(Color accent) {
        Color base = new Color(30, 28, 26); // fundo escuro de referência
        int r = (int) (base.getRed() * 0.85 + accent.getRed() * 0.15);
        int g = (int) (base.getGreen() * 0.85 + accent.getGreen() * 0.15);
        int b = (int) (base.getBlue() * 0.85 + accent.getBlue() * 0.15);
        return new Color(r, g, b);
    }

    /**
     * Barra de progresso simples (cópia independente da usada em
     * ConflictRiskRenderer, mantida privada e específica deste painel).
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
            return new Dimension(220, 8);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int h = getHeight();
            int w = getWidth();

            g2.setColor(new Color(70, 70, 70));
            g2.fillRoundRect(0, 0, w, h, h, h);

            int fillWidth = (int) (w * (value / 100.0));
            g2.setColor(fillColor);
            g2.fillRoundRect(0, 0, fillWidth, h, h, h);

            g2.dispose();
        }
    }
}
