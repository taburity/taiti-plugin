package br.edu.ufape.taiti.gui.taskbar;

import br.edu.ufape.taiti.service.Task;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Painel exibido dentro de ImpactedFiles com a previsão de arquivos de
 * código-fonte que podem ser impactados por conflito, com base na
 * interseção de arquivos calculada pelo ConflictAnalyzer
 * (br.ufpe.cin.tan.conflict) entre a task selecionada e as demais tasks
 * em andamento (PlannedTask / ITest.getFiles()).
 *
 * Possui dois estados, alternados via CardLayout:
 *  - EMPTY: nenhum arquivo impactado calculado ainda (sem testes vinculados,
 *           sem iTesk, ou sem nenhuma interseção de arquivos encontrada).
 *  - WITH_FILES: lista de arquivos impactados, cada um com pontinhos
 *                coloridos representando as tasks conflitantes
 *                relacionadas a ele (cor = nível de risco daquela task).
 *
 * Uso (dentro do TaskBarGUI, por exemplo no showTaskDetails):
 *
 *   impactedFilesPanel.showEmpty();
 *   // ou
 *   impactedFilesPanel.showImpactedFiles(impactedFileList);
 *
 * onde impactedFileList é uma List<ImpactedFile> já calculada pelo
 * TaskBarGUI a partir do ConflictAnalyzer (ver buildImpactedFiles(...)
 * no TaskBarGUI).
 */
public class ImpactedFilesPanel extends JPanel {

    private static final String CARD_EMPTY = "EMPTY";
    private static final String CARD_WITH_FILES = "WITH_FILES";

    private static final Color COLOR_LOW = new Color(30, 150, 70);
    private static final Color COLOR_MEDIUM = new Color(215, 165, 25);
    private static final Color COLOR_HIGH = new Color(190, 45, 45);
    private static final Color COLOR_EMPTY_DOT = new Color(90, 90, 90);

    private static final Color COLOR_NEUTRAL_TEXT = new Color(220, 220, 220);
    private static final Color COLOR_MUTED_TEXT = new Color(170, 170, 170);
    private static final Color COLOR_RUBY_BADGE = new Color(60, 90, 150);

    private static final int MAX_DOTS = 4;

    private final CardLayout cardLayout;

    // --- Card: vazio ---
    private JPanel emptyCard;

    // --- Card: com arquivos ---
    private JPanel withFilesCard;
    private JLabel titleLabel;
    private JLabel countBadgeLabel;
    private JPanel fileListPanel;
    private JScrollPane fileScrollPane;

    public ImpactedFilesPanel() {
        cardLayout = new CardLayout();
        setLayout(cardLayout);
        setOpaque(true);

        emptyCard = buildEmptyCard();
        withFilesCard = buildWithFilesCard();

        add(emptyCard, CARD_EMPTY);
        add(withFilesCard, CARD_WITH_FILES);

        cardLayout.show(this, CARD_EMPTY);
    }

    /**
     * Mostra o estado vazio: nenhum arquivo impactado para exibir.
     */
    public void showEmpty() {
        cardLayout.show(this, CARD_EMPTY);
    }

    /**
     * Mostra a lista de arquivos impactados já calculada (ver
     * TaskBarGUI.buildImpactedFiles(...)).
     */
    public void showImpactedFiles(List<ImpactedFile> impactedFiles) {
        if (impactedFiles == null || impactedFiles.isEmpty()) {
            showEmpty();
            return;
        }

        countBadgeLabel.setText(impactedFiles.size() + (impactedFiles.size() == 1 ? " arquivo" : " arquivos"));

        fileListPanel.removeAll();
        for (ImpactedFile file : impactedFiles) {
            fileListPanel.add(buildFileRow(file));
        }
        fileListPanel.revalidate();
        fileListPanel.repaint();

        cardLayout.show(this, CARD_WITH_FILES);
    }

    // ----------------------------------------------------------------
    // Construção dos cards
    // ----------------------------------------------------------------

