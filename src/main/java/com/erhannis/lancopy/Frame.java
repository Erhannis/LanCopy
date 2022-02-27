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
import com.erhannis.lancopy.data.NoData;
import com.erhannis.lancopy.data.TextData;
import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.lancopy.refactor.LanCopyNet;
import com.erhannis.lancopy.refactor.Summary;
import com.erhannis.lancopy.refactor.tcp.TcpComm;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import com.erhannis.mathnstuff.components.OptionsFrame;
import com.erhannis.mathnstuff.components.ProgressDialog;
import com.erhannis.mathnstuff.utils.Options;
import com.google.common.collect.Lists;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.ParseException;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import jcsp.helpers.JcspUtils;
import jcsp.lang.Alternative;
import jcsp.lang.AltingChannelInputInt;
import jcsp.lang.Any2OneChannelInt;
import jcsp.lang.Channel;
import jcsp.lang.ChannelOutputInt;
import jcsp.lang.Guard;
import jcsp.lang.ProcessManager;
import jcsp.util.ints.OverWriteOldestBufferInt;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author erhannis
 */
public class Frame extends javax.swing.JFrame {

    private static class NodeLine {

        public final Summary summary;

        public NodeLine(Summary summary) {
            this.summary = summary;
        }

        @Override
        public String toString() {
            return summary.timestamp + " | " + summary.id + " - " + summary.summary;
        }
    }

    private final DataOwner dataOwner;
    private final LanCopyNet.UiInterface uii;
    private ConcurrentLinkedDeque<CommsFrame> commsFrames = new ConcurrentLinkedDeque<>();
    private ConcurrentHashMap<Comm, Boolean> commStatus = new ConcurrentHashMap<>();

