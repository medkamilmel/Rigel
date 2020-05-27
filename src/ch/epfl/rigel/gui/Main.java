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

import static javafx.beans.binding.Bindings.when;

/**
 * @author Bastien Faivre (310929)
 * @author Kamil Mellouk (312327)
 */
public class Main extends Application {

    private final ObserverLocationBean observerLocationBean = new ObserverLocationBean();
    private final DateTimeBean dateTimeBean = new DateTimeBean();
    private final ViewingParametersBean viewingParametersBean = new ViewingParametersBean();
    private final TimeAnimator timeAnimator = new TimeAnimator(dateTimeBean);
    private SkyCanvasManager skyCanvasManager;
    private CityCatalogue cityCatalogue;

    // Strings used for button icons
    private static final String RESET_ICON = "\uf0e2";
    private static final String PLAY_ICON = "\uf04b";
    private static final String PAUSE_ICON = "\uf04c";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        viewingParametersBean.setCenter(HorizontalCoordinates.ofDeg(180.000000000001, 15));
        viewingParametersBean.setFieldOfViewDeg(100);

        skyCanvasManager = createManager();
        cityCatalogue = createCityCatalogue();

        BorderPane mainPane = new BorderPane(
                new Pane(skyCanvasManager.canvas()),
                controlBar(),
                null,
                infoBar(),
                null
        );

        skyCanvasManager.canvas().widthProperty().bind(mainPane.widthProperty());
        skyCanvasManager.canvas().heightProperty().bind(mainPane.heightProperty());

        HomePage homePage = new HomePage();
        BorderPane homePane = homePage.getBorderPane();

        Scene scene = new Scene(homePane);
        scene.getStylesheets().add("/darkmode.css");

        // launch the program by clicking on the button start
        homePage.getStartButton().setOnAction(
                e -> scene.setRoot(mainPane)
        );

        primaryStage.setScene(scene);
        primaryStage.setTitle("Rigel");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.show();