    private JPanel buildEmptyCard() {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 4));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);

        JLabel title = new JLabel("Impacted files (forecast)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setForeground(COLOR_NEUTRAL_TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel body = new JLabel(wrapHtml(
                "The potentially impacted files will be displayed here after the tests are linked."
        ));
        body.setForeground(COLOR_MUTED_TEXT);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        textPanel.add(title);
        textPanel.add(body);

        JLabel folderIcon = new JLabel(new FolderIcon(40, 32, new Color(120, 120, 120)));
        folderIcon.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        card.add(textPanel, BorderLayout.CENTER);
        card.add(folderIcon, BorderLayout.EAST);

        return card;
    }

    private JPanel buildWithFilesCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleLabel = new JLabel("Impacted files (forecast)");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(COLOR_NEUTRAL_TEXT);

        countBadgeLabel = new JLabel("0 files");
        countBadgeLabel.setOpaque(true);
        countBadgeLabel.setBackground(new Color(45, 50, 65));
        countBadgeLabel.setForeground(new Color(180, 195, 230));
        countBadgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        headerRow.add(titleLabel, BorderLayout.WEST);
        headerRow.add(countBadgeLabel, BorderLayout.EAST);

        JLabel relatedToLabel = new JLabel("Related to");
        relatedToLabel.setForeground(COLOR_MUTED_TEXT);
        Font relFont = relatedToLabel.getFont();
        relatedToLabel.setFont(relFont.deriveFont(relFont.getSize2D() - 1f));
        relatedToLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel relatedToRow = new JPanel(new BorderLayout());
        relatedToRow.setOpaque(false);
        relatedToRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        relatedToRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        relatedToRow.add(relatedToLabel, BorderLayout.EAST);

        fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setOpaque(false);
        fileListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        fileScrollPane = new JScrollPane(fileListPanel);
        fileScrollPane.setBorder(null);
        fileScrollPane.setOpaque(false);
        fileScrollPane.getViewport().setOpaque(false);
        fileScrollPane.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        fileScrollPane.setVerticalScrollBarPolicy(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        /*
         * Altura máxima da área de arquivos.
         * Ajuste conforme necessário.
         */
        fileScrollPane.setPreferredSize(new Dimension(10, 180));
        fileScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        fileScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(headerRow);
        card.add(relatedToRow);
        card.add(fileScrollPane);

        return card;
    }

    private JPanel buildFileRow(ImpactedFile file) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftPanel.setOpaque(false);

        leftPanel.add(buildFileTypeBadge(file.getPath()));

        JLabel pathLabel = new JLabel(displayPath(file.getPath()));
        pathLabel.setForeground(COLOR_NEUTRAL_TEXT);
        leftPanel.add(pathLabel);

        JPanel dotsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        dotsPanel.setOpaque(false);

        List<Task> relatedTasks = file.getRelatedTasks();
        int dotsToShow = Math.min(relatedTasks.size(), MAX_DOTS);
        for (int i = 0; i < dotsToShow; i++) {
            dotsPanel.add(new DotIcon(colorForTask(relatedTasks.get(i))));
        }
        for (int i = dotsToShow; i < MAX_DOTS; i++) {
            dotsPanel.add(new DotIcon(COLOR_EMPTY_DOT));
        }
        if (relatedTasks.size() > MAX_DOTS) {
            JLabel extra = new JLabel("+" + (relatedTasks.size() - MAX_DOTS));
            extra.setForeground(COLOR_MUTED_TEXT);
            Font f = extra.getFont();
            extra.setFont(f.deriveFont(f.getSize2D() - 1f));
            dotsPanel.add(extra);
        }

        row.add(leftPanel, BorderLayout.WEST);
        row.add(dotsPanel, BorderLayout.EAST);

        return row;
    }

    private JLabel buildFileTypeBadge(String path) {
        boolean isRuby = path != null && path.toLowerCase().endsWith(".rb");

        JLabel badge = new JLabel(isRuby ? "rb" : "•");
        badge.setOpaque(true);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setPreferredSize(new Dimension(26, 18));
        Font f = badge.getFont();
        badge.setFont(f.deriveFont(Font.BOLD, 10f));

        if (isRuby) {
            badge.setBackground(COLOR_RUBY_BADGE);
            badge.setForeground(new Color(200, 210, 255));
        } else {
            badge.setBackground(new Color(70, 70, 70));
            badge.setForeground(new Color(200, 200, 200));
        }

        return badge;
    }

    private Color colorForTask(Task task) {
        if (task == null) return COLOR_EMPTY_DOT;
        double risk = task.getConflictRate();
        if (risk < 0) return COLOR_EMPTY_DOT;
        if (risk >= 70) return COLOR_HIGH;
        if (risk >= 40) return COLOR_MEDIUM;
        return COLOR_LOW;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static String wrapHtml(String text) {
        return "<html><div style='width: 260px;'>" + text + "</div></html>";
    }

    /**
     * Reduz um caminho absoluto para um caminho relativo mais legível,
     * tentando começar a exibição a partir de pastas conhecidas de projeto
     * (app/, spec/, config/, lib/, test/). Se não encontrar, mostra o
     * caminho como veio.
     */
    private static String displayPath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return "unknown";
        }

        String normalized = absolutePath.replace('\\', '/');
        String[] knownRoots = {"/app/", "/spec/", "/config/", "/lib/", "/test/"};
        for (String root : knownRoots) {
            int idx = normalized.lastIndexOf(root);
            if (idx >= 0) {
                return normalized.substring(idx + 1); // remove a barra inicial
            }
        }

        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    /**
     * Representa um arquivo impactado e as tasks conflitantes relacionadas
     * a ele. Construído pelo TaskBarGUI a partir do ConflictAnalyzer.
     */
    public static class ImpactedFile {
        private final String path;
        private final List<Task> relatedTasks;

        public ImpactedFile(String path, List<Task> relatedTasks) {
            this.path = path;
            this.relatedTasks = relatedTasks != null ? relatedTasks : new ArrayList<>();
        }

        public String getPath() {
            return path;
        }

        public List<Task> getRelatedTasks() {
            return relatedTasks;
        }
    }

    /**
     * Ícone simples de pontinho colorido (usado nos indicadores de risco
     * "Relacionado a").
     */
    private static class DotIcon extends JComponent {
        private final Color color;

        DotIcon(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(10, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /**
     * Ícone simples de pasta (usado no estado vazio).
     */
    private static class FolderIcon implements Icon {
        private final int width;
        private final int height;
        private final Color color;

        FolderIcon(int width, int height, Color color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f));

            int tabWidth = width / 3;
            int bodyTop = y + height / 4;

            g2.drawLine(x, bodyTop, x + tabWidth, bodyTop);
            g2.drawLine(x + tabWidth, bodyTop, x + tabWidth + 6, y);
            g2.drawLine(x + tabWidth + 6, y, x + width - 4, y);
            g2.drawLine(x + width - 4, y, x + width, bodyTop);

            g2.drawRect(x, bodyTop, width, height - (bodyTop - y));

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
