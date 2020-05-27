package ch.epfl.rigel.gui;

import ch.epfl.rigel.coordinates.GeographicCoordinates;

/**
 * @author Bastien Faivre (310929)
 * @author Kamil Mellouk (312327)
 */

public final class City {
    private String name;
    private String country;
    private GeographicCoordinates coordinates;

    public City(String name, String country, GeographicCoordinates coordinates) {
        this.name = name;
        this.country = country;
        this.coordinates = coordinates;
    }

    public String name() {
        return name;
    }

    public String country() {
        return country;
    }

    public GeographicCoordinates coordinates() {
        return coordinates;
    }

    @Override
    public String toString() {
        return name + ", " + country;
    }
}