        skyCanvasManager.canvas().requestFocus();
    }

    /**
     * Creating te top bar dedicated to parameters control (location, time, timeflow)
     *
     * @return HBox dedicated to parameters control
     * @throws IOException if IO exception when loading AwesomeFont
     */
    private HBox controlBar() throws IOException {

        //-----------------------------------------------------------------------------
        // Observation location
        //-----------------------------------------------------------------------------
        TextField lonTextField = createLonLatTextField(true, 6.57);
        TextField latTextField = createLonLatTextField(false, 46.52);

        ComboBox<City> cityChoiceBox = new ComboBox<>();
        cityChoiceBox.setItems(FXCollections.observableList(cityCatalogue.cities()));
        cityChoiceBox.setValue(cityCatalogue.cities().get(0));
        cityChoiceBox.setOnAction(e -> {
            GeographicCoordinates coordinates = cityChoiceBox.getValue().coordinates();
            lonTextField.setText(String.format("%.2f", coordinates.lonDeg()));
            latTextField.setText(String.format("%.2f", coordinates.latDeg()));
        });
        HBox whereControl = new HBox(
                new Label("Longitude (°) :"), lonTextField,
                new Label("Latitude (°) :"), latTextField,
                cityChoiceBox
        );
        whereControl.setStyle("-fx-spacing: inherit; -fx-alignment: baseline-left;");

        //-----------------------------------------------------------------------------
        // Observation instant
        //-----------------------------------------------------------------------------
        // Date selection
        DatePicker datePicker = new DatePicker();
        datePicker.setStyle("-fx-pref-width: 120");
        datePicker.setValue(LocalDate.now());
        dateTimeBean.dateProperty().bindBidirectional(datePicker.valueProperty());

        // Time selection
        TextField timeField = new TextField();
        timeField.setStyle("-fx-pref-width: 75; -fx-alignment: baseline-right;");
        DateTimeFormatter hmsFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTimeStringConverter stringConverter = new LocalTimeStringConverter(hmsFormatter, hmsFormatter);
        TextFormatter<LocalTime> timeFormatter = new TextFormatter<>(stringConverter);
        timeField.setTextFormatter(timeFormatter);
        timeFormatter.setValue(LocalTime.now());
        dateTimeBean.timeProperty().bindBidirectional(timeFormatter.valueProperty());

        // Timezone selection
        ComboBox<ZoneId> zoneIdComboBox = new ComboBox<>();
        List<String> availableZoneIds = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(availableZoneIds);
        List<ZoneId> zoneIdList = new ArrayList<>();
        availableZoneIds.forEach(e -> zoneIdList.add(ZoneId.of(e)));
        zoneIdComboBox.setItems(FXCollections.observableList(zoneIdList));
        zoneIdComboBox.setStyle("-fx-pref-width: 180");
        zoneIdComboBox.setValue(ZoneId.systemDefault());
        dateTimeBean.zoneProperty().bindBidirectional(zoneIdComboBox.valueProperty());

        HBox whenControl = new HBox(
                new Label("Date :"), datePicker,
                new Label("Heure :"), timeField, zoneIdComboBox
        );
        whenControl.setStyle("-fx-spacing: inherit; -fx-alignment: baseline-left;");
        whenControl.disableProperty().bind(timeAnimator.runningProperty());

        //-----------------------------------------------------------------------------
        // Time flow control
        //-----------------------------------------------------------------------------
        // Time accelerator selection
        ChoiceBox<NamedTimeAccelerator> acceleratorChoiceBox = new ChoiceBox<>();
        acceleratorChoiceBox.setItems(FXCollections.observableList(List.of(NamedTimeAccelerator.values())));
        acceleratorChoiceBox.setValue(NamedTimeAccelerator.TIMES_300);
        timeAnimator.acceleratorProperty().bind(Bindings.select(acceleratorChoiceBox.valueProperty(),
                "accelerator"));
        acceleratorChoiceBox.disableProperty().bind(timeAnimator.runningProperty());

        // Time reset
        Font fontAwesome = loadFontAwesome();
        Button resetButton = new Button(RESET_ICON);
        resetButton.setFont(fontAwesome);
        resetButton.setOnAction(e -> {
            datePicker.setValue(LocalDate.now());
            timeFormatter.setValue(LocalTime.now());
            zoneIdComboBox.setValue(ZoneId.systemDefault());
        });
        resetButton.disableProperty().bind(timeAnimator.runningProperty());

        // Time play/pause
        Button playPauseButton = new Button(PLAY_ICON);
        playPauseButton.setFont(fontAwesome);
        playPauseButton.textProperty().bind(when(timeAnimator.runningProperty()).then(PAUSE_ICON).otherwise(PLAY_ICON));
        playPauseButton.setOnAction(e -> {
            if (!timeAnimator.getRunning()) {
                timeAnimator.start();
            } else {
                timeAnimator.stop();
            }
        });

        HBox timeFlowControl = new HBox(acceleratorChoiceBox, resetButton, playPauseButton);
        timeFlowControl.setStyle("-fx-spacing: inherit");

        //-----------------------------------------------------------------------------
        // Control bar
        //-----------------------------------------------------------------------------
        HBox controlBar = new HBox(
                whereControl, verticalSeparator(),
                whenControl, verticalSeparator(),
                timeFlowControl
        );
        controlBar.setStyle("-fx-spacing: 4; -fx-padding: 4;");
        return controlBar;
    }

    /**
     * Creating the bottom bar containing information on the sky
     *
     * @return HBox containing information on the sky
     */
    private BorderPane infoBar() {
        Text fovDisplay = new Text();
        fovDisplay.textProperty().bind(Bindings.format(Locale.ROOT, "Champ de vue : %.1f°",
                viewingParametersBean.fieldOfViewDegProperty()));

        Text objectInfo = new Text();
        objectInfo.textProperty().bind(Bindings.createStringBinding(
                () -> skyCanvasManager.getObjUnderMouse() != null ? skyCanvasManager.getObjUnderMouse().info() : "",
                skyCanvasManager.objUnderMouseProperty()));

        Text mousePos = new Text();
        mousePos.textProperty().bind(Bindings.format(Locale.ROOT, "Azimut : %.2f°, hauteur : %.2f°",
                skyCanvasManager.mouseAzDegProperty(),
                skyCanvasManager.mouseAltDegProperty()));

        BorderPane infoBar = new BorderPane(objectInfo, null, mousePos, null, fovDisplay);
        infoBar.setStyle("-fx-padding: 4; -fx-background-color: white;");
        return infoBar;
    }

    /**
     * Creating the SkyCanvasManager used to manage the sky
     *
     * @return SkyCanvasManager used to manage the sky
     * @throws IOException if there is an input exception
     */
    private SkyCanvasManager createManager() throws IOException {
        try (InputStream hs = getClass().getResourceAsStream("/hygdata_v3.csv");
             InputStream as = getClass().getResourceAsStream("/asterisms.txt")) {
            StarCatalogue catalogue = new StarCatalogue.Builder()
                    .loadFrom(hs, HygDatabaseLoader.INSTANCE)
                    .loadFrom(as, AsterismLoader.INSTANCE)
                    .build();
            return new SkyCanvasManager(catalogue, dateTimeBean, observerLocationBean, viewingParametersBean);
        }
    }

    /**
     * Creating the CityCatalogue used in the control bar
     *
     * @return CityCatalogue
     * @throws IOException if there is an input exception
     */
    private CityCatalogue createCityCatalogue() throws IOException {
        try (InputStream cs = getClass().getResourceAsStream("/worldcities.csv")) {
            return new CityCatalogue.Builder()
                    .loadFrom(cs, CityCatalogue.CityLoader.INSTANCE)
                    .build();
        }
    }

    /**
     * Creating a text field for lon or lat with the right formatting
     *
     * @param isLon        boolean indicating whether the TextField is for lon or lat
     * @param defaultValue to assign to the TextField
     * @return TextField with the right formatting
     */
    private TextField createLonLatTextField(boolean isLon, double defaultValue) {
        TextField tf = new TextField();
        tf.setStyle("-fx-pref-width: 60; -fx-alignment: baseline-right;");
        // Creating the right text formatter
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
        TextFormatter<Number> textFormatter = new TextFormatter<>(stringConverter, 0, filter);
        tf.setTextFormatter(textFormatter);
        // Binding the TextField to the correct ObserverLocationBean property
        if (isLon) {
            observerLocationBean.lonDegProperty().bind(textFormatter.valueProperty());
        } else {
            observerLocationBean.latDegProperty().bind(textFormatter.valueProperty());
        }
        textFormatter.setValue(defaultValue);
        return tf;
    }

    /**
     * @return a vertical separator
     */
    private Separator verticalSeparator() {
        Separator verticalSeparator = new Separator();
        verticalSeparator.setOrientation(Orientation.VERTICAL);
        return verticalSeparator;
    }

    /**
     * Loading FontAwesome (used for buttons)
     *
     * @return the font FontAwesome
     * @throws IOException if loading exception
     */
    private Font loadFontAwesome() throws IOException {
        try (InputStream fs = getClass().getResourceAsStream("/Font Awesome 5 Free-Solid-900.otf")) {
            return Font.loadFont(fs, 15);
        }
    }
}
