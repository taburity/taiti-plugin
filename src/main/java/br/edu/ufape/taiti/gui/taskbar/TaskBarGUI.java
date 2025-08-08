package br.edu.ufape.taiti.gui.taskbar;

import br.edu.ufape.taiti.exceptions.HttpException; // May need to adapt or replace with TrelloApiException
import br.edu.ufape.taiti.gui.TaitiDialog;
import br.edu.ufape.taiti.gui.conflicts.ConflictsGUI;
import br.edu.ufape.taiti.service.TrelloService; // Changed from PivotalTracker
import br.edu.ufape.taiti.service.Stories;      // This class will need adaptation for Trello
import br.edu.ufape.taiti.service.Task;         // This class will need adaptation for Trello
import br.edu.ufape.taiti.settings.TaitiSettingsState;
import br.ufpe.cin.tan.conflict.ConflictAnalyzer;
import br.ufpe.cin.tan.conflict.PlannedTask; // This might relate to how Tasks are structured

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


public class TaskBarGUI {

    private DefaultTableModel modelo1;
    private DefaultTableModel modelo2;
    private JPanel TaskBar; // Main panel for the taskbar content once loaded
    private JPanel buttonsPanel;
    private JButton refreshButton;
    private JTextField txtSearch;

    private JTable unstartedTable;
    private JTable startedTable;
    private JPanel tables;
    private JPanel content; // The root panel that switches between loading, config, and TaskBar
    private JPanel configPanel; // Panel shown when settings are missing/invalid
    private JLabel messageLabel; // Label within configPanel to show error messages

    private ArrayList<Task> myUnstartedTasksList; // Changed from Stories to Tasks for clarity
    private ArrayList<Task> otherPendingTasksList; // Pending = started + unstarted

    private final Project project;
    static public ConflictAnalyzer conflictAnalyzer; // Consider if this needs changes for Trello data
    private final LoadingScreen loading;

    // Pattern for validating Trello Board URLs (optional, TrelloService might handle ID extraction)
    private static final Pattern TRELLO_BOARD_URL_PATTERN = Pattern.compile("^https?://trello\\.com/b/([a-zA-Z0-9]+)(?:/[^/]+)?$");
    private static final Pattern TRELLO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");


    private JPanel createConfigPanel() {
        configPanel = new JPanel();
        configPanel.setLayout(new BorderLayout());
        configPanel.setName("ConfigPanel");

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        messageLabel = new JLabel("Configure your Trello credentials and Board URL/ID to start.");
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        messageLabel.setForeground(JBColor.RED);

        JButton configButton = new JButton("Open Settings");
        configButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        configButton.addActionListener(e -> {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
                    project, "TAITI"); // Display name of the settings
            // After closing settings, attempt to reload
            ApplicationManager.getApplication().invokeLater(() -> {
                changeJpanel(loading);
                checkSettingsAndLoad(); // Renamed for clarity
            });
        });

        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(messageLabel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(configButton);
        centerPanel.add(Box.createVerticalGlue());

        configPanel.add(centerPanel, BorderLayout.CENTER);
        return configPanel;
    }

    public TaskBarGUI(ToolWindow toolWindow, Project project) {
        this.project = project;
        conflictAnalyzer = new ConflictAnalyzer();

        this.myUnstartedTasksList = new ArrayList<>();
        this.otherPendingTasksList = new ArrayList<>();

        loading = new LoadingScreen();
        loading.setName("LoadingScreen");

        content = new JPanel();
        content.setLayout(new BorderLayout());

        createConfigPanel(); // Initialize configPanel

        // Initial check for settings
        checkSettingsAndLoad();
    }

