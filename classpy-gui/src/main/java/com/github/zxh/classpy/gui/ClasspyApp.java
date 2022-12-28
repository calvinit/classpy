package com.github.zxh.classpy.gui;

import com.github.zxh.classpy.gui.events.*;
import com.github.zxh.classpy.gui.fs.DirTreeView;
import com.github.zxh.classpy.gui.parsed.ParsedViewerPane;
import com.github.zxh.classpy.gui.support.*;
import com.github.zxh.classpy.gui.fs.ZipTreeView;
import com.github.zxh.classpy.helper.UrlHelper;
import javafx.application.Application;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

/**
 * Main class.
 */
public class ClasspyApp extends Application {

    private static final String TITLE = "Classpy";

    private final EventBus eventBus = new EventBus();

    private Stage stage;
    private BorderPane root;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(createTabPane());

        Scene scene = new Scene(root, 960, 540);
        scene.getRoot().setStyle("-fx-font-family: 'serif'");
        //scene.getStylesheets().add("classpy.css");
        enableDragAndDrop(scene);
        listenEvents();

        stage.setScene(scene);
        stage.setTitle(TITLE);
        stage.getIcons().add(ImageHelper.loadImage("/spy16.png"));
        stage.getIcons().add(ImageHelper.loadImage("/spy32.png"));
        stage.show();

        // cmd args
//        String userDir = System.getProperty("user.dir");
//        for (String arg : this.getParameters().getRaw()) {
//            String path = arg;
//            if (!arg.startsWith("/")) {
//                path = userDir + "/" + arg;
//            }
//            openFile(new File(path));
//        }
    }

    private TabPane createTabPane() {
        TabPane tp = new TabPane();
        tp.getSelectionModel().selectedItemProperty().addListener(
                (ObservableValue<? extends Tab> observable, Tab oldTab, Tab newTab) -> {
                    if (newTab != null) {
                        stage.setTitle(TITLE + " - " + newTab.getUserData());
                    }
        });
        return tp;
    }

    private Tab createFileTab(String url) {
        Tab tab = new Tab();
        tab.setText(UrlHelper.getFileName(url));
        tab.setUserData(url);
        tab.setContent(new BorderPane(new ProgressBar()));
        ((TabPane) root.getCenter()).getTabs().add(tab);
        return tab;
    }

    private Tab createDirTab(File dir) {
        Tab tab = new Tab();
        tab.setText(dir.getName() + "/");
        tab.setContent(new BorderPane(new ProgressBar()));
        ((TabPane) root.getCenter()).getTabs().add(tab);
        return tab;
    }

    private MenuBar createMenuBar() {
        MyMenuBar menuBar = new MyMenuBar(eventBus);
        //menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    // http://www.java2s.com/Code/Java/JavaFX/DraganddropfiletoScene.htm
    private void enableDragAndDrop(Scene scene) {
        scene.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            } else {
                event.consume();
            }
        });

        // Dropping over surface
        scene.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                for (File file : db.getFiles()) {
                    //System.out.println(file.getAbsolutePath());
                    openFile(file);
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void openFile(FileType ft, String url) {
        if (ft == FileType.FOLDER) {
            openDir(url);
        } else if (url == null) {
            if (ft == FileType.BITCOIN_BLOCK) {
                showBitcoinBlockDialog();
            } else if (ft == FileType.BITCOIN_TX) {
                showBitcoinTxDialog();
            } else {
                showFileChooser(ft);
            }
        } else {
            openFile(url);
        }
    }

    private void openDir(String url) {
        File dir = null;
        if (url != null) {
            try {
                dir = new File(new URL(url).toURI());
            } catch (MalformedURLException | URISyntaxException e) {
                e.printStackTrace(System.err);
            }
        } else {
            dir = MyFileChooser.showDirChooser(stage);
        }

        if (dir != null) {
            System.out.println(dir);
            try {
                DirTreeView treeView = DirTreeView.create(dir);
                treeView.setOpenFileHandler(this::openFile);

                Tab tab = createDirTab(dir);
                tab.setContent(treeView.getTreeView());

                RecentFiles.INSTANCE.add(FileType.FOLDER, dir.toURI().toURL().toString());
                eventBus.pub(new UpdateRecentFiles());
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    private void showBitcoinBlockDialog() {
        String apiUrl = "https://blockchain.info/rawblock/<hash>?format=hex";
        String genesisBlockHash = "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f";

        TextInputDialog dialog = new TextInputDialog(genesisBlockHash);
        dialog.setTitle("Block Hash Input Dialog");
        dialog.setHeaderText("API: " + apiUrl);
        dialog.setContentText("hash: ");
        dialog.setResizable(true);

        // Traditional way to get the response value.
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(s -> openFile(apiUrl.replace("<hash>", s)));
    }

    private void showBitcoinTxDialog() {
        String apiUrl = "https://blockchain.info/rawtx/<hash>?format=hex";

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Transaction Hash Input Dialog");
        dialog.setHeaderText("API: " + apiUrl);
        dialog.setContentText("hash: ");
        dialog.setResizable(true);

        // Traditional way to get the response value.
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(s -> apiUrl.replace("<hash>", s));
    }

    private void showFileChooser(FileType ft) {
        File file = MyFileChooser.showFileChooser(stage, ft);
        if (file != null) {
            openFile(file);
        }
    }

    private void openFile(File file) {
        try {
            openFile(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            e.printStackTrace(System.err);
        }
    }

    private void openFile(String url) {
        Tab tab = createFileTab(url);
        OpenFileTask task = new OpenFileTask(url);

        task.setOnSucceeded((OpenFileResult ofr) -> {
            if (ofr.fileType.isZip()) {
                ZipTreeView treeView = new ZipTreeView(ofr.url, ofr.zipRootNode);
                treeView.setOpenFileHandler(this::openFile);
                tab.setContent(treeView.getTreeView());
            } else {
                ParsedViewerPane viewerPane = new ParsedViewerPane(ofr.fileRootNode, ofr.hexText);
                tab.setContent(viewerPane);
            }

            RecentFiles.INSTANCE.add(ofr.fileType, url);
            eventBus.pub(new UpdateRecentFiles());
        });

        task.setOnFailed((Throwable err) -> {
            Text errMsg = new Text(err.toString());
            tab.setContent(errMsg);
        });

        task.startInNewThread();
    }

    private void listenEvents() {
        eventBus.sub(OpenAboutDialog.class,
                event -> getHostServices().showDocument(event.url));
        eventBus.sub(OpenNewWindow.class,
                event -> new ClasspyApp().start(new Stage()));
        eventBus.sub(CloseAllTabs.class,
                event -> ((TabPane) root.getCenter()).getTabs().clear());
        eventBus.sub(OpenFile.class,
                event -> openFile(event.fileType, event.fileUrl));
    }

    public static void main(String[] args) {
        Application.launch(args);
    }

}
