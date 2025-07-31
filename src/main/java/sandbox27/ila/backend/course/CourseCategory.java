package sandbox27.ila.backend.course;

public enum CourseCategory {

    iLa("iLa"),
    KuP("Kreativität und Praxis"),
    BuE("Bewegung und Entspannung"),
    FuF("Fordern und Fördern"),
    SOL("Selbstorganisiertes Lernen");

    final String name;

    CourseCategory(String longName) {
        this.name = longName;
    }

}
