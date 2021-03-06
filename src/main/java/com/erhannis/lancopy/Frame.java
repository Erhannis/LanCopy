/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.data.BinaryData;
import com.erhannis.lancopy.data.Data;
import com.erhannis.lancopy.data.ErrorData;
import com.erhannis.lancopy.data.FilesData;
import com.erhannis.lancopy.data.TextData;
import com.erhannis.mathnstuff.components.ProgressDialog;
import com.erhannis.mathnstuff.utils.ObservableMap.Change;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author erhannis
 */
public class Frame extends javax.swing.JFrame {
  private static class NodeLine {
    public final String id;
    public final String summary;

    public NodeLine(String id, String summary) {
      this.id = id;
      this.summary = summary;
    }

    @Override
    public String toString() {
      return id + " - " + summary;
    }
  }

  private final DataOwner dataOwner;
  private final JmDNSProcess jdp;

  /**
   * Creates new form Frame
   */
  public Frame(DataOwner dataOwner, JmDNSProcess jdp) {
    initComponents();
    this.dataOwner = dataOwner;
    this.jdp = jdp;
    this.setTitle(jdp.ID);
    this.cbLoopClipboard.setSelected(dataOwner.cachedSettingLoopClipboard);
    DefaultListModel<NodeLine> modelServices = new DefaultListModel<>();
    listServices.setModel(modelServices);

    if (dataOwner.cachedSettingDefaultOpenPath != null && !dataOwner.cachedSettingDefaultOpenPath.trim().isEmpty()) {
      this.fileChooser.setCurrentDirectory(new File(dataOwner.cachedSettingDefaultOpenPath.trim()));
    }

    dataOwner.localData.subscribeWithGet(data -> {
      taPostedData.setText("" + data);
    });

    dataOwner.remoteSummaries.subscribeWithGet((Change<String, String> change) -> {
      //TODO Make efficient
      modelServices.clear();
      for (Entry<String, String> entry : dataOwner.remoteSummaries.get().entrySet()) {
        modelServices.addElement(new NodeLine(entry.getKey(), entry.getValue()));
      }
    });
    
    this.addWindowListener(new WindowListener() {
      @Override
      public void windowClosing(WindowEvent e) {
        new Thread(() -> {
          try {
            Thread.sleep(5000);
          } catch (Throwable t) {
            Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, t);
          }
          Runtime.getRuntime().halt(1);
        }).start();
        jdp.shutdown();
        if (dataOwner.cachedSettingSaveSettingsOnExit) {
          dataOwner.saveSettings();
        }
      }

      @Override
      public void windowOpened(WindowEvent e) {
      }

      @Override
      public void windowClosed(WindowEvent e) {
      }

      @Override
      public void windowIconified(WindowEvent e) {
      }

      @Override
      public void windowDeiconified(WindowEvent e) {
      }

      @Override
      public void windowActivated(WindowEvent e) {
      }

      @Override
      public void windowDeactivated(WindowEvent e) {
      }
    });

