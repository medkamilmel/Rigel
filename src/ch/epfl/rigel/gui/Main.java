package ch.epfl.rigel.gui;

import ch.epfl.rigel.astronomy.AsterismLoader;
import ch.epfl.rigel.astronomy.HygDatabaseLoader;
import ch.epfl.rigel.astronomy.StarCatalogue;
import ch.epfl.rigel.coordinates.GeographicCoordinates;
import ch.epfl.rigel.coordinates.HorizontalCoordinates;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.converter.LocalTimeStringConverter;
import javafx.util.converter.NumberStringConverter;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * @author Bastien Faivre (310929)
 * @author Kamil Mellouk (312327)
 */

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {

        ObserverLocationBean observerLocationBean = new ObserverLocationBean();
        DateTimeBean dateTimeBean = new DateTimeBean();

        ViewingParametersBean viewingParametersBean = new ViewingParametersBean();
        viewingParametersBean.setCenter(HorizontalCoordinates.ofDeg(180.000000000001, 42));
        viewingParametersBean.setFieldOfViewDeg(100);

        SkyCanvasManager canvasManager = createManager(dateTimeBean, observerLocationBean, viewingParametersBean);

        TimeAnimator timeAnimator = new TimeAnimator(dateTimeBean);

        BorderPane mainPane = new BorderPane(
                new Pane(canvasManager.canvas()),
                controlBar(observerLocationBean, dateTimeBean , timeAnimator),
                null,
                infoBar(viewingParametersBean, canvasManager),
                null
        );

        canvasManager.canvas().widthProperty().bind(mainPane.widthProperty());
        canvasManager.canvas().heightProperty().bind(mainPane.heightProperty());

        primaryStage.setScene(new Scene(mainPane));

        primaryStage.setTitle("Rigel");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        primaryStage.show();
        canvasManager.canvas().requestFocus();

    }


    private HBox controlBar(ObserverLocationBean observerLocationBean, DateTimeBean dateTimeBean, TimeAnimator timeAnimator) throws IOException {

        // Observer Location
        HBox whereControl = new HBox(
                new Label("Longitude (°) :"), createTextField(true, observerLocationBean, 6.57),
                new Label("Latitude (°) :"), createTextField(false, observerLocationBean, 46.52)
        );
        whereControl.setStyle("-fx-spacing: inherit; -fx-alignment: baseline-left;");

        DatePicker datePicker = new DatePicker();
        datePicker.setStyle("-fx-pref-width: 120");
        datePicker.setValue(LocalDate.now());
        dateTimeBean.dateProperty().bindBidirectional(datePicker.valueProperty());

        TextField timeField = new TextField();
        timeField.setStyle("-fx-pref-width: 75; -fx-alignment: baseline-right;");
        DateTimeFormatter hmsFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTimeStringConverter stringConverter = new LocalTimeStringConverter(hmsFormatter, hmsFormatter);
        TextFormatter<LocalTime> timeFormatter = new TextFormatter<>(stringConverter);
        timeField.setTextFormatter(timeFormatter);
        timeFormatter.setValue(LocalTime.now());
        dateTimeBean.timeProperty().bindBidirectional(timeFormatter.valueProperty());

        ComboBox<ZoneId> timeZone = new ComboBox<>();
        List<String> availableZoneIds = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(availableZoneIds);
        List<ZoneId> zoneIdList = new ArrayList<>();
        availableZoneIds.forEach(e -> zoneIdList.add(ZoneId.of(e)));
        timeZone.setItems(FXCollections.observableList(zoneIdList));
        timeZone.setStyle("-fx-pref-width: 180");
        timeZone.setValue(ZoneId.systemDefault());
        dateTimeBean.zoneProperty().bindBidirectional(timeZone.valueProperty());

        HBox whenControl = new HBox(
                new Label("Date :"), datePicker,
                new Label("Heure :"), timeField, timeZone
        );
        whenControl.setStyle("-fx-spacing: inherit; -fx-alignment: baseline-left;");
        whenControl.disableProperty().bind(timeAnimator.runningProperty());

        // time flow

        ChoiceBox<NamedTimeAccelerator> acceleratorChoicer = new ChoiceBox<>();
        acceleratorChoicer.setItems(FXCollections.observableList(List.of(NamedTimeAccelerator.values())));
        acceleratorChoicer.valueProperty().addListener(
                (p, o, n) -> {
                    timeAnimator.setAccelerator(n.getAccelerator());
                }
        );
        acceleratorChoicer.setValue(NamedTimeAccelerator.TIMES_300);
        acceleratorChoicer.disableProperty().bind(timeAnimator.runningProperty());

        Font fontAwesome = loadFontAwesome();

        Button resetButton = new Button("\uf0e2");
        resetButton.setFont(fontAwesome);
        resetButton.setOnAction(e -> {
            datePicker.setValue(LocalDate.now());
            timeFormatter.setValue(LocalTime.now());
            timeZone.setValue(ZoneId.systemDefault());
        });
        resetButton.disableProperty().bind(timeAnimator.runningProperty());

        Button startStopButton = new Button("\uf04b");
        startStopButton.setFont(fontAwesome);
        startStopButton.setOnAction(e -> {
            if(!timeAnimator.isRunning()) {
                timeAnimator.start();
                startStopButton.setText("\uf04c");
            } else {
                timeAnimator.stop();
                startStopButton.setText("\uf04b");
            }
        });


        HBox timeFlowControl = new HBox(acceleratorChoicer, resetButton, startStopButton);
        timeFlowControl.setStyle("-fx-spacing: inherit");

        // control bar

        HBox controlBar = new HBox(
                whereControl, verticalSeparator(),
                whenControl, verticalSeparator(),
                timeFlowControl
        );

        controlBar.setStyle("-fx-spacing: 4; -fx-padding: 4;");
        return controlBar;
    }


    private SkyCanvasManager createManager(DateTimeBean dtb, ObserverLocationBean olb, ViewingParametersBean vpb) throws IOException {
        try (InputStream hs = getClass().getResourceAsStream("/hygdata_v3.csv");
             InputStream as = getClass().getResourceAsStream("/asterisms.txt")) {
            StarCatalogue catalogue = new StarCatalogue.Builder()
                    .loadFrom(hs, HygDatabaseLoader.INSTANCE)
                    .loadFrom(as, AsterismLoader.INSTANCE)
                    .build();

            return new SkyCanvasManager(catalogue, dtb, olb, vpb);
        }
    }

    private BorderPane infoBar(ViewingParametersBean vpb, SkyCanvasManager canvasManager) {
        Text fovDisplay = new Text();
        fovDisplay.textProperty().bind(Bindings.format(Locale.ROOT, "Champ de vue : %.1f°",
                vpb.fieldOfViewDegProperty()));

        // TODO: 08/05/2020 check with null
        Text objectInfo = new Text();
        canvasManager.objUnderMouseProperty().addListener(
                (p, o, n) -> {
                    if (n != null) {
                        objectInfo.setText(n.info());
                    } else {
                        objectInfo.setText("");
                    }
                }
        );

        Text mousePos = new Text();
        mousePos.textProperty().bind(Bindings.format(Locale.ROOT,
                "Azimut : %.2f°, hauteur : %.2f°",
                canvasManager.mouseAzDegProperty(),
                canvasManager.mouseAltDegProperty()));

        BorderPane infoBar = new BorderPane(objectInfo, null, mousePos, null, fovDisplay);
        infoBar.setStyle("-fx-padding: 4; -fx-background-color: white;");
        return infoBar;
    }

    /**
     * @return a vertical separator
     */
    private Separator verticalSeparator() {
        Separator verticalSeparator = new Separator();
        verticalSeparator.setOrientation(Orientation.VERTICAL);
        return verticalSeparator;
    }

    private Font loadFontAwesome() throws IOException {
        try (InputStream fs = getClass().getResourceAsStream("/Font Awesome 5 Free-Solid-900.otf")) {
            return Font.loadFont(fs, 15);
        }
    }

    private TextField createTextField(boolean isLon, ObserverLocationBean olb, double defaultValue) {
        TextField tf = new TextField();
        tf.setStyle("-fx-pref-width: 60; -fx-alignment: baseline-right;");
        TextFormatter<Number> textFormatter = getFormatter(isLon);
        tf.setTextFormatter(textFormatter);
        if (isLon)
            olb.lonDegProperty().bind(textFormatter.valueProperty());
        else
            olb.latDegProperty().bind(textFormatter.valueProperty());
        textFormatter.setValue(defaultValue);
        return tf;
    }

    /**
     * Return the formatter
     *
     * @param isLon {@code true} iff this is the formatter for the longitude,
     *              {@code false} iff this is the formatter for the latitude
     * @return the formatter
     */
    private TextFormatter<Number> getFormatter(boolean isLon) {
        NumberStringConverter stringConverter = new NumberStringConverter("#0.00");

        UnaryOperator<TextFormatter.Change> filter = (change -> {
            try {
                String newText = change.getControlNewText();
                double newValue = stringConverter.fromString(newText).doubleValue();
                if (isLon) {
                    return GeographicCoordinates.isValidLonDeg(newValue) ? change : null;
                } else {
                    return GeographicCoordinates.isValidLatDeg(newValue) ? change : null;
                }
            } catch (Exception e) {
                return null;
            }
        });

        return new TextFormatter<>(stringConverter, 0, filter);
    }

}
