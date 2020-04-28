package ch.epfl.rigel.gui;

import ch.epfl.rigel.astronomy.CelestialObject;
import ch.epfl.rigel.astronomy.ObservedSky;
import ch.epfl.rigel.astronomy.StarCatalogue;
import ch.epfl.rigel.coordinates.CartesianCoordinates;
import ch.epfl.rigel.coordinates.HorizontalCoordinates;
import ch.epfl.rigel.coordinates.StereographicProjection;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.transform.Transform;

import java.util.Optional;

/**
 * @author Bastien Faivre (310929)
 * @author Kamil Mellouk (312327)
 */

public class SkyCanvasManager {

    private final ObservableValue<ObservedSky> observedSky;
    private final ObservableValue<StereographicProjection> projection;
    private final ObservableValue<Transform> planeToCanvas;
    private final ObjectProperty<CartesianCoordinates> mousePosition = new SimpleObjectProperty<>(null);
    private final ObservableValue<Optional<CelestialObject>> objectUnderMouse;
    private final ObservableValue<HorizontalCoordinates> mouseHorizontalPosition;
    private final ObservableDoubleValue mouseAzDeg;
    private final ObservableDoubleValue mouseAltDeg;

    /**
     * Constructor of a sky canvas manager
     *
     * @param starCatalogue         the catalogue to use
     * @param dateTimeBean          the date time bean
     * @param observerLocationBean  the observer location bean
     * @param viewingParametersBean the viewing parameters bean
     */
    public SkyCanvasManager(StarCatalogue starCatalogue,
                            DateTimeBean dateTimeBean,
                            ObserverLocationBean observerLocationBean,
                            ViewingParametersBean viewingParametersBean) {

        Canvas canvas = new Canvas();
        SkyCanvasPainter painter = new SkyCanvasPainter(canvas);

        projection = Bindings.createObjectBinding(
                () -> new StereographicProjection(viewingParametersBean.getCenter()),
                viewingParametersBean.centerProperty()
        );

        observedSky = Bindings.createObjectBinding(
                () -> new ObservedSky(dateTimeBean.getZonedDateTime(), observerLocationBean.getCoordinates(), projection.getValue(), starCatalogue),
                dateTimeBean.dateProperty(),
                dateTimeBean.timeProperty(),
                dateTimeBean.zoneProperty(),
                observerLocationBean.coordinatesProperty(),
                projection
        );

        // TODO: 28/04/2020 How do we create the Tranform ?
        planeToCanvas = Bindings.createObjectBinding(
                () -> Transform.affine(1300, 0, 0, -1300, 400, 300),
                projection, canvas.widthProperty(), canvas.heightProperty(), viewingParametersBean.fieldOfViewDegProperty()
        );


        // TODO: 28/04/2020 what is 10 units on the canvas ?
        objectUnderMouse = Bindings.createObjectBinding(
                () -> observedSky.getValue().objectClosestTo(mousePosition.getValue(), 10),
                observedSky, mousePosition
        );

        mouseHorizontalPosition = Bindings.createObjectBinding(
                () -> {
                    Point2D inverseTransformedMousePosition = planeToCanvas.getValue().inverseTransform(mousePosition.getValue().x(), mousePosition.getValue().y());
                    HorizontalCoordinates horizontalPosition = projection.getValue().inverseApply(CartesianCoordinates.of(inverseTransformedMousePosition.getX(), inverseTransformedMousePosition.getY()));
                    return HorizontalCoordinates.of(
                            horizontalPosition.az(),
                            horizontalPosition.alt()
                    );
                },
                planeToCanvas, projection, mousePosition
        );

        mouseAzDeg = Bindings.createDoubleBinding(
                () -> mouseHorizontalPosition.getValue().azDeg(),
                mouseHorizontalPosition
        );

        mouseAltDeg = Bindings.createDoubleBinding(
                () -> mouseHorizontalPosition.getValue().altDeg(),
                mouseHorizontalPosition
        );

    }

    // TODO: 28/04/2020 Any setter for these 3 properties?

    /**
     * Getter for the property azimuth in deg of the mouse
     *
     * @return the property azimuth in deg of the mouse
     */
    public ObservableDoubleValue mouseAzDegProperty() {
        return mouseAzDeg;
    }

    /**
     * Getter for the azimuth in deg of the mouse
     *
     * @return the azimuth in deg of the mouse
     */
    public double getMouseAzDeg() {
        return mouseAzDeg.get();
    }


    /**
     * Getter for the property altitude in deg of the mouse
     *
     * @return the property altitude in deg of the mouse
     */
    public ObservableDoubleValue mouseAltDegProperty() {
        return mouseAltDeg;
    }

    /**
     * Getter for the altitude in deg of the mouse
     *
     * @return the altitude in deg of the mouse
     */
    public double getMouseAltDeg() {
        return mouseAltDeg.get();
    }

    /**
     * Getter for the property object under mouse
     *
     * @return the property object under mouse
     */
    public ObservableValue<Optional<CelestialObject>> objectUnderMouseProperty() {
        return objectUnderMouse;
    }

    /**
     * Getter for the object under mouse
     *
     * @return the object under mouse
     */
    public Optional<CelestialObject> getObjectUnderMouse() {
        return objectUnderMouse.getValue();
    }
}