    DropTarget dt = new DropTarget() {
      private boolean checkDropOk(DropTargetDropEvent e) {
        if (e.isDataFlavorSupported(DataFlavor.stringFlavor) || e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          e.acceptDrop(DnDConstants.ACTION_COPY);
          return true;
        }
        e.rejectDrop();
        return false;
      }

      private boolean checkDragOk(DropTargetDragEvent e) {
        if (e.isDataFlavorSupported(DataFlavor.stringFlavor) || e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          e.acceptDrag(DnDConstants.ACTION_COPY);
          return true;
        }
        e.rejectDrag();
        return false;
      }

      public void dragEnter(DropTargetDragEvent e) {
        checkDragOk(e);
      }

      public void dragOver(DropTargetDragEvent e) {
        checkDragOk(e);
      }

      public void dropActionChanged(DropTargetDragEvent e) {
        checkDragOk(e);
      }

      @Override
      public synchronized void drop(DropTargetDropEvent evt) {
        if (cbLoopClipboard.isSelected()) {
          return;
        }
        if (!checkDropOk(evt)) {
          return;
        }
        if (evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          try {
            List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            System.out.println("Dropped files: " + droppedFiles);
            dataOwner.localData.set(new FilesData(droppedFiles.toArray(new File[]{})));
            evt.dropComplete(true);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        } else if (evt.isDataFlavorSupported(DataFlavor.stringFlavor)) {
          evt.acceptDrop(DnDConstants.ACTION_COPY);

          try {
            String droppedString = (String) evt.getTransferable().getTransferData(DataFlavor.stringFlavor);
            System.out.println("Dropped string: " + droppedString);
            dataOwner.localData.set(new TextData(droppedString));
            evt.dropComplete(true);
          } catch (Exception ex) {
            ex.printStackTrace();
          }
        }
      }
    };

    // This segment is weird.  Something funky going on.  If you change it, things break.
    taPostedData.setDropTarget(dt);
    taLoadedData.setDropTarget(dt);
    this.setDropTarget(dt);

    Thread t = new Thread(() -> {
      while (true) {
        if (cbLoopClipboard.isSelected()) {
          try {
            dataOwner.localData.set(new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)));
          } catch (Throwable ex) {
            Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
        }
      }
    });
    t.setDaemon(true);
    t.start();
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    jSplitPane1 = new javax.swing.JSplitPane();
    jPanel1 = new javax.swing.JPanel();
    jScrollPane2 = new javax.swing.JScrollPane();
    taPostedData = new javax.swing.JTextArea();
    btnSendClipboard = new javax.swing.JButton();
    cbLoopClipboard = new javax.swing.JCheckBox();
    btnPostFiles = new javax.swing.JButton();
    jLabel3 = new javax.swing.JLabel();
    jSplitPane3 = new javax.swing.JSplitPane();
    jPanel3 = new javax.swing.JPanel();
    jScrollPane4 = new javax.swing.JScrollPane();
    taLoadedData = new javax.swing.JTextArea();
    jLabel2 = new javax.swing.JLabel();
    jPanel2 = new javax.swing.JPanel();
    jScrollPane1 = new javax.swing.JScrollPane();
    listServices = new javax.swing.JList<>();
    jLabel1 = new javax.swing.JLabel();
    jMenuBar1 = new javax.swing.JMenuBar();
    jMenu1 = new javax.swing.JMenu();
    miPostClipboard = new javax.swing.JMenuItem();
    miPostFiles = new javax.swing.JMenuItem();
    jMenu3 = new javax.swing.JMenu();
    miAbout = new javax.swing.JMenuItem();

    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

    jSplitPane1.setDividerLocation(150);
    jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

    taPostedData.setEditable(false);
    taPostedData.setColumns(20);
    taPostedData.setRows(5);
    jScrollPane2.setViewportView(taPostedData);

