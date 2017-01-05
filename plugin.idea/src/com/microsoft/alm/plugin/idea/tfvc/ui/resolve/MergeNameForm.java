// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.microsoft.alm.plugin.idea.tfvc.ui.resolve;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.EventDispatcher;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventListener;

public class MergeNameForm {
    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        myContentPanel = new JPanel();
        myContentPanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
        myYoursRadioButton = new JRadioButton();
        myYoursRadioButton.setSelected(true);
        myYoursRadioButton.setText("Keep local");
        myYoursRadioButton.setMnemonic('K');
        myYoursRadioButton.setDisplayedMnemonicIndex(0);
        myContentPanel.add(myYoursRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myTheirsRadioButton = new JRadioButton();
        myTheirsRadioButton.setText("Accept server");
        myTheirsRadioButton.setMnemonic('A');
        myTheirsRadioButton.setDisplayedMnemonicIndex(0);
        myContentPanel.add(myTheirsRadioButton, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myUseCustomRadioButton = new JRadioButton();
        myUseCustomRadioButton.setText("Use specified:");
        myUseCustomRadioButton.setMnemonic('U');
        myUseCustomRadioButton.setDisplayedMnemonicIndex(0);
        myContentPanel.add(myUseCustomRadioButton, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCustomPathTextField = new JTextField();
        myCustomPathTextField.setEnabled(false);
        myContentPanel.add(myCustomPathTextField, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(100, -1), null, 2, false));
        final Spacer spacer1 = new Spacer();
        myContentPanel.add(spacer1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myErrorLabel = new JLabel();
        myErrorLabel.setForeground(new Color(-65536));
        myErrorLabel.setText("");
        myContentPanel.add(myErrorLabel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(myYoursRadioButton);
        buttonGroup.add(myTheirsRadioButton);
        buttonGroup.add(myUseCustomRadioButton);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myContentPanel;
    }

    public interface Listener extends EventListener {
        void selectedPathChanged();
    }

    private final EventDispatcher<Listener> myEventDispatcher = EventDispatcher.create(Listener.class);

    private final String myYoursPath;
    private final String myTheirsPath;

    private JRadioButton myYoursRadioButton;
    private JRadioButton myTheirsRadioButton;
    private JRadioButton myUseCustomRadioButton;
    private JTextField myCustomPathTextField;
    private JPanel myContentPanel;
    private JLabel myErrorLabel;

    public MergeNameForm(final String yoursName, final String theirsName) {
        myYoursPath = yoursName;
        myTheirsPath = theirsName;

        myYoursRadioButton.setText(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_CONFLICT_NAME_KEEP_LOCAL));
        myTheirsRadioButton.setText(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_CONFLICT_NAME_ACCEPT_SERVER));
        myUseCustomRadioButton.setText(TfPluginBundle.message(TfPluginBundle.KEY_TFVC_CONFLICT_NAME_USE_SPECIFED));

        myYoursRadioButton.setText(myYoursRadioButton.getText() + ": " + myYoursPath);
        myYoursRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                update();
            }
        });

        myTheirsRadioButton.setText(myTheirsRadioButton.getText() + ": " + myTheirsPath);
        myTheirsRadioButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                update();
            }
        });

        // disabling custom option for path
        // TODO: allow customization later on
        myCustomPathTextField.setVisible(false);
        myUseCustomRadioButton.setVisible(false);
//    myCustomPathTextField.setText(myYoursPath);
//    myUseCustomRadioButton.addActionListener(new ActionListener() {
//      public void actionPerformed(ActionEvent ae) {
//        update();
//      }
//    });
//
//    myCustomPathTextField.setText(myYoursPath);
//
//    myCustomPathTextField.getDocument().addDocumentListener(new DocumentAdapter() {
//      protected void textChanged(final DocumentEvent e) {
//        myEventDispatcher.getMulticaster().selectedPathChanged();
//      }
//    });

        myErrorLabel.setText(" ");
    }

    private void update() {
        myCustomPathTextField.setEnabled(myUseCustomRadioButton.isSelected());
        myEventDispatcher.getMulticaster().selectedPathChanged();
    }

    public JComponent getPanel() {
        return myContentPanel;
    }

    @Nullable
    public String getSelectedPath() {
        if (myYoursRadioButton.isSelected()) {
            return myYoursPath;
        } else if (myTheirsRadioButton.isSelected()) {
            return myTheirsPath;
        } else if (myUseCustomRadioButton.isSelected()) {
            return myCustomPathTextField.getText();
        }
        throw new IllegalStateException("Unexpected state");
    }

    public void addListener(Listener listener) {
        myEventDispatcher.addListener(listener);
    }

    public void removeListener(Listener listener) {
        myEventDispatcher.removeListener(listener);
    }

    public void setErrorText(final String errorMessage) {
        myErrorLabel.setText(errorMessage);
    }
}