    /**
     * Creates new form Frame
     */
    public Frame(LanCopyNet.UiInterface uii, Data initialData, AltingChannelInputInt showLocalFingerprintIn) {
        //List<Comm> lComms = Lists.newArrayList(new QRComm(null));
        //dummyAd = new Advertisement(new UUID(0, 0), System.currentTimeMillis(), lComms, true, null);

        
        initComponents();
        this.dataOwner = uii.dataOwner;
        this.uii = uii;
        this.setTitle(dataOwner.ID.toString());
        this.cbLoopClipboard.setSelected((Boolean) dataOwner.options.getOrDefault("LOOP_CLIPBOARD", false));
        DefaultListModel<NodeLine> modelServices = new DefaultListModel<>();
        listServices.setModel(modelServices);

        String openPath = (String) dataOwner.options.getOrDefault("DEFAULT_OPEN_PATH", "");
        if (openPath != null && !openPath.trim().isEmpty()) {
            this.fileOpenChooser.setCurrentDirectory(new File(openPath.trim()));
        }
        String savePath = (String) dataOwner.options.getOrDefault("DEFAULT_SAVE_PATH", "");
        if (savePath != null && !savePath.trim().isEmpty()) {
            this.fileSaveChooser.setCurrentDirectory(new File(savePath.trim()));
        }
        
        if (initialData != null) {
            setData(initialData);
        }

        new ProcessManager(() -> {
            Alternative alt = new Alternative(new Guard[]{uii.adIn, uii.commStatusIn, uii.summaryIn, uii.confirmationServer, showLocalFingerprintIn});
            HashMap<UUID, Summary> summarys = new HashMap<>();
            List<Advertisement> roster = uii.rosterCall.call(null);
            for (Advertisement ad : roster) {
                //TODO Creating a false Summary makes me uncomfortable
                summarys.put(ad.id, new Summary(ad.id, ad.timestamp, "???"));
            }
            while (true) {
                switch (alt.fairSelect()) {
                    case 0: // adIn
                    {
                        Advertisement ad = uii.adIn.read();
                        System.out.println("UI rx " + ad);
                        if (!summarys.containsKey(ad.id)) {
                            //TODO Creating a false Summary makes me uncomfortable
                            summarys.put(ad.id, new Summary(ad.id, ad.timestamp, "???"));
                        }
                        Iterator<CommsFrame> cfi = commsFrames.iterator();
                        while (cfi.hasNext()) {
                            CommsFrame cf = cfi.next();
                            if (cf.isDisplayable()) {
                                cf.update(ad);
                            } else {
                                cfi.remove();
                            }
                        }
                        uii.subscribeOut.write(ad.comms);
                        break;
                    }
                    case 1: // commStatusIn
                    {
                        Pair<Comm, Boolean> status = uii.commStatusIn.read();
                        commStatus.put(status.a, status.b);
                        System.out.println("UI rx " + status);
                        Iterator<CommsFrame> cfi = commsFrames.iterator();
                        while (cfi.hasNext()) {
                            CommsFrame cf = cfi.next();
                            if (cf.isDisplayable()) {
                                cf.update(status);
                            } else {
                                cfi.remove();
                            }
                        }
                        break;
                    }
                    case 2: // summaryIn
                    {
                        Summary summary = uii.summaryIn.read();
                        System.out.println("UI rx " + summary);
                        summarys.put(summary.id, summary);
                        break;
                    }
                    case 3: { // uii.confirmationServer
                        String msg = uii.confirmationServer.startRead();
                        boolean result = false;
                        if (JOptionPane.showConfirmDialog(null, msg, "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                            result = true;
                        } else {
                            result = false;
                        }
                        uii.confirmationServer.endRead(result);
                        break;
                    }
                    case 4: { // showLocalFingerprintIn
                        showLocalFingerprintIn.read();
                        boolean show = (boolean) uii.dataOwner.options.getOrDefault("TLS.SHOW_LOCAL_FINGERPRINT", true);
                        if (show) {
                            JOptionPane.showMessageDialog(null, "An incoming connection has paused, presumably for fingerprint verification.\nThe local TLS fingerprint is:\n" + uii.dataOwner.tlsContext.sha256Fingerprint, "Security: Local fingerprint", JOptionPane.INFORMATION_MESSAGE);
                        }
                        break;
                    }
                }
                //TODO Make efficient
                final HashMap<UUID, Summary> scopy = new HashMap<>(summarys);
                
                ArrayList<NodeLine> nodeLines = new ArrayList<>();
                for (Map.Entry<UUID, Summary> entry : scopy.entrySet()) {
                    nodeLines.add(new NodeLine(entry.getValue()));
                }
                int sorting = (int) uii.dataOwner.options.getOrDefault("NodeList.SORT_BY_(TIMESTAMP|ID|SUMMARY)", 0);
                switch (sorting) {
                    case 0: // Timestamp
                        Collections.sort(nodeLines, (o1, o2) -> -Long.compare(o1.summary.timestamp, o2.summary.timestamp));
                        break;
                    case 1: // Id
                        Collections.sort(nodeLines, (o1, o2) -> MeUtils.compare(o1.summary.id.toString(), o2.summary.id.toString()));
                        break;
                    case 2: // Summary
                        Collections.sort(nodeLines, (o1, o2) -> MeUtils.compare(o1.summary.summary, o2.summary.summary));
                        break;
                }
                
                SwingUtilities.invokeLater(() -> {
                    modelServices.clear();
                    for (NodeLine nl : nodeLines) {
                        modelServices.addElement(nl);
                    }
                    // Invalidate model or something?
                });
            }
        }).start();

        this.addWindowListener(new WindowListener() {
            @Override
            public void windowClosing(WindowEvent e) {
                Thread t = new Thread(() -> {
                    // Ensure shutdown
                    try {
                        Thread.sleep(5000);
                    } catch (Throwable ex) {
                        Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Runtime.getRuntime().halt(1);
                });
                t.setDaemon(true);
                t.start();

                if ((Boolean) dataOwner.options.getOrDefault("SAVE_SETTINGS_ON_EXIT", true)) {
                    try {
                        Options.saveOptions(dataOwner.options, OptionsFrame.DEFAULT_OPTIONS_FILENAME);
                    } catch (IOException ex) {
                        Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                dataOwner.errOnce("UI //TODO Graceful shutdown");
                //jdp.shutdown();
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
                        setData(new FilesData(droppedFiles.toArray(new File[]{})));
                        evt.dropComplete(true);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (evt.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);

                    try {
                        String droppedString = (String) evt.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        System.out.println("Dropped string: " + droppedString);
                        setData(new TextData(droppedString));
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
                        setData(new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)));
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

    private void setData(Data data) {
        taPostedData.setText("" + data);
        uii.newDataOut.write(data);
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
        miPostLanCopy = new javax.swing.JMenuItem();
        jMenu2 = new javax.swing.JMenu();
        miOptions = new javax.swing.JMenuItem();
        miComms = new javax.swing.JMenuItem();
        miManualUrls = new javax.swing.JMenuItem();
        miManualConnect = new javax.swing.JMenuItem();
        miQrChannel = new javax.swing.JMenuItem();
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

        miPostClipboard.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        miPostClipboard.setText("Post clipboard");
        miPostClipboard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miPostClipboardActionPerformed(evt);
            }
        });
        jMenu1.add(miPostClipboard);

        miPostFiles.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        miPostFiles.setText("Post files...");
        miPostFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miPostFilesActionPerformed(evt);
            }
        });
        jMenu1.add(miPostFiles);

        miPostLanCopy.setText("Post LanCopy");
        miPostLanCopy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miPostLanCopyActionPerformed(evt);
            }
        });
        jMenu1.add(miPostLanCopy);

        jMenuBar1.add(jMenu1);

        jMenu2.setText("Windows");

        miOptions.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.ALT_DOWN_MASK));
        miOptions.setText("Options...");
        miOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miOptionsActionPerformed(evt);
            }
        });
        jMenu2.add(miOptions);

        miComms.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.ALT_DOWN_MASK));
        miComms.setText("Comms...");
        miComms.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miCommsActionPerformed(evt);
            }
        });
        jMenu2.add(miComms);

        miManualUrls.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.event.InputEvent.ALT_DOWN_MASK));
        miManualUrls.setText("Manual urls...");
        miManualUrls.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miManualUrlsActionPerformed(evt);
            }
        });
        jMenu2.add(miManualUrls);

        miManualConnect.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.ALT_DOWN_MASK));
        miManualConnect.setText("Manual connect...");
        miManualConnect.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miManualConnectActionPerformed(evt);
            }
        });
        jMenu2.add(miManualConnect);

        miQrChannel.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.ALT_DOWN_MASK));
        miQrChannel.setText("QR channel...");
        miQrChannel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                miQrChannelActionPerformed(evt);
            }
        });
        jMenu2.add(miQrChannel);

        jMenuBar1.add(jMenu2);

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
          setData(new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor)));
      } catch (UnsupportedFlavorException ex) {
          Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
      } catch (IOException ex) {
          Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
      }
  }//GEN-LAST:event_btnSendClipboardActionPerformed

  public final JFileChooser fileOpenChooser = new JFileChooser();
  public final JFileChooser fileSaveChooser = new JFileChooser();

  private void btnPostFilesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnPostFilesActionPerformed
      if (!btnPostFiles.isEnabled()) {
          return;
      }
      fileOpenChooser.setMultiSelectionEnabled(true);
      if (fileOpenChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
          setData(new FilesData(fileOpenChooser.getSelectedFiles()));
      }
  }//GEN-LAST:event_btnPostFilesActionPerformed

  private void cbLoopClipboardStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_cbLoopClipboardStateChanged
      boolean looping = cbLoopClipboard.isSelected();
      btnPostFiles.setEnabled(!looping);
      btnSendClipboard.setEnabled(!looping);
      dataOwner.options.set("LOOP_CLIPBOARD", looping);
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
              + "See \"Options...\" for options, most recently accessed first.\n"
              + "See \"Manual urls...\" for a list of URLs from which to fetch local data,\n"
              + "from e.g. a browser.\n"
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

    private void miOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miOptionsActionPerformed
        new OptionsFrame("Note: many of these options require a restart.", dataOwner.options, OptionsFrame.DEFAULT_OPTIONS_FILENAME).setVisible(true);
    }//GEN-LAST:event_miOptionsActionPerformed

    private void miCommsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miCommsActionPerformed
        CommsFrame cf = new CommsFrame(uii.subscribeOut);
        cf.setVisible(true);
        commsFrames.add(cf);
        for (Advertisement ad : uii.rosterCall.call(null)) {
            cf.update(ad);
        }
        //TODO Tighten race condition here between subscription and overwriting new with old
        for (Entry<Comm, Boolean> state : commStatus.entrySet()) {
            cf.update(Pair.gen(state.getKey(), state.getValue()));
        }
    }//GEN-LAST:event_miCommsActionPerformed

    private void miManualUrlsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miManualUrlsActionPerformed
        new UrlFrame(dataOwner, uii.adCall.call(dataOwner.ID)).setVisible(true);
    }//GEN-LAST:event_miManualUrlsActionPerformed

    private void miManualConnectActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miManualConnectActionPerformed
        String url = JOptionPane.showInputDialog("Enter address and port (e.g. 192.168.1.99:12345)");
        if (url == null) {
            return;
        }

        try { // https://stackoverflow.com/a/2347356/513038
            // WORKAROUND: add any scheme to make the resulting URI valid.
            URI uri = new URI("my://" + url); // may throw URISyntaxException
            String host = uri.getHost();
            int port = uri.getPort();

            if (uri.getHost() == null || uri.getPort() == -1) {
                throw new URISyntaxException(uri.toString(), "URI must have host and port parts");
            }

            List<Comm> lComms = Lists.newArrayList(new TcpComm(null, host, port));
            Advertisement lad = new Advertisement(null, System.currentTimeMillis(), lComms, true, null);
            uii.subscribeOut.write(lad.comms);
        } catch (URISyntaxException ex) {
            System.err.println("Failed to validate host:port : " + url);
        }
    }//GEN-LAST:event_miManualConnectActionPerformed

    private void miPostLanCopyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miPostLanCopyActionPerformed
        try {
            setData(new FilesData(new File[]{new File(Frame.class.getProtectionDomain().getCodeSource().getLocation().toURI())}));
        } catch (URISyntaxException ex) {
            Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_miPostLanCopyActionPerformed

    private void miQrChannelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_miQrChannelActionPerformed
        SwingQRComm qc = new SwingQRComm(null);
        uii.subscribeOut.write(Arrays.asList(qc));
    }//GEN-LAST:event_miQrChannelActionPerformed
    
    private void pullFromNode() {
        NodeLine nl = listServices.getSelectedValue();
        if (nl != null) {
            ProgressDialog pd = new ProgressDialog(this, false, "Pulling data...", "Hang on");
            this.setEnabled(false);
            pd.setVisible(true);
            new ProcessManager(() -> {
                try {
                    //TODO This is not airtight; drag-n-drop still works, for instance
                    Pair<String, InputStream> result = uii.dataCall.call(nl.summary.id);
                    try { //[finally close stream]
                        Data data;
                        if (result == null) {
                            data = new ErrorData("Node could not be reached");
                        } else {
                            switch (result.a) {
                                case "text/plain":
                                    data = TextData.deserialize(result.b);
                                    break;
                                case "application/octet-stream":
                                    data = BinaryData.deserialize(result.b);
                                    break;
                                case "lancopy/files":
                                    data = FilesData.deserialize(result.b, filename -> {
                                        File f = new File(filename);
                                        fileSaveChooser.setSelectedFile(f);
                                        if (fileSaveChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                          return fileSaveChooser.getSelectedFile();
                                        } else {
                                          return null;
                                        }
                                    });
                                    break;
                                case "lancopy/nodata":
                                    data = NoData.deserialize(result.b);
                                    break;
                                default:
                                    data = new ErrorData("Unhandled MIME: " + result.a);
                                    break;
                            }
                        }
                        //System.out.println("rx data: " + data);
                        if (data instanceof TextData) {
                            taLoadedData.setText(((TextData) data).text);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(((TextData) data).text), null);
                        } else if (data instanceof ErrorData) {
                            taLoadedData.setText(((ErrorData) data).text);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(((ErrorData) data).text), null);
                        } else if (data instanceof BinaryData) {
                            if (fileOpenChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                                File f = fileOpenChooser.getSelectedFile();
                                System.out.println("--> copyInputStreamToFile");
                                FileUtils.copyInputStreamToFile(((BinaryData) data).stream, f);
                                System.out.println("<-- copyInputStreamToFile");
                                ((BinaryData) data).stream.close();
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
                        } else if (data instanceof NoData) {
                            taLoadedData.setText(((NoData) data).toString());
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(((NoData) data).toString()), null);
                        } else {
                            throw new RuntimeException("Unhandled data type");
                        }
                    } finally {
                        try {
                            result.b.close();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
                    taLoadedData.setText("ERROR: " + ex.getMessage());
                } finally {
                    if (pd != null) {
                        pd.setVisible(false);
                        pd.dispose();
                        Frame.this.setEnabled(true);
                    }
                }
            }).start();
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) throws InterruptedException, IOException, ParseException, JSAPException {
        SimpleJSAP jsap = new SimpleJSAP(
                "LanCopy",
                "Send files and text from one computer to another nearby with minimum effort.",
                new Parameter[]{
                    new Switch("help2", 'h', null, "Print help."),
                    new Switch("clipboard", 'c', "clipboard", "Post clipboard on start."),
                    new Switch("self", 's', "self", "Post LanCopy on start."),
                    new UnflaggedOption("files", JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED, JSAP.GREEDY,
                            "Zero or more files to post on start.")
                }
        );

        JSAPResult config = jsap.parse(args);
        if (jsap.messagePrinted()) {
            System.exit(1);
        }
        if (config.getBoolean("help2")) {
            System.out.println(jsap.getHelp());
            System.exit(0);
        }

        String[] files = config.getStringArray("files");
        boolean clipboard = config.getBoolean("clipboard");
        boolean postSelf = config.getBoolean("self");

        LanCopyNet.UiInterface[] uii0 = new LanCopyNet.UiInterface[1];
        
        Any2OneChannelInt showLocalFingerprintChannel = Channel.any2oneInt(new OverWriteOldestBufferInt(1));
        AltingChannelInputInt showLocalFingerprintIn = showLocalFingerprintChannel.in();
        ChannelOutputInt showLocalFingerprintOut = JcspUtils.logDeadlock(showLocalFingerprintChannel.out());
        
        final DataOwner dataOwner = new DataOwner(OptionsFrame.DEFAULT_OPTIONS_FILENAME, showLocalFingerprintOut, (msg) -> {
            String localFingerprint = "UNKNOWN";
            LanCopyNet.UiInterface luii = uii0[0];
            if (luii != null) {
                localFingerprint = luii.dataOwner.tlsContext.sha256Fingerprint;
            }
            msg = msg + "\n\n" + "Local fingerprint is\n" + localFingerprint;
            if (JOptionPane.showConfirmDialog(null, msg, "Security error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                return true;
            } else {
                return false;
            }
        });
        
        final LanCopyNet.UiInterface uii = LanCopyNet.startNet(dataOwner, showLocalFingerprintOut);
        uii0[0] = uii;

        final Data data;
        if (files.length > 0) {
            System.out.println("Dropped files: " + files);
            data = new FilesData(Arrays.asList(files).stream().map(s -> new File(s)).toArray(n -> new File[n]));
        } else if (clipboard) {
            Data data0 = null;
            try {
                data0 = new TextData((String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
            } catch (UnsupportedFlavorException ex) {
                Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
            }
            data = data0;
        } else if (postSelf) {
            Data data0 = null;
            try {
                data0 = new FilesData(new File[]{new File(Frame.class.getProtectionDomain().getCodeSource().getLocation().toURI())});
            } catch (URISyntaxException ex) {
                Logger.getLogger(Frame.class.getName()).log(Level.SEVERE, null, ex);
            }
            data = data0;
        } else {
            data = null;
        }

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
                new Frame(uii, data, showLocalFingerprintIn).setVisible(true);
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
    private javax.swing.JMenu jMenu2;
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
    private javax.swing.JMenuItem miComms;
    private javax.swing.JMenuItem miManualConnect;
    private javax.swing.JMenuItem miManualUrls;
    private javax.swing.JMenuItem miOptions;
    private javax.swing.JMenuItem miPostClipboard;
    private javax.swing.JMenuItem miPostFiles;
    private javax.swing.JMenuItem miPostLanCopy;
    private javax.swing.JMenuItem miQrChannel;
    private javax.swing.JTextArea taLoadedData;
    private javax.swing.JTextArea taPostedData;
    // End of variables declaration//GEN-END:variables
}