    private void checkSettingsAndLoad() {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
        settings.retrieveStoredCredentials(project).thenRun(() -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                String trelloApiKey = settings.getTrelloApiKey();
                String trelloServerToken = settings.getTrelloServerToken();
                String trelloBoardUrlOrId = settings.getTrelloBoardUrlOrId();
                String githubURL = settings.getGithubURL(); // Keep GitHub URL if still needed

                // Check if essential Trello settings are present
                if (isBlank(trelloApiKey) || isBlank(trelloServerToken) || isBlank(trelloBoardUrlOrId) || isBlank(githubURL)) {
                    messageLabel.setText("Please configure Trello API Key, Server Token, Board URL/ID, and GitHub URL in settings.");
                    messageLabel.setForeground(JBColor.RED);
                    changeJpanel(configPanel);
                    return;
                }

                // Validate Trello Board URL/ID format (basic)
                if (!isValidTrelloBoardUrlOrId(trelloBoardUrlOrId)) {
                    messageLabel.setText("Invalid Trello Board URL/ID format. Please check settings.");
                    messageLabel.setForeground(JBColor.RED);
                    changeJpanel(configPanel);
                    return;
                }

                TrelloService trelloService = new TrelloService(trelloApiKey, trelloServerToken, trelloBoardUrlOrId, project);
                try {
                    int status = trelloService.checkBoardStatus(); // Assumes TrelloService has this method

                    if (status == 200) {
                        if (TaskBar == null) { // Initialize UI components only once if connection is OK
                            initializeUIComponents(); // Initialize tables, buttons etc.
                        }
                        changeJpanel(loading); // Show loading screen while fetching tasks
                        loadTasksFromTrello(); // Proceed to load tasks
                    } else if (status == 401) { // Unauthorized for Trello
                        messageLabel.setText("Invalid Trello API Key or Server Token. Please check your settings.");
                        messageLabel.setForeground(JBColor.RED);
                        changeJpanel(configPanel);
                    } else if (status == 404) { // Board not found for Trello
                        messageLabel.setText("Trello Board not found or access denied. Please check Board URL/ID and permissions.");
                        messageLabel.setForeground(JBColor.RED);
                        changeJpanel(configPanel);
                    } else {
                        messageLabel.setText("Failed to connect to Trello. Status: " + status + ". Check settings.");
                        messageLabel.setForeground(JBColor.RED);
                        changeJpanel(configPanel);
                    }
                } catch (IOException | InterruptedException e) {
                    messageLabel.setText("Error connecting to Trello: " + e.getMessage());
                    messageLabel.setForeground(JBColor.RED);
                    changeJpanel(configPanel);
                    Thread.currentThread().interrupt();
                }
            });
        }).exceptionally(ex -> {
            ApplicationManager.getApplication().invokeLater(() -> {
                messageLabel.setText("Error retrieving settings: " + ex.getMessage());
                messageLabel.setForeground(JBColor.RED);
                changeJpanel(configPanel);
            });
            return null;
        });
    }
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isValidTrelloBoardUrlOrId(String input) {
        if (isBlank(input)) return false;
        // TrelloService constructor will handle parsing, this is a pre-check
        return TRELLO_BOARD_URL_PATTERN.matcher(input).matches() || TRELLO_ID_PATTERN.matcher(input).matches();
    }


    // Initializes the main UI components (tables, search, buttons)
    private void initializeUIComponents() {
        // TaskBar is the main panel holding tables and search, initialized from the .form file
        // If TaskBar is not null, it means it was already inflated by IntelliJ's UI designer.
        // We just need to ensure its sub-components are configured.
        if (TaskBar == null) {
            // This case should ideally not happen if the .form is correctly linked.
            // For robustness, one might inflate it programmatically or show an error.
            // For now, we assume TaskBar (and its children like unstartedTable, startedTable, txtSearch)
            // are already initialized by the UI designer when TaskBarGUI instance is created.
            // If they are null here, it indicates a problem with the .form binding.
            System.err.println("WARNING: TaskBar panel is null in initializeUIComponents. Creating components programmatically as fallback.");
            // Fallback: create a simple panel to avoid NullPointerExceptions, though UI will be broken.
            TaskBar = new JPanel(new BorderLayout());
            TaskBar.add(new JLabel("Error: UI Components not loaded."), BorderLayout.CENTER);
            TaskBar.setName("TaskBarError");
            // Initialize other components to avoid NPEs, though they won't be functional.
            unstartedTable = new JTable();
            startedTable = new JTable();
            txtSearch = new JTextField();
            refreshButton = new JButton("Refresh");
            buttonsPanel = new JPanel();
            buttonsPanel.add(refreshButton);
            tables = new JPanel(new GridLayout(0,1));
            tables.add(new JScrollPane(unstartedTable));
            tables.add(new JScrollPane(startedTable));
            TaskBar.add(buttonsPanel, BorderLayout.NORTH);
            TaskBar.add(tables, BorderLayout.CENTER);

        } else {
            TaskBar.setName("TaskBar"); // For debugging changeJpanel
        }


        addPlaceHolderStyle(txtSearch);
        myUnstartedTasksList = new ArrayList<>();
        otherPendingTasksList = new ArrayList<>();

        modelo1 = new DefaultTableModel(null, new String[]{"<html><b>My unstarted tasks</b></html>", "<html><b>Conflict Rate</b></html>"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        unstartedTable.setModel(modelo1);
        unstartedTable.getColumnModel().getColumn(1).setMaxWidth(100);
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        unstartedTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);

        modelo2 = new DefaultTableModel(null, new String[]{"<html><b>Potential conflict-inducing tasks</b></html>"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        startedTable.setModel(modelo2);

        refreshButton.addActionListener(e -> {
            changeJpanel(loading);
            refresh();
        });

        txtSearch.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
                filterTable(unstartedTable, modelo1, txtSearch.getText());
                filterTable(startedTable, modelo2, txtSearch.getText());
            }
        });

        txtSearch.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                super.focusGained(e);
                if ("Search by task name".equals(txtSearch.getText())) {
                    txtSearch.setText("");
                    removePlaceHolderStyle(txtSearch);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                if (txtSearch.getText().isEmpty()) {
                    addPlaceHolderStyle(txtSearch);
                    txtSearch.setText("Search by task name");
                }
            }
        });

        // Tooltips and Mouse Listeners (adapted for 'Task' object)
        configureTableMouseListeners();
    }

    private void filterTable(JTable table, DefaultTableModel model, String searchText) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        if (searchText == null || searchText.trim().isEmpty() || "Search by task name".equals(searchText)) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText))); // Case-insensitive
        }
    }


    private void configureTableMouseListeners() {
        unstartedTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = unstartedTable.rowAtPoint(e.getPoint());
                if (row > -1) {
                    int modelRow = unstartedTable.convertRowIndexToModel(row); // Important for sorted tables
                    if (modelRow < myUnstartedTasksList.size()) {
                        Task task = myUnstartedTasksList.get(modelRow);
                        String tooltip = "<html>Task Name: " + task.getName() + "<br>Owner: " + task.getPersonName();
                        if (task.hasScenarios()) {
                            tooltip += "<br>Conflict Rate: " + task.getConflictRate() + "%";
                        } else {
                            tooltip += "<br>Please add tests to calculate the conflict rate.";
                        }
                        tooltip += "</html>";
                        unstartedTable.setToolTipText(tooltip);
                    }
                } else {
                    unstartedTable.setToolTipText(null);
                }
            }
        });

        startedTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = startedTable.rowAtPoint(e.getPoint());
                if (row > -1) {
                    int modelRow = startedTable.convertRowIndexToModel(row);
                    if (modelRow < otherPendingTasksList.size()) {
                        Task task = otherPendingTasksList.get(modelRow);
                        startedTable.setToolTipText("<html>" + task.getName() +
                                "<br>TaskID: #" + task.getId() + // Assuming Task has getId() for Trello card ID
                                "<br>Owner: " + task.getPersonName() + "</html>");
                    }
                } else {
                    startedTable.setToolTipText(null);
                }
            }
        });

        unstartedTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = unstartedTable.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = unstartedTable.convertRowIndexToModel(viewRow);
                    if (modelRow < 0 || modelRow >= myUnstartedTasksList.size()) return;

                    Task task = myUnstartedTasksList.get(modelRow);

                    if (!task.hasScenarios()) {
                        TaitiDialog taitiDialog = new TaitiDialog(project, TaskBarGUI.this, task);
                        taitiDialog.show();
                    } else {
                        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
                        ToolWindow myToolWindow = toolWindowManager.getToolWindow("Conflicts");
                        String text = "Conflict table for task \"" + task.getName() + "\" which contains "
                                + task.getConflictRate() + "% conflict rate.";
                        ConflictsGUI.setLabel(text);
                        ConflictsGUI.fillTable(task, conflictAnalyzer, getOtherPendingTasksList());
                        if (myToolWindow != null) myToolWindow.show(null);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                int viewRow = unstartedTable.rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                int modelRow = unstartedTable.convertRowIndexToModel(viewRow);

                if (modelRow < 0 || modelRow >= myUnstartedTasksList.size()) return;

                unstartedTable.setRowSelectionInterval(viewRow, viewRow); // Select the row for visual feedback
                Task task = myUnstartedTasksList.get(modelRow);

                if (task.hasScenarios()) {
                    JPopupMenu popupMenu = new JPopupMenu();
                    JMenuItem editTestsItem = new JMenuItem("Edit tests");
                    editTestsItem.addActionListener(evt -> {
                        TaitiDialog taitiDialog = new TaitiDialog(project, TaskBarGUI.this, task);
                        // Scenarios should be loaded by TaitiDialog constructor if task.hasScenarios()
                        taitiDialog.show();
                    });
                    popupMenu.add(editTestsItem);

                    JMenuItem removeTestsItem = new JMenuItem("Remove tests");
                    removeTestsItem.addActionListener(evt -> {
                        int result = JOptionPane.showConfirmDialog(
                                TaskBar, // Parent component for the dialog
                                "Are you sure you want to remove tests for this Trello card?",
                                "Confirm Remove Tests",
                                JOptionPane.YES_NO_OPTION);
                        if (result == JOptionPane.YES_OPTION) {
                            changeJpanel(loading);
                            TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
                            settings.retrieveStoredCredentials(project).thenRun(() -> {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    TrelloService trelloSvc = new TrelloService(
                                            settings.getTrelloApiKey(),
                                            settings.getTrelloServerToken(),
                                            settings.getTrelloBoardUrlOrId(),
                                            project);
                                    try {
                                        // Assuming TrelloService has a method like deleteTaitiScenarios
                                        // This method would delete the TAITI comment and related attachment
                                        trelloSvc.deleteTaitiScenarios(String.valueOf(task.getId())); // task.getId() is Trello Card ID
                                        refresh(); // Refresh the task list
                                    } catch (IOException e1) {
                                        e1.printStackTrace();
                                        changeJpanel(TaskBar); // Show TaskBar again on error
                                        JOptionPane.showMessageDialog(content, "Error removing tests: " + e1.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                    }
                                });
                            }).exceptionally(ex -> {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    changeJpanel(TaskBar);
                                    JOptionPane.showMessageDialog(content, "Error retrieving settings for removing tests: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                });
                                return null;
                            });
                        }
                    });
                    popupMenu.add(removeTestsItem);
                    popupMenu.show(unstartedTable, e.getX(), e.getY());
                }
            }
        });
    }


    private void addPlaceHolderStyle(JTextField textField) {
        Font font = textField.getFont();
        font = font.deriveFont(Font.ITALIC);
        textField.setFont(font);
        textField.setForeground(JBColor.GRAY); // Standard placeholder color
    }

    private void removePlaceHolderStyle(JTextField textField) {
        Font font = textField.getFont();
        font = font.deriveFont(Font.PLAIN);
        textField.setFont(font);
        textField.setForeground(UIManager.getColor("TextField.foreground")); // Standard text color
    }

    // Renamed from configTaskList to loadTasksFromTrello for clarity
    private void loadTasksFromTrello() {
        TaitiSettingsState settings = TaitiSettingsState.getInstance(project);
        // Settings should already be retrieved by checkSettingsAndLoad, but good to have them here.
        // No need to call retrieveStoredCredentials again if checkSettingsAndLoad ensures they are loaded.

        // Ensure TrelloService is initialized (it should be if we reached here)
        TrelloService trelloSvc = new TrelloService(
                settings.getTrelloApiKey(),
                settings.getTrelloServerToken(),
                settings.getTrelloBoardUrlOrId(),
                project
        );

        // The Stories class needs to be adapted to use TrelloService
        // and fetch cards from Trello lists, map them to Task objects.
        Stories trelloStories = new Stories(trelloSvc, project, settings.getGithubURL());
        trelloStories.clearLists(); // Assuming this clears internal lists in Stories object

        ProgressManager.getInstance().run(new com.intellij.openapi.progress.Task.Backgroundable(project, "Loading Trello Cards", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // The startList method in Stories needs to be adapted for Trello
                // It should fetch cards from relevant lists (e.g., "My Unstarted", "Other Pending")
                trelloStories.startList(indicator); // This is the core Trello data fetching
            }

            @Override
            public void onFinished() {
                ApplicationManager.getApplication().invokeLater(() -> {
                    // Ensure UI components are initialized before updating
                    ensureUIComponentsInitialized();
                    limparTaskListsInModels(); // Clear JTable models and internal lists

                    // The following logic assumes trelloStories.getMyUnstartedStories() and
                    // trelloStories.getOtherPendingStories() return Task objects populated from Trello.
                    // Conflict analysis logic might need review based on Trello data structure.
                    ArrayList<PlannedTask> othersPlannedTaskArrayList = new ArrayList<>();
                    for (Task othersTask : trelloStories.getOtherPendingTasks()) {
                        // Assuming Task has getiTesk() or similar for conflict analysis
                        if (othersTask.getiTesk() != null) {
                            othersPlannedTaskArrayList.add(othersTask.getiTesk());
                        }
                    }
                    for (Task myUnstartedTask : trelloStories.getMyUnstartedTasks()) {
                        if (myUnstartedTask.getiTesk() != null) {
                            double conflictRate = conflictAnalyzer.meanRelativeConflictRiskForTasks(myUnstartedTask.getiTesk(), othersPlannedTaskArrayList);
                            double formattedConflictRate = Math.round(conflictRate * 100.0);
                            myUnstartedTask.setConflictRate(formattedConflictRate);
                        } else {
                            myUnstartedTask.setConflictRate(0.0); // Default if no iTesk
                        }
                    }

                    // Combine "My Unstarted" and "No Scenario" tasks if that's the desired logic
                    List<Task> myCombinedUnstartedTasks = new ArrayList<>(trelloStories.getMyUnstartedTasks());
                    // myCombinedUnstartedTasks.addAll(trelloStories.getNoScenarioTasks()); // If getNoScenarioTasks is still relevant

                    updateMyUnstartedTasksListInModel(myCombinedUnstartedTasks, myUnstartedTasksList, modelo1);
                    updateOtherPendingTasksListInModel(trelloStories.getOtherPendingTasks(), otherPendingTasksList, modelo2);

                    changeJpanel(TaskBar); // Show the main task bar UI

                    // Refresh JTable views - safe calls with null checks
                    safeFireTableDataChanged();
                    unstartedTable.revalidate();
                    unstartedTable.repaint();
                    startedTable.revalidate();
                    startedTable.repaint();
                });
            }

            @Override
            public void onCancel() {
                super.onCancel();
                ApplicationManager.getApplication().invokeLater(() -> {
                    limparTaskListsInModels();
                    changeJpanel(TaskBar); // Show TaskBar even on cancel, maybe with a message
                    JOptionPane.showMessageDialog(content, "Trello card loading cancelled by user.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
                });
            }
        });
    }


    private void limparTaskListsInModels() {
        try {
            if (myUnstartedTasksList != null) {
                myUnstartedTasksList.clear();
            }
            if (otherPendingTasksList != null) {
                otherPendingTasksList.clear();
            }

            if (modelo1 != null) {
                modelo1.setRowCount(0);
            }
            if (modelo2 != null) {
                modelo2.setRowCount(0);
            }
        } catch (Exception e) {
            System.err.println("ERROR in limparTaskListsInModels(): " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void changeJpanel(JPanel panel) {
        if (content == null || panel == null) {
            System.err.println("Cannot change JPanel: content or panel is null.");
            return;
        }
        //System.out.println("Attempting to change JPanel to: " + (panel.getName() != null ? panel.getName() : panel.getClass().getSimpleName()));

        if (content.getComponentCount() == 0 || content.getComponent(0) != panel) {
            content.removeAll();
            content.add(panel, BorderLayout.CENTER);
            content.revalidate();
            content.repaint();
            //System.out.println("JPanel changed to: " + (panel.getName() != null ? panel.getName() : panel.getClass().getSimpleName()));
        } else {
            //System.out.println("JPanel is already set to: " + (panel.getName() != null ? panel.getName() : panel.getClass().getSimpleName()));
        }
    }

    public void refresh() {
        // Before refreshing, ensure settings are still valid.
        // This also handles the case where settings might have been changed
        // while the plugin was open.
        changeJpanel(loading); // Show loading screen
        checkSettingsAndLoad(); // This will re-validate settings and then call loadTasksFromTrello if OK
    }

    // Renamed and adapted for Task objects
    private void updateMyUnstartedTasksListInModel(List<Task> tasks, ArrayList<Task> internalList, DefaultTableModel model) {
        internalList.clear(); // Clear before adding new ones
        for (Task task : tasks) {
            internalList.add(task);
            String taskName = truncateTaskName(task.getName()); // Assuming Task has getName()
            String conflictRateStr;

            if (task.hasScenarios()) { // Assuming Task has hasScenarios()
                conflictRateStr = task.getConflictRate() > 0 ? task.getConflictRate() + "%" : "0%";
            } else {
                conflictRateStr = "Add tests";
            }
            model.addRow(new Object[]{taskName, conflictRateStr});
        }
    }

    // Renamed and adapted for Task objects
    private void updateOtherPendingTasksListInModel(List<Task> tasks, ArrayList<Task> internalList, DefaultTableModel model) {
        internalList.clear(); // Clear before adding new ones
        for (Task task : tasks) {
            internalList.add(task);
            String taskName = truncateTaskName(task.getName());
            model.addRow(new Object[]{taskName});
        }
    }

    private String truncateTaskName(String taskName) {
        if (taskName != null && taskName.length() > 50) {
            return String.format("%s...", taskName.substring(0, 50));
        }
        return taskName;
    }

    public ArrayList<Task> getMyUnstartedTasksList() {
        return myUnstartedTasksList;
    }

    public ArrayList<Task> getOtherPendingTasksList() {
        return otherPendingTasksList;
    }

    public JPanel getContent() {
        return content;
    }

    public LoadingScreen getLoading() {
        return loading;
    }

    /**
     * Ensures that UI components are properly initialized before use.
     * This method provides protection against timing issues where background tasks
     * complete before UI initialization is finished.
     */
    private void ensureUIComponentsInitialized() {
        if (TaskBar == null || modelo1 == null || modelo2 == null) {
            System.err.println("WARNING: UI components not properly initialized. Re-initializing...");
            initializeUIComponents();
        }
    }

    /**
     * Safely calls fireTableDataChanged() with proper null checks and recovery.
     * This prevents NullPointerException when table models are not initialized.
     */
    private void safeFireTableDataChanged() {
        try {
            if (modelo1 != null) {
                modelo1.fireTableDataChanged();
            } else {
                System.err.println("WARNING: modelo1 is null, skipping fireTableDataChanged()");
            }

            if (modelo2 != null) {
                modelo2.fireTableDataChanged();
            } else {
                System.err.println("WARNING: modelo2 is null, skipping fireTableDataChanged()");
            }

            // Safely revalidate and repaint tables
            if (unstartedTable != null) {
                unstartedTable.revalidate();
                unstartedTable.repaint();
            }

            if (startedTable != null) {
                startedTable.revalidate();
                startedTable.repaint();
            }
        } catch (Exception e) {
            System.err.println("ERROR in safeFireTableDataChanged(): " + e.getMessage());
            e.printStackTrace();
        }
    }
}