    btnSendClipboard.setText("Post clipboard");
    btnSendClipboard.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnSendClipboardActionPerformed(evt);
      }
    });

    cbLoopClipboard.setText("Loop clipboard");
    cbLoopClipboard.setToolTipText("Continually (1Hz) broadcast clipboard.  SETTING SAVED ON SHUTDOWN.  See settings.xml");
    cbLoopClipboard.addChangeListener(new javax.swing.event.ChangeListener() {
      public void stateChanged(javax.swing.event.ChangeEvent evt) {
        cbLoopClipboardStateChanged(evt);
      }
    });

    btnPostFiles.setText("Post files...");
    btnPostFiles.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnPostFilesActionPerformed(evt);
      }
    });

    jLabel3.setText("Currently posted:");

    javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
    jPanel1.setLayout(jPanel1Layout);
    jPanel1Layout.setHorizontalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(btnSendClipboard)
          .addComponent(btnPostFiles)
          .addComponent(cbLoopClipboard))
        .addGap(25, 25, 25)
        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(jPanel1Layout.createSequentialGroup()
            .addComponent(jLabel3)
            .addGap(0, 0, Short.MAX_VALUE))
          .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 592, Short.MAX_VALUE))
        .addContainerGap())
    );
    jPanel1Layout.setVerticalGroup(
      jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(jPanel1Layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addGroup(jPanel1Layout.createSequentialGroup()
            .addComponent(cbLoopClipboard)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnSendClipboard)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnPostFiles)
            .addGap(0, 0, Short.MAX_VALUE))
          .addGroup(jPanel1Layout.createSequentialGroup()
            .addComponent(jLabel3)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 101, Short.MAX_VALUE)))
        .addContainerGap())
    );

    jSplitPane1.setTopComponent(jPanel1);

    jSplitPane3.setDividerLocation(300);
    jSplitPane3.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

    taLoadedData.setEditable(false);
    taLoadedData.setColumns(20);
    taLoadedData.setRows(5);
    jScrollPane4.setViewportView(taLoadedData);

    jLabel2.setText("Loaded:");

    javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
    jPanel3.setLayout(jPanel3Layout);
    jPanel3Layout.setHorizontalGroup(
      jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(jPanel3Layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 739, Short.MAX_VALUE)
          .addGroup(jPanel3Layout.createSequentialGroup()
            .addComponent(jLabel2)
            .addGap(0, 0, Short.MAX_VALUE)))
        .addContainerGap())
    );
    jPanel3Layout.setVerticalGroup(
      jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(jLabel2)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(jScrollPane4, javax.swing.GroupLayout.DEFAULT_SIZE, 92, Short.MAX_VALUE)
        .addContainerGap())
    );

    jSplitPane3.setRightComponent(jPanel3);

    listServices.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    listServices.setToolTipText("Double click to copy from highlighted node");
    listServices.addMouseListener(new java.awt.event.MouseAdapter() {
      public void mouseClicked(java.awt.event.MouseEvent evt) {
        listServicesMouseClicked(evt);
      }
    });
    jScrollPane1.setViewportView(listServices);

    jLabel1.setFont(new java.awt.Font("Cantarell", 1, 15)); // NOI18N
    jLabel1.setText("Connected nodes");

    javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
    jPanel2.setLayout(jPanel2Layout);
    jPanel2Layout.setHorizontalGroup(
      jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(jPanel2Layout.createSequentialGroup()
        .addContainerGap()
        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
          .addComponent(jScrollPane1)
          .addGroup(jPanel2Layout.createSequentialGroup()
            .addComponent(jLabel1)
            .addGap(0, 621, Short.MAX_VALUE)))
        .addContainerGap())
    );
    jPanel2Layout.setVerticalGroup(
      jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
        .addContainerGap()
        .addComponent(jLabel1)
        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 251, Short.MAX_VALUE)
        .addContainerGap())
    );

    jSplitPane3.setLeftComponent(jPanel2);

    jSplitPane1.setRightComponent(jSplitPane3);

    jMenu1.setText("Actions");

    miPostClipboard.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_MASK));
    miPostClipboard.setText("Post clipboard");
    miPostClipboard.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miPostClipboardActionPerformed(evt);
      }
    });
    jMenu1.add(miPostClipboard);

    miPostFiles.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
    miPostFiles.setText("Post files...");
    miPostFiles.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miPostFilesActionPerformed(evt);
      }
    });
    jMenu1.add(miPostFiles);

    jMenuBar1.add(jMenu1);

    jMenu3.setText("Help");

    miAbout.setText("About");
    miAbout.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        miAboutActionPerformed(evt);
      }
    });
    jMenu3.add(miAbout);

    jMenuBar1.add(jMenu3);

    setJMenuBar(jMenuBar1);

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
    getContentPane().setLayout(layout);
    layout.setHorizontalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jSplitPane1)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
      .addComponent(jSplitPane1)
    );

    pack();
  }// </editor-fold>//GEN-END:initComponents

  private void listServicesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listServicesMouseClicked
    if (evt.getClickCount() >= 2) {
      pullFromNode();
    }
  }//GEN-LAST:event_listServicesMouseClicked

  private void btnSendClipboardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendClipboardActionPerformed
    if (!btnSendClipboard.isEnabled()) {
      return;
    }
    try {
      dataOwner.localData.set(new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)));
    } catch (UnsupportedFlavorException ex) {
      Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
    } catch (IOException ex) {
      Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
    }
  }//GEN-LAST:event_btnSendClipboardActionPerformed

  public final JFileChooser fileChooser = new JFileChooser();

  private void btnPostFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPostFilesActionPerformed
    if (!btnPostFiles.isEnabled()) {
      return;
    }
    fileChooser.setMultiSelectionEnabled(true);
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      dataOwner.localData.set(new FilesData(fileChooser.getSelectedFiles()));
    }
  }//GEN-LAST:event_btnPostFilesActionPerformed

  private void cbLoopClipboardStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_cbLoopClipboardStateChanged
    boolean looping = cbLoopClipboard.isSelected();
    btnPostFiles.setEnabled(!looping);
    btnSendClipboard.setEnabled(!looping);
    dataOwner.cachedSettingLoopClipboard = looping;
    //TODO Save settings?
  }//GEN-LAST:event_cbLoopClipboardStateChanged

  private void miAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miAboutActionPerformed
    JOptionPane.showMessageDialog(null,
            "Double click to pull a connected computer's clipboard.\n"
            + "Open LanCopy on both computers A and B.  Copy text on A, click \"Post clipboard\",\n"
            + "then on B, double-click A's node to pull the data over and into B's clipboard.\n"
            + "You can click \"Post files...\", or drag files onto the window, to post files.\n"
            + "Pulling files copies their new path into your clipboard, for convenience.\n"
            + "You can also drag text onto the window to post it.\n"
            + "And there are ctrl-v, ctrl-o shortcuts to post clipboard and post files,\n"
            + "respectively, though they're a little finnicky.\n"
            + "Checking \"Loop clipboard\" will cause the clipboard to be checked every second\n"
            + "for change, and any changes to be broadcast.  The checkbox state is saved if\n"
            + "program is shut down normally, by default.\n"
            + "See settings.xml for a few settings.\n"
            + "\n"
            + "Only a summary of data is broadcast, until a node requests the full data.\n"
            + "\n"
            + "Beware, there is basically zero security on this.  It's unencrypted, and\n"
            + "unauthenticated.  It is shouting your posts for the whole local network to see.\n"
            + "\n"
            + "Erhannis, 2021\n"
            + "MIT License\n"
            + "https://github.com/Erhannis/LanCopy",
            "About",
            JOptionPane.INFORMATION_MESSAGE);
  }//GEN-LAST:event_miAboutActionPerformed

  private void miPostClipboardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miPostClipboardActionPerformed
    btnSendClipboardActionPerformed(evt);
  }//GEN-LAST:event_miPostClipboardActionPerformed

  private void miPostFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miPostFilesActionPerformed
    btnPostFilesActionPerformed(evt);
  }//GEN-LAST:event_miPostFilesActionPerformed

  private void pullFromNode() {
    NodeLine nl = listServices.getSelectedValue();
    if (nl != null) {
      ProgressDialog pd = new ProgressDialog(this, false, "Pulling data...", "Hang on");
      try {
        //TODO This is not airtight; drag-n-drop still works, for instance
        this.setEnabled(false);
        pd.setVisible(true);
        Data data = jdp.pullFromNode(nl.id);
        //System.out.println("rx data: " + data);
        if (data instanceof TextData) {
          taLoadedData.setText(((TextData) data).text);
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(((TextData) data).text), null);
        } else if (data instanceof ErrorData) {
          taLoadedData.setText(((ErrorData) data).text);
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(((ErrorData) data).text), null);
        } else if (data instanceof BinaryData) {
          if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            File f = fileChooser.getSelectedFile();
            FileUtils.copyInputStreamToFile(((BinaryData) data).stream, f);
            taLoadedData.setText(((BinaryData) data).toString());
            try {
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(f.getParentFile().getAbsolutePath()), null);
            } catch (Throwable t) {
              // Nevermind
            }
          } else {
            throw new RuntimeException("File save canceled");
          }
        } else if (data instanceof FilesData) {
          FilesData fd = ((FilesData) data);
          taLoadedData.setText(fd.toLongString());
          try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fd.files[0].getParentFile().getAbsolutePath()), null);
          } catch (Throwable t) {
            // Nevermind
          }
          //System.err.println("//TODO Save files");
        } else {
          throw new RuntimeException("Unhandled data type");
        }
      } catch (IOException ex) {
        Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
        taLoadedData.setText("ERROR: " + ex.getMessage());
      } finally {
        if (pd != null) {
          pd.setVisible(false);
          pd.dispose();
          this.setEnabled(true);
        }
      }
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    final DataOwner dataOwner = new DataOwner();
    dataOwner.loadSettings();
    if (dataOwner.cachedSettingDefaultSavePath != null && !dataOwner.cachedSettingDefaultSavePath.trim().isEmpty()) {
      FilesData.fileChooser.setCurrentDirectory(new File(dataOwner.cachedSettingDefaultSavePath.trim()));
    }
    final JmDNSProcess jdp = JmDNSProcess.start(dataOwner);

