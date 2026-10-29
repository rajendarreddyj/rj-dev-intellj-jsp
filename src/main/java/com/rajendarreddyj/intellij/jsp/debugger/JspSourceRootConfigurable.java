package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ToolbarDecorator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public class JspSourceRootConfigurable implements Configurable {
    private final Project project;
    private JComponent component;
    private JTable table;
    private DefaultTableModel model;
    private JTextField packageField;

    public JspSourceRootConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls String getDisplayName() {
        return "JSP Source Roots";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (component != null) {
            return component;
        }

        model = new DefaultTableModel(new Object[]{"SMAP Prefix", "Local Source Root"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        table = new JTable(model);
        table.setFillsViewportHeight(true);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(table)
                .disableUpDownActions()
                .setAddAction(actionButton -> model.addRow(new Object[]{"", ""}))
                .setRemoveAction(actionButton -> {
                    int index = table.getSelectedRow();
                    if (index >= 0) {
                        model.removeRow(index);
                    }
                });

        packageField = new JTextField();
        packageField.setToolTipText("Jasper generatedJspPackageName (default " + JspSourceRootSettings.DEFAULT_GENERATED_JSP_PACKAGE + ")");
        JPanel packagePanel = new JPanel(new BorderLayout(8, 0));
        packagePanel.add(new JLabel("Generated JSP package:"), BorderLayout.WEST);
        packagePanel.add(packageField, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(packagePanel, BorderLayout.NORTH);
        panel.add(decorator.createPanel(), BorderLayout.CENTER);
        component = panel;
        reset();
        return component;
    }

    @Override
    public boolean isModified() {
        JspSourceRootSettings settings = JspSourceRootSettings.getInstance(project);
        return !rows().equals(settings.getMappings())
                || !JspSourceRootSettings.sanitizePackage(packageText()).equals(settings.getGeneratedJspPackage());
    }

    @Override
    public void apply() throws ConfigurationException {
        JspSourceRootSettings settings = JspSourceRootSettings.getInstance(project);
        settings.setMappings(rows());
        settings.setGeneratedJspPackage(packageText());
    }

    @Override
    public void reset() {
        if (model == null) {
            return;
        }
        packageField.setText(JspSourceRootSettings.getInstance(project).getGeneratedJspPackage());
        model.setRowCount(0);
        for (JspSourceRootMapping mapping : JspSourceRootSettings.getInstance(project).getMappings()) {
            model.addRow(new Object[]{mapping.getSmapPrefix(), mapping.getLocalSourceRoot()});
        }
    }

    @Override
    public void disposeUIResources() {
        component = null;
        table = null;
        model = null;
        packageField = null;
    }

    private String packageText() {
        return packageField == null ? "" : packageField.getText();
    }

    private List<JspSourceRootMapping> rows() {
        List<JspSourceRootMapping> mappings = new ArrayList<>();
        if (model == null) {
            return mappings;
        }
        for (int row = 0; row < model.getRowCount(); row++) {
            String prefix = valueAt(row, 0);
            String root = valueAt(row, 1);
            if (!root.isBlank()) {
                mappings.add(new JspSourceRootMapping(prefix, root));
            }
        }
        return mappings;
    }

    private String valueAt(int row, int col) {
        Object value = model.getValueAt(row, col);
        return value == null ? "" : value.toString().trim();
    }
}
