package net.leitingsd.litematica.exporter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static JTextArea logArea;
    private static JButton convertButton;
    private static JButton selectButton;
    private static JLabel statusLabel;
    private static File[] selectedFiles;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(Main::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Litematica V7 to V6 转换器");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectButton = new JButton("选择文件 (.litematic)");
        convertButton = new JButton("开始转换");
        convertButton.setEnabled(false);
        statusLabel = new JLabel("未选择文件。您也可以将文件拖放到此处。");

        topPanel.add(selectButton);
        topPanel.add(convertButton);
        topPanel.add(statusLabel);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("转换日志"));

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        frame.add(mainPanel);

        frame.setTransferHandler(new FileDropHandler());

        selectButton.addActionListener(e -> chooseFiles(frame));
        convertButton.addActionListener(e -> startConversion());

        frame.setVisible(true);
    }

    private static void handleSelectedFiles(File[] files) {
        if (files != null && files.length > 0) {
            selectedFiles = files;
            statusLabel.setText("已选择 " + selectedFiles.length + " 个文件。");
            convertButton.setEnabled(true);
            log("已选择 " + selectedFiles.length + " 个文件准备转换。");
        }
    }

    private static void chooseFiles(JFrame parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileFilter(new FileNameExtensionFilter("Litematic 文件", "litematic"));
        fileChooser.setCurrentDirectory(new File("."));

        int result = fileChooser.showOpenDialog(parent);
        if (result == JFileChooser.APPROVE_OPTION) {
            handleSelectedFiles(fileChooser.getSelectedFiles());
        }
    }

    private static void startConversion() {
        if (selectedFiles == null || selectedFiles.length == 0) return;

        selectButton.setEnabled(false);
        convertButton.setEnabled(false);
        log("\n--- 开始批量转换 ---");

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() {
                int successCount = 0;
                int failCount = 0;

                for (File file : selectedFiles) {
                    String fileName = file.getName();
                    String newName = "v6_" + fileName;
                    File outFile = new File(file.getParent(), newName);

                    publish("正在转换: " + fileName + " -> " + newName + " ...");

                    try {
                        boolean success = convertSingleFile(file.toPath(), outFile.toPath());
                        if (success) {
                            publish("  [成功] 保存至: " + outFile.getAbsolutePath());
                            successCount++;
                        } else {
                            publish("  [失败] 无法转换文件。");
                            failCount++;
                        }
                    } catch (Exception ex) {
                        publish("  [错误] 异常: " + ex.getMessage());
                        ex.printStackTrace();
                        failCount++;
                    }
                }

                publish("\n完成! 成功: " + successCount + ", 失败: " + failCount);
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    log(message);
                }
            }

            @Override
            protected void done() {
                selectButton.setEnabled(true);
                convertButton.setEnabled(true);
            }
        }.execute();
    }

    private static boolean convertSingleFile(Path inputPath, Path outputPath) {
        LitematicaSchematic v7Schematic = LitematicaSchematic.createFromFile(inputPath);
        if (v7Schematic == null) return false;

        LitematicaSchematic v6Schematic = LitematicaSchematic.createEmptySchematicFromExisting(v7Schematic);
        v6Schematic.downgradeV7toV6Schematic(v7Schematic);
        return v6Schematic.writeToFile(outputPath, true);
    }

    private static void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable transferable = support.getTransferable();
            try {
                List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                List<File> litematicFiles = new ArrayList<>();
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".litematic")) {
                        litematicFiles.add(file);
                    }
                }
                handleSelectedFiles(litematicFiles.toArray(new File[0]));
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    }
}
