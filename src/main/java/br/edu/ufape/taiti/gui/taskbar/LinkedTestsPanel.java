package br.edu.ufape.taiti.gui.taskbar;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Painel exibido dentro de LinkedTests com o resumo dos testes (cenários
 * Cucumber) vinculados à task selecionada.
 *
 * Possui dois estados, alternados via CardLayout:
 *  - NO_TESTS: nenhum teste vinculado à task.
 *  - WITH_TESTS: lista de arquivos .feature vinculados, agrupados, com a
 *                contagem de cenários por arquivo, e um botão para
 *                gerenciar (abrir o TaitiDialog).
 *
 * Uso (dentro do TaskBarGUI, por exemplo no showTaskDetails):
 *
 *   linkedTestsPanel.showNoTests();
 *   // ou
 *   linkedTestsPanel.showTests(task.getScenarios());
 *
 * O clique no botão "Gerenciar testes" é repassado para um Runnable
 * configurado via setOnManageTestsClicked(...), para que o TaskBarGUI
 * decida o que fazer (ex: abrir o TaitiDialog com a task selecionada).
 */
public class LinkedTestsPanel extends JPanel {

    private static final String CARD_NO_TESTS = "NO_TESTS";
    private static final String CARD_WITH_TESTS = "WITH_TESTS";

    private static final Color COLOR_SUCCESS = new Color(60, 179, 113);
    private static final Color COLOR_NEUTRAL_TEXT = new Color(220, 220, 220);
    private static final Color COLOR_MUTED_TEXT = new Color(170, 170, 170);

    private final CardLayout cardLayout;

    // --- Card: nenhum teste vinculado ---
    private JPanel noTestsCard;

    // --- Card: testes vinculados ---
    private JPanel withTestsCard;
    private JLabel titleLabel;
    private JPanel fileListPanel;
    private JButton manageTestsButton;

    private Runnable onManageTestsClicked;

    public LinkedTestsPanel() {
        cardLayout = new CardLayout();
        setLayout(cardLayout);
        setOpaque(true);

        noTestsCard = buildNoTestsCard();
        withTestsCard = buildWithTestsCard();

        add(noTestsCard, CARD_NO_TESTS);
        add(withTestsCard, CARD_WITH_TESTS);

        cardLayout.show(this, CARD_NO_TESTS);
    }

    /**
     * Define a ação executada quando o botão "Gerenciar testes" é clicado.
     * Normalmente, o TaskBarGUI deve abrir o TaitiDialog para a task atual.
     */
    public void setOnManageTestsClicked(Runnable callback) {
        this.onManageTestsClicked = callback;
    }

    /**
     * Mostra o estado de "nenhum teste vinculado".
     */
    public void showNoTests() {
        cardLayout.show(this, CARD_NO_TESTS);
    }

    /**
     * Mostra o estado de "testes vinculados", agrupando os cenários por
     * caminho de arquivo .feature e exibindo a contagem de cenários por
     * arquivo.
     *
     * @param scenarios lista de mapas com "path" (String) e "lines" (List de Integer),
     *                   no mesmo formato retornado por Task.getScenarios().
     */
    public void showTests(List<LinkedHashMap<String, Serializable>> scenarios) {
        // Agrupa por path, contando quantas "lines" (cenários) cada arquivo tem.
        LinkedHashMap<String, Integer> countByFile = new LinkedHashMap<>();

        if (scenarios != null) {
            for (LinkedHashMap<String, Serializable> scenario : scenarios) {
                Object pathObj = scenario.get("path");
                Object linesObj = scenario.get("lines");

                String path = pathObj != null ? pathObj.toString() : "unknown.feature";
                int lineCount = 0;
                if (linesObj instanceof List) {
                    lineCount = ((List<?>) linesObj).size();
                }

                countByFile.merge(path, Math.max(lineCount, 0), Integer::sum);
            }
        }

        int totalScenarios = countByFile.values().stream().mapToInt(Integer::intValue).sum();

        titleLabel.setText("\u2705 Linked tests (" + totalScenarios + ")");

        fileListPanel.removeAll();
        for (Map.Entry<String, Integer> entry : countByFile.entrySet()) {
            fileListPanel.add(buildFileRow(displayPath(entry.getKey()), entry.getValue()));
        }
        fileListPanel.revalidate();
        fileListPanel.repaint();

        cardLayout.show(this, CARD_WITH_TESTS);
    }

    // ----------------------------------------------------------------
    // Construção dos cards
    // ----------------------------------------------------------------

    private JPanel buildNoTestsCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

        JLabel header = new JLabel("Linked tests");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setForeground(COLOR_SUCCESS);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel body = new JLabel("No tests are linked to this task.");
        body.setForeground(COLOR_NEUTRAL_TEXT);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        card.add(header);
        card.add(body);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JPanel buildWithTestsCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

        titleLabel = new JLabel("Linked tests (0)");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(COLOR_SUCCESS);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);
        fileListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fileListPanel.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 0));

        manageTestsButton = new JButton("Manage tests");
        manageTestsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        manageTestsButton.addActionListener(e -> {
            if (onManageTestsClicked != null) {
                onManageTestsClicked.run();
            }
        });

        card.add(titleLabel);
        card.add(fileListPanel);
        card.add(manageTestsButton);
        card.add(Box.createVerticalGlue());

        return card;
    }

    private JPanel buildFileRow(String displayPath, int scenarioCount) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        String scenarioWord = scenarioCount == 1 ? "scenario" : "scenarios";
        JLabel label = new JLabel("• " + displayPath + " (" + scenarioCount + " " + scenarioWord + ")");
        label.setForeground(COLOR_NEUTRAL_TEXT);

        row.add(label, BorderLayout.CENTER);
        return row;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Reduz um caminho absoluto para um caminho relativo mais legível,
     * tentando começar a exibição a partir da pasta "features" (convenção
     * do Cucumber). Se não encontrar, exibe apenas o nome do arquivo.
     */
    private static String displayPath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "unknown.feature";
        }

        String normalized = absolutePath.replace('\\', '/');
        int featuresIdx = normalized.lastIndexOf("/features/");
        if (featuresIdx >= 0) {
            return normalized.substring(featuresIdx + 1); // mantém "features/..."
        }
        if (normalized.startsWith("features/")) {
            return normalized;
        }

        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
}