//    /* Set the Nimbus look and feel */
//    
//    //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
//    /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
//         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
//     */
//    try {
//      for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//        if ("Nimbus".equals(info.getName())) {
//          javax.swing.UIManager.setLookAndFeel(info.getClassName());
//          break;
//        }
//      }
//    } catch (ClassNotFoundException ex) {
//      java.util.logging.Logger.getLogger(Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//    } catch (InstantiationException ex) {
//      java.util.logging.Logger.getLogger(Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//    } catch (IllegalAccessException ex) {
//      java.util.logging.Logger.getLogger(Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//    } catch (javax.swing.UnsupportedLookAndFeelException ex) {
//      java.util.logging.Logger.getLogger(Frame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//    }
//    //</editor-fold>
    /* Create and display the form */
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        new Frame(dataOwner, jdp).setVisible(true);
      }
    });
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton btnPostFiles;
  private javax.swing.JButton btnSendClipboard;
  private javax.swing.JCheckBox cbLoopClipboard;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JMenu jMenu1;
  private javax.swing.JMenu jMenu3;
  private javax.swing.JMenuBar jMenuBar1;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JScrollPane jScrollPane2;
  private javax.swing.JScrollPane jScrollPane4;
  private javax.swing.JSplitPane jSplitPane1;
  private javax.swing.JSplitPane jSplitPane3;
  private javax.swing.JList<NodeLine> listServices;
  private javax.swing.JMenuItem miAbout;
  private javax.swing.JMenuItem miPostClipboard;
  private javax.swing.JMenuItem miPostFiles;
  private javax.swing.JTextArea taLoadedData;
  private javax.swing.JTextArea taPostedData;
  // End of variables declaration//GEN-END:variables
}
